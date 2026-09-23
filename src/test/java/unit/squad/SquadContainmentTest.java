package unit.squad;

import bwapi.UnitType;
import bwapi.WeaponType;
import org.junit.jupiter.api.Test;
import telemetry.DecisionPath;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.SquadManager.CONTAINMENT_REEVALUATE_INTERVAL;
import static unit.squad.SquadManager.ContainmentVerdict;
import static unit.squad.SquadManager.ReinforcementPath;
import static unit.squad.SquadManager.baseAttackEndsContainment;
import static unit.squad.SquadManager.containKillRadius;
import static unit.squad.SquadManager.containmentExitPath;
import static unit.squad.SquadManager.containmentVerdict;
import static unit.squad.SquadManager.isCombatThreat;
import static unit.squad.SquadManager.mayEnterContainment;
import static unit.squad.SquadManager.mergeEndsContainment;
import static unit.squad.SquadManager.reinforcementPath;

class SquadContainmentTest {

    private static final boolean BASES_SAFE = false;
    private static final boolean BASES_ATTACKED = true;
    private static final boolean HOLDING_UP = false;
    private static final boolean BLEEDING = true;
    private static final boolean ARC_KEPT = false;
    private static final boolean ARC_LOST = true;
    private static final boolean UNTHROTTLED = false;
    private static final boolean THROTTLED = true;
    private static final boolean ENEMIES_ON_ARC = true;
    private static final boolean ARC_CLEAR = false;
    private static final boolean IN_TIME = false;
    private static final boolean TIMED_OUT = true;
    private static final boolean CAN_BREAK = true;
    private static final boolean BELOW_BREAK_RATIO = false;
    private static final boolean SHOULD_CONTAIN = true;
    private static final boolean COMBAT_THREAT = true;
    private static final boolean SCOUT_THREAT = false;
    private static final int FLAP_WINDOW = 24;

    private static ContainmentVerdict verdict(boolean basesUnderAttack, boolean throttled, boolean engaged,
                                              boolean timedOut, boolean canBreak, boolean shouldContain) {
        return containmentVerdict(basesUnderAttack, HOLDING_UP, ARC_KEPT, throttled, engaged, timedOut, canBreak,
                shouldContain);
    }

    @Test
    void enemiesOnTheArcBelowTheBreakRatioKeepTheContainment() {
        assertEquals(ContainmentVerdict.HOLD,
                verdict(BASES_SAFE, UNTHROTTLED, ENEMIES_ON_ARC, IN_TIME, BELOW_BREAK_RATIO, SHOULD_CONTAIN));
        assertEquals(ContainmentVerdict.HOLD,
                verdict(BASES_SAFE, THROTTLED, ENEMIES_ON_ARC, IN_TIME, BELOW_BREAK_RATIO, SHOULD_CONTAIN));
    }

    @Test
    void anEnemyOnTheArcHoldsThePositionRatherThanRepositioning() {
        assertEquals(ContainmentVerdict.HOLD,
                verdict(BASES_SAFE, UNTHROTTLED, ENEMIES_ON_ARC, IN_TIME, BELOW_BREAK_RATIO, SHOULD_CONTAIN));
        assertEquals(ContainmentVerdict.REPOSITION,
                verdict(BASES_SAFE, UNTHROTTLED, ARC_CLEAR, IN_TIME, BELOW_BREAK_RATIO, SHOULD_CONTAIN));
    }

    @Test
    void enemiesInsideTheContainedAreaDoNotEndTheEpisode() {
        assertEquals(ContainmentVerdict.REPOSITION,
                verdict(BASES_SAFE, UNTHROTTLED, ARC_CLEAR, IN_TIME, BELOW_BREAK_RATIO, SHOULD_CONTAIN));
    }

    @Test
    void aboveTheBreakRatioTheArmyPushesIn() {
        assertEquals(ContainmentVerdict.BREAK_ALL,
                verdict(BASES_SAFE, UNTHROTTLED, ENEMIES_ON_ARC, IN_TIME, CAN_BREAK, SHOULD_CONTAIN));
        assertEquals(ContainmentVerdict.BREAK_ALL,
                verdict(BASES_SAFE, UNTHROTTLED, ARC_CLEAR, IN_TIME, CAN_BREAK, SHOULD_CONTAIN));
    }

    @Test
    void onlyTheStrengthGateCommitsTheSquad() {
        assertEquals(ContainmentVerdict.BREAK_ALL,
                verdict(BASES_SAFE, UNTHROTTLED, ENEMIES_ON_ARC, IN_TIME, CAN_BREAK, SHOULD_CONTAIN));
        assertEquals(ContainmentVerdict.RETREAT,
                verdict(BASES_SAFE, UNTHROTTLED, ENEMIES_ON_ARC, IN_TIME, BELOW_BREAK_RATIO, false));
    }

    @Test
    void theTimeoutDisengagesOnlyTheSquadThatRanOut() {
        assertEquals(ContainmentVerdict.RETREAT,
                verdict(BASES_SAFE, UNTHROTTLED, ARC_CLEAR, TIMED_OUT, BELOW_BREAK_RATIO, SHOULD_CONTAIN));
        assertEquals(ContainmentVerdict.BREAK_ALL,
                verdict(BASES_SAFE, UNTHROTTLED, ARC_CLEAR, TIMED_OUT, CAN_BREAK, SHOULD_CONTAIN));
    }

    @Test
    void containmentNoLongerApplicableFallsThroughToRetreat() {
        assertEquals(ContainmentVerdict.RETREAT,
                verdict(BASES_SAFE, UNTHROTTLED, ARC_CLEAR, IN_TIME, BELOW_BREAK_RATIO, false));
    }

    @Test
    void aThrottledFrameWithSafeBasesKeepsTheArcUnlessTheSquadBleedsOrLosesItsArc() {
        assertEquals(ContainmentVerdict.BREAK_ALL,
                verdict(BASES_ATTACKED, THROTTLED, ARC_CLEAR, IN_TIME, BELOW_BREAK_RATIO, SHOULD_CONTAIN));
        for (boolean engaged : new boolean[] {ARC_CLEAR, ENEMIES_ON_ARC}) {
            for (boolean timedOut : new boolean[] {IN_TIME, TIMED_OUT}) {
                for (boolean canBreak : new boolean[] {BELOW_BREAK_RATIO, CAN_BREAK}) {
                    for (boolean shouldContain : new boolean[] {SHOULD_CONTAIN, false}) {
                        assertEquals(ContainmentVerdict.HOLD,
                                verdict(BASES_SAFE, THROTTLED, engaged, timedOut, canBreak, shouldContain),
                                "a throttled frame with safe bases must keep the arc");
                    }
                }
            }
        }
    }

    @Test
    void attritionRetreatsTheSquadOnAnyFrameIncludingAThrottledOne() {
        for (boolean throttled : new boolean[] {UNTHROTTLED, THROTTLED}) {
            for (boolean engaged : new boolean[] {ARC_CLEAR, ENEMIES_ON_ARC}) {
                for (boolean canBreak : new boolean[] {BELOW_BREAK_RATIO, CAN_BREAK}) {
                    assertEquals(ContainmentVerdict.RETREAT,
                            containmentVerdict(BASES_SAFE, BLEEDING, ARC_KEPT, throttled, engaged, IN_TIME,
                                    canBreak, SHOULD_CONTAIN));
                }
            }
        }
    }

    @Test
    void withoutAttritionAnEngagedSquadHolds() {
        assertEquals(ContainmentVerdict.HOLD,
                containmentVerdict(BASES_SAFE, HOLDING_UP, ARC_KEPT, UNTHROTTLED, ENEMIES_ON_ARC, IN_TIME,
                        BELOW_BREAK_RATIO, SHOULD_CONTAIN));
        assertEquals(ContainmentVerdict.HOLD,
                containmentVerdict(BASES_SAFE, HOLDING_UP, ARC_KEPT, THROTTLED, ENEMIES_ON_ARC, IN_TIME,
                        BELOW_BREAK_RATIO, SHOULD_CONTAIN));
    }

    @Test
    void aBaseUnderAttackStillOutranksAttrition() {
        assertEquals(ContainmentVerdict.BREAK_ALL,
                containmentVerdict(BASES_ATTACKED, BLEEDING, ARC_LOST, THROTTLED, ENEMIES_ON_ARC, IN_TIME,
                        BELOW_BREAK_RATIO, SHOULD_CONTAIN));
    }

    @Test
    void noArcPointOutOfReachRetreatsInsteadOfHolding() {
        for (boolean throttled : new boolean[] {UNTHROTTLED, THROTTLED}) {
            assertEquals(ContainmentVerdict.RETREAT,
                    containmentVerdict(BASES_SAFE, HOLDING_UP, ARC_LOST, throttled, ENEMIES_ON_ARC, IN_TIME,
                            BELOW_BREAK_RATIO, SHOULD_CONTAIN));
        }
    }

    @Test
    void eachContainmentExitNamesItsCause() {
        assertEquals(DecisionPath.CONTAIN_ATTRITION, containmentExitPath(BLEEDING, ARC_KEPT));
        assertEquals(DecisionPath.CONTAIN_OUTRANGED, containmentExitPath(HOLDING_UP, ARC_LOST));
        assertEquals(DecisionPath.CONTAIN_RETREAT, containmentExitPath(HOLDING_UP, ARC_KEPT));
    }

    @Test
    void aUnitJoiningAContainingSquadLeavesItContaining() {
        assertEquals(ReinforcementPath.JOIN_CONTAINMENT, reinforcementPath(SquadStatus.CONTAIN, false));
        assertEquals(ReinforcementPath.JOIN_CONTAINMENT, reinforcementPath(SquadStatus.CONTAIN, true));
    }

    @Test
    void onlyAContainingSquadSkipsTheSim() {
        for (SquadStatus status : SquadStatus.values()) {
            if (status == SquadStatus.CONTAIN) {
                continue;
            }
            assertNotEquals(ReinforcementPath.JOIN_CONTAINMENT, reinforcementPath(status, false));
        }
        assertEquals(ReinforcementPath.STAGE, reinforcementPath(SquadStatus.RALLY, true));
        assertEquals(ReinforcementPath.SIMULATE, reinforcementPath(SquadStatus.RALLY, false));
        assertEquals(ReinforcementPath.SIMULATE, reinforcementPath(SquadStatus.FIGHT, false));
    }

    @Test
    void aMergeOutOfContainClosesTheContainingSourcesEpisode() {
        assertTrue(mergeEndsContainment(SquadStatus.CONTAIN, SquadStatus.FIGHT));
        assertTrue(mergeEndsContainment(SquadStatus.CONTAIN, SquadStatus.RUNBY));
    }

    @Test
    void aMergeThatStaysInContainOrNeverContainedClosesNothing() {
        assertFalse(mergeEndsContainment(SquadStatus.CONTAIN, SquadStatus.CONTAIN));
        assertFalse(mergeEndsContainment(SquadStatus.FIGHT, SquadStatus.FIGHT));
        assertFalse(mergeEndsContainment(SquadStatus.RALLY, SquadStatus.CONTAIN));
    }

    @Test
    void aKillAtTheOldEngageRadiusIsNotCreditedToALingContain() {
        int lingReach = ContainmentPushback.groundReach(UnitType.Zerg_Zergling, WeaponType::maxRange);

        assertTrue(containKillRadius(UnitType.Zerg_Zergling, lingReach, UnitType.Terran_Marine) < 256);
    }

    @Test
    void aKillBesideAMemberIsCredited() {
        int lingReach = ContainmentPushback.groundReach(UnitType.Zerg_Zergling, WeaponType::maxRange);
        int adjacent = UnitType.Zerg_Zergling.dimensionRight() + UnitType.Terran_Marine.dimensionLeft() + lingReach;

        assertTrue(adjacent <= containKillRadius(UnitType.Zerg_Zergling, lingReach, UnitType.Terran_Marine));
    }

    @Test
    void aRangedMemberIsCreditedFartherOut() {
        int lingReach = ContainmentPushback.groundReach(UnitType.Zerg_Zergling, WeaponType::maxRange);
        int hydraReach = ContainmentPushback.groundReach(UnitType.Zerg_Hydralisk, WeaponType::maxRange);

        assertTrue(containKillRadius(UnitType.Zerg_Hydralisk, hydraReach, UnitType.Terran_Marine)
                > containKillRadius(UnitType.Zerg_Zergling, lingReach, UnitType.Terran_Marine));
    }

    @Test
    void aBaseUnderAttackRefusesContainmentEntry() {
        assertFalse(mayEnterContainment(BASES_ATTACKED, SHOULD_CONTAIN, BELOW_BREAK_RATIO));
        assertTrue(mayEnterContainment(BASES_SAFE, SHOULD_CONTAIN, BELOW_BREAK_RATIO));
    }

    @Test
    void containmentEntryStillNeedsShouldContainAndAClosedStrengthGate() {
        assertFalse(mayEnterContainment(BASES_SAFE, false, BELOW_BREAK_RATIO));
        assertFalse(mayEnterContainment(BASES_SAFE, SHOULD_CONTAIN, CAN_BREAK));
    }

    @Test
    void aCombatThreatAtABaseEndsTheContainOnAnyFrame() {
        assertTrue(baseAttackEndsContainment(BASES_ATTACKED, COMBAT_THREAT, THROTTLED));
        assertTrue(baseAttackEndsContainment(BASES_ATTACKED, COMBAT_THREAT, UNTHROTTLED));
    }

    @Test
    void aNonCombatThreatAtABaseEndsTheContainOnlyOnAReevaluationTick() {
        assertFalse(baseAttackEndsContainment(BASES_ATTACKED, SCOUT_THREAT, THROTTLED));
        assertTrue(baseAttackEndsContainment(BASES_ATTACKED, SCOUT_THREAT, UNTHROTTLED));
    }

    @Test
    void safeBasesNeverEndTheContain() {
        assertFalse(baseAttackEndsContainment(BASES_SAFE, SCOUT_THREAT, THROTTLED));
        assertFalse(baseAttackEndsContainment(BASES_SAFE, SCOUT_THREAT, UNTHROTTLED));
    }

    @Test
    void mobileGroundAndAirFightersAreCombatThreats() {
        assertTrue(isCombatThreat(UnitType.Terran_Marine));
        assertTrue(isCombatThreat(UnitType.Terran_Vulture));
        assertTrue(isCombatThreat(UnitType.Protoss_Zealot));
        assertTrue(isCombatThreat(UnitType.Zerg_Mutalisk));
        assertTrue(isCombatThreat(UnitType.Terran_Wraith));
    }

    @Test
    void scoutsWorkersAndBuildingsAreNotCombatThreats() {
        assertFalse(isCombatThreat(UnitType.Terran_SCV));
        assertFalse(isCombatThreat(UnitType.Protoss_Probe));
        assertFalse(isCombatThreat(UnitType.Zerg_Overlord));
        assertFalse(isCombatThreat(UnitType.Protoss_Observer));
        assertFalse(isCombatThreat(UnitType.Terran_Bunker));
        assertFalse(isCombatThreat(UnitType.Protoss_Photon_Cannon));
    }

    @Test
    void aSquadRetreatingUnderABaseThreatNeverFlapsBetweenContainAndFight() {
        for (boolean combat : new boolean[] {SCOUT_THREAT, COMBAT_THREAT}) {
            List<SquadStatus> statuses = replayContainOnRetreatVerdicts(240, frame -> true, combat);

            assertFalse(statuses.contains(SquadStatus.CONTAIN), "no arc is taken while a base has a threat");
            assertEquals(0, containFightContainFlaps(statuses));
        }
    }

    @Test
    void aScoutThreatArrivingMidContainHoldsTheArcForTheReevaluationInterval() {
        List<SquadStatus> statuses = replayContainOnRetreatVerdicts(240, frame -> frame > 0, SCOUT_THREAT);

        for (int frame = 0; frame < CONTAINMENT_REEVALUATE_INTERVAL; frame++) {
            assertEquals(SquadStatus.CONTAIN, statuses.get(frame), "frame " + frame);
        }
        assertEquals(SquadStatus.FIGHT, statuses.get(CONTAINMENT_REEVALUATE_INTERVAL));
        assertEquals(0, containFightContainFlaps(statuses));
    }

    @Test
    void aCombatThreatArrivingMidContainBreaksAtOnceAndIsNotReentered() {
        List<SquadStatus> statuses = replayContainOnRetreatVerdicts(240, frame -> frame > 0, COMBAT_THREAT);

        assertEquals(SquadStatus.CONTAIN, statuses.get(0));
        assertEquals(SquadStatus.FIGHT, statuses.get(1));
        assertEquals(0, containFightContainFlaps(statuses));
    }

    /**
     * Replays a squad whose sim returns RETREAT on every frame with the strength gate closed, as squad 97a3b43c did
     * in game LSWLD05O from frame 18150, through the contain entry and contain verdict predicates.
     */
    private static List<SquadStatus> replayContainOnRetreatVerdicts(int frames, IntPredicate threatAt,
                                                                    boolean combat) {
        List<SquadStatus> statuses = new ArrayList<>();
        SquadStatus status = SquadStatus.FIGHT;
        int enteredAt = -1;
        for (int frame = 0; frame < frames; frame++) {
            boolean threat = threatAt.test(frame);
            if (status == SquadStatus.CONTAIN) {
                int elapsed = frame - enteredAt;
                boolean throttled = elapsed <= 0 || elapsed % CONTAINMENT_REEVALUATE_INTERVAL != 0;
                boolean breaks = baseAttackEndsContainment(threat, threat && combat, throttled);
                ContainmentVerdict verdict = containmentVerdict(breaks, HOLDING_UP, ARC_KEPT, throttled, ARC_CLEAR,
                        IN_TIME, BELOW_BREAK_RATIO, SHOULD_CONTAIN);
                if (verdict == ContainmentVerdict.BREAK_ALL) {
                    status = SquadStatus.FIGHT;
                }
            } else if (mayEnterContainment(threat, SHOULD_CONTAIN, BELOW_BREAK_RATIO)) {
                status = SquadStatus.CONTAIN;
                enteredAt = frame;
            } else {
                status = SquadStatus.RETREAT;
            }
            statuses.add(status);
        }
        return statuses;
    }

    private static int containFightContainFlaps(List<SquadStatus> statuses) {
        int flaps = 0;
        for (int frame = 1; frame < statuses.size(); frame++) {
            if (statuses.get(frame - 1) != SquadStatus.CONTAIN || statuses.get(frame) != SquadStatus.FIGHT) {
                continue;
            }
            int end = Math.min(statuses.size(), frame + FLAP_WINDOW + 1);
            if (statuses.subList(frame, end).contains(SquadStatus.CONTAIN)) {
                flaps++;
            }
        }
        return flaps;
    }
}
