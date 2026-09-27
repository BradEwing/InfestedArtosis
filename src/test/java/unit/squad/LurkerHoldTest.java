package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import util.StaticDefenseZone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.CombatSimulator.CombatResult.ADVANCE;
import static unit.squad.CombatSimulator.CombatResult.ENGAGE;
import static unit.squad.CombatSimulator.CombatResult.RETREAT;

class LurkerHoldTest {

    private static final Position TANK_AT = new Position(1000, 1000);
    private static final StaticDefenseZone TANK = new StaticDefenseZone(UnitType.Terran_Siege_Tank_Siege_Mode,
            TANK_AT, 400);
    private static final StaticDefenseZone BUNKER = new StaticDefenseZone(UnitType.Terran_Bunker,
            new Position(1400, 1000), 192);

    @Test
    void aLurkerHitInsideATanksReachIsSentOut() {
        assertEquals(LurkerHold.HIT, LurkerHold.reason(false, true, false, false));
    }

    @Test
    void aLurkerRetreatingInsideFixedFireIsSentOut() {
        assertEquals(LurkerHold.RETREAT, LurkerHold.reason(false, false, true, false));
    }

    @Test
    void aLurkerOutOfFireIsLeftAlone() {
        assertNull(LurkerHold.reason(false, false, false, false));
    }

    @Test
    void aHoldingLurkerKeepsItsPointWhileTheFireStaysOffIt() {
        assertNull(LurkerHold.reason(false, true, true, true));
    }

    @Test
    void aHoldingLurkerMovesWhenTheFireMovesOntoItsPoint() {
        assertEquals(LurkerHold.MOVED, LurkerHold.reason(true, false, false, true));
    }

    @Test
    void aSquadFightingOnAnEngageVerdictIsCommitting() {
        assertTrue(SquadManager.isCommitting(SquadStatus.FIGHT, false, ENGAGE));
    }

    @Test
    void aSquadUnderItsFightLockIsCommitting() {
        assertTrue(SquadManager.isCommitting(SquadStatus.FIGHT, true, ADVANCE));
    }

    @Test
    void aBlindAdvanceIsNotCommitting() {
        assertFalse(SquadManager.isCommitting(SquadStatus.FIGHT, false, ADVANCE));
        assertFalse(SquadManager.isCommitting(SquadStatus.FIGHT, false, null));
    }

    @Test
    void aSquadNotFightingIsNotCommitting() {
        assertFalse(SquadManager.isCommitting(SquadStatus.RETREAT, false, ENGAGE));
        assertFalse(SquadManager.isCommitting(SquadStatus.CONTAIN, true, RETREAT));
    }

    @Test
    void anEngageReadThisFrameCommitsTheLurkersAtOnce() {
        assertTrue(LurkerHold.lurkersCommit(SquadStatus.FIGHT, false, ENGAGE));
    }

    @Test
    void aFightLockWithNoEngageReadDoesNotCommitTheLurkers() {
        assertFalse(LurkerHold.lurkersCommit(SquadStatus.FIGHT, false, null));
        assertFalse(LurkerHold.lurkersCommit(SquadStatus.FIGHT, false, ADVANCE));
        assertFalse(LurkerHold.lurkersCommit(SquadStatus.FIGHT, false, RETREAT));
    }

    @Test
    void aSquadBornIntoFightOnItsLockWithNoSimReadIsNotCommittingItsLurkers() {
        assertTrue(SquadManager.isCommitting(SquadStatus.FIGHT, true, null));
        assertFalse(LurkerHold.lurkersCommit(SquadStatus.FIGHT, false, null));
    }

    @Test
    void aContainBreakOrCollapseCommitsTheLurkersWithoutARead() {
        assertTrue(LurkerHold.lurkersCommit(SquadStatus.FIGHT, true, null));
        assertTrue(LurkerHold.lurkersCommit(SquadStatus.FIGHT, true, RETREAT));
    }

    @Test
    void aSquadOutOfFightNeverCommitsItsLurkers() {
        assertFalse(LurkerHold.lurkersCommit(SquadStatus.RETREAT, true, ENGAGE));
        assertFalse(LurkerHold.lurkersCommit(SquadStatus.CONTAIN, false, ENGAGE));
    }

    @Test
    void lurkersThatDoNotCommitKeepOutOfEveryZone() {
        List<StaticDefenseZone> zones = Arrays.asList(TANK, BUNKER);

        assertEquals(zones, LurkerHold.keptOut(zones, false, false, Collections.singletonList(TANK_AT)));
    }

    @Test
    void lurkersCommittedByABreakKeepOutOfNoZone() {
        assertTrue(LurkerHold.keptOut(Arrays.asList(TANK, BUNKER), true, true, Collections.emptyList()).isEmpty());
    }

    @Test
    void anEngageThatPricedTheTankLetsTheLurkersIntoItsReach() {
        assertTrue(LurkerHold.keptOut(Arrays.asList(TANK, BUNKER), true, false,
                Collections.singletonList(TANK_AT)).isEmpty());
    }

    @Test
    void anEngageThatDidNotPriceATankKeepsTheLurkersOutOfItsReach() {
        StaticDefenseZone other = new StaticDefenseZone(UnitType.Terran_Siege_Tank_Siege_Mode,
                new Position(2400, 1000), 400);

        assertEquals(Collections.singletonList(other), LurkerHold.keptOut(Arrays.asList(TANK, other, BUNKER), true,
                false, Collections.singletonList(TANK_AT)));
        assertEquals(Arrays.asList(TANK, other), LurkerHold.keptOut(Arrays.asList(TANK, other, BUNKER), true, false,
                Collections.emptyList()));
    }

    @Test
    void aTankIsPricedOnlyWithinTheMatchDistanceOfItsZone() {
        assertTrue(LurkerHold.priced(TANK, Collections.singletonList(
                new Position(TANK_AT.getX() + LurkerHold.PRICED_MATCH_DISTANCE, TANK_AT.getY()))));
        assertFalse(LurkerHold.priced(TANK, Collections.singletonList(
                new Position(TANK_AT.getX() + LurkerHold.PRICED_MATCH_DISTANCE + 1, TANK_AT.getY()))));
        assertFalse(LurkerHold.priced(TANK, Collections.emptyList()));
    }

    @Test
    void aUnitInTwoSquadsIsVisitedOnceForTheFirstSquad() {
        Map<String, String> first = LurkerHold.firstSquadOf(Arrays.asList("a", "b"),
                squad -> squad.equals("a") ? Arrays.asList("lurker", "ling") : Arrays.asList("lurker", "hydra"));

        assertEquals(3, first.size());
        assertEquals("a", first.get("lurker"));
        assertEquals("a", first.get("ling"));
        assertEquals("b", first.get("hydra"));
        assertEquals(Arrays.asList("lurker", "ling", "hydra"), new ArrayList<>(first.keySet()));
    }

    @Test
    void aContainBreakCommitEndsWithItsFightLockOrAStatusChange() {
        Squad squad = new GroundSquad();
        squad.setStatus(SquadStatus.FIGHT);
        squad.startFightLock(1000);

        assertTrue(SquadManager.wholeSquadCommitHolds(squad, 1000));
        assertFalse(SquadManager.wholeSquadCommitHolds(squad, 1500));
        squad.setStatus(SquadStatus.RETREAT);
        assertFalse(SquadManager.wholeSquadCommitHolds(squad, 1000));
    }

    @Test
    void aCollapseWrapOutlastingItsFightLockKeepsTheWholeSquadCommitted() {
        Squad squad = new GroundSquad();
        squad.setStatus(SquadStatus.FIGHT);
        squad.startFightLock(1000);
        squad.setCollapse(new ContainmentCollapse.Maneuver(Collections.emptyMap(), Collections.emptySet(), 9000));

        assertTrue(SquadManager.wholeSquadCommitHolds(squad, 1500));
        squad.setCollapse(null);
        assertFalse(SquadManager.wholeSquadCommitHolds(squad, 1500));
    }

    @Test
    void aCommittedCollapseHoldsTheWholeSquadWhileItsCommitHolds() {
        Squad squad = new GroundSquad();
        squad.setStatus(SquadStatus.FIGHT);
        squad.commitCollapse(1000);

        assertTrue(SquadManager.wholeSquadCommitHolds(squad, 1001));
        assertFalse(SquadManager.wholeSquadCommitHolds(squad, 1500));
    }

    @Test
    void aHoldingUnitNotVisitedThisFrameIsLeftBehind() {
        List<String> left = LurkerHold.leftBehind(Arrays.asList("gone", "kept"),
                new HashSet<>(Arrays.asList("kept", "new")));

        assertEquals(Collections.singletonList("gone"), left);
        assertTrue(LurkerHold.leftBehind(Collections.<String>emptyList(), new HashSet<>()).isEmpty());
    }

    @Test
    void aHoldIsMovedOnlyForAPointThatGainsGround() {
        assertFalse(LurkerHold.worthMoving(-40, -40));
        assertFalse(LurkerHold.worthMoving(-40, -40 + LurkerHold.MOVE_GAIN - 1));
        assertTrue(LurkerHold.worthMoving(-40, -40 + LurkerHold.MOVE_GAIN));
    }
}
