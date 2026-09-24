package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import bwapi.WalkPosition;
import org.junit.jupiter.api.Test;
import util.Arc;
import util.StaticDefenseZone;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.CombatSimulator.CombatResult.ADVANCE;
import static unit.squad.CombatSimulator.CombatResult.ENGAGE;
import static unit.squad.CombatSimulator.CombatResult.RETREAT;

class ContainTransitAndLockTest {

    private static final double TERRAN_THRESHOLD = 1.44;
    private static final double PROTOSS_THRESHOLD = 1.25;
    private static final int MAP_PIXELS = 4096;
    private static final Set<WalkPosition> ALL_WALKABLE = Collections.emptySet();

    @Test
    void aStrongEngageBreaksAnAttritionRetreatLock() {
        assertTrue(SquadManager.strongEngageBreaksRetreatLock(true, ENGAGE, true, 2.89, TERRAN_THRESHOLD));
        assertTrue(SquadManager.strongEngageBreaksRetreatLock(true, ENGAGE, true, SquadManager.STRONG_ENGAGE_RATIO,
                TERRAN_THRESHOLD));
    }

    @Test
    void anEngageBelowTheStrongThresholdKeepsTheLock() {
        assertFalse(SquadManager.strongEngageBreaksRetreatLock(true, ENGAGE, true, 1.49, TERRAN_THRESHOLD));
        assertFalse(SquadManager.strongEngageBreaksRetreatLock(true, ENGAGE, true, 1.3, PROTOSS_THRESHOLD),
                "clears the Protoss engage threshold but not the strong one");
    }

    @Test
    void theStrongThresholdIsNeverBelowTheMatchupThreshold() {
        assertEquals(SquadManager.STRONG_ENGAGE_RATIO, SquadManager.strongEngageThreshold(TERRAN_THRESHOLD));
        assertEquals(1.8, SquadManager.strongEngageThreshold(1.8));
    }

    @Test
    void onlyAnAttritionLockCanBeBroken() {
        assertFalse(SquadManager.strongEngageBreaksRetreatLock(false, ENGAGE, true, 2.89, TERRAN_THRESHOLD));
    }

    @Test
    void onlyAMeasuredEngageBreaksTheLock() {
        assertFalse(SquadManager.strongEngageBreaksRetreatLock(true, ADVANCE, false, 0, TERRAN_THRESHOLD));
        assertFalse(SquadManager.strongEngageBreaksRetreatLock(true, RETREAT, true, 2.0, TERRAN_THRESHOLD));
        assertFalse(SquadManager.strongEngageBreaksRetreatLock(true, ENGAGE, false, 2.0, TERRAN_THRESHOLD));
    }

    @Test
    void anAttritionLockIsMarkedAndAnOrdinaryLockClearsTheMark() {
        Squad squad = new GroundSquad();

        squad.startAttritionRetreatLock(1000);
        assertTrue(squad.isAttritionRetreatLock());
        assertTrue(squad.isRetreatLocked(1001));

        squad.startRetreatLock(1050);
        assertFalse(squad.isAttritionRetreatLock());
    }

    @Test
    void clearingTheLockEndsItAtOnce() {
        Squad squad = new GroundSquad();
        squad.startAttritionRetreatLock(1000);

        squad.clearRetreatLock();

        assertFalse(squad.isRetreatLocked(1001));
        assertFalse(squad.isAttritionRetreatLock());
    }

    @Test
    void aMergeDoesNotInheritAnAttritionRetreatLock() {
        Squad bled = new GroundSquad();
        bled.setStatus(SquadStatus.RETREAT);
        bled.startAttritionRetreatLock(9881);
        Squad fresh = new GroundSquad();
        fresh.setStatus(SquadStatus.FIGHT);

        Squad merged = new GroundSquad();
        merged.inheritStateFrom(Arrays.asList(bled, fresh));

        assertFalse(merged.isRetreatLocked(9950));
        assertFalse(merged.isAttritionRetreatLock());
    }

    @Test
    void aMergeStillInheritsAnOrdinaryRetreatLock() {
        Squad retreating = new GroundSquad();
        retreating.setStatus(SquadStatus.RETREAT);
        retreating.startRetreatLock(9881);
        Squad fresh = new GroundSquad();
        fresh.setStatus(SquadStatus.RETREAT);

        Squad merged = new GroundSquad();
        merged.inheritStateFrom(Arrays.asList(retreating, fresh));

        assertTrue(merged.isRetreatLocked(9950));
    }

    @Test
    void aSquadTakesTheArcOnlyOnceItHasArrived() {
        assertTrue(SquadManager.arrivedAtArc(0));
        assertTrue(SquadManager.arrivedAtArc(SquadManager.CONTAIN_ARRIVAL_DISTANCE));
        assertFalse(SquadManager.arrivedAtArc(SquadManager.CONTAIN_ARRIVAL_DISTANCE + 1),
                "a squad in transit keeps the sim running");
        assertFalse(SquadManager.arrivedAtArc(1000), "the mid-map squad at 12000 was 1000 px out");
    }

    @Test
    void mobileEnemiesDoNotMoveTheArcButStaticDefenceStillDoes() {
        StaticDefenseZone marine = new StaticDefenseZone(UnitType.Terran_Marine, new Position(1600, 1300), 128);
        StaticDefenseZone dragoon = new StaticDefenseZone(UnitType.Protoss_Dragoon, new Position(1600, 1300), 128);
        StaticDefenseZone bunker = new StaticDefenseZone(UnitType.Terran_Bunker, new Position(1700, 1700), 128);
        StaticDefenseZone hurt = new StaticDefenseZone(UnitType.None, new Position(1500, 1300), 64);
        StaticDefenseZone sieged = new StaticDefenseZone(UnitType.Terran_Siege_Tank_Siege_Mode,
                new Position(1600, 1900), 384);

        List<StaticDefenseZone> kept = ContainmentPushback.arcZones(Arrays.asList(marine, dragoon, bunker, hurt,
                sieged));

        assertEquals(Arrays.asList(bunker, hurt, sieged), kept);
    }

    @Test
    void aRecomputedArcThatStandsWhereItWasHasNotMoved() {
        Arc held = new Arc(new Position(1600, 1600), new Position(1600, 960), 160, 90, 8);
        held.compute(ALL_WALKABLE, Collections.emptyList(), 48, MAP_PIXELS, MAP_PIXELS);
        Arc same = held.withRadius(held.getRadius());
        same.compute(ALL_WALKABLE, Collections.emptyList(), 48, MAP_PIXELS, MAP_PIXELS);
        Arc wider = held.withRadius(held.getRadius() + ContainmentPushback.RADIUS_STEP);
        wider.compute(ALL_WALKABLE, Collections.emptyList(), 48, MAP_PIXELS, MAP_PIXELS);

        assertFalse(ContainmentPushback.moved(held, same));
        assertTrue(ContainmentPushback.moved(held, wider));
        assertTrue(ContainmentPushback.moved(null, same));
    }
}
