package unit.squad;

import org.junit.jupiter.api.Test;
import telemetry.DecisionPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static unit.squad.SquadManager.ContainmentVerdict;
import static unit.squad.SquadManager.ReinforcementPath;
import static unit.squad.SquadManager.containmentExitPath;
import static unit.squad.SquadManager.containmentVerdict;
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
}
