package unit.squad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static unit.squad.SquadManager.ContainmentVerdict;
import static unit.squad.SquadManager.containmentVerdict;

class SquadContainmentTest {

    private static final boolean BASES_SAFE = false;
    private static final boolean BASES_ATTACKED = true;
    private static final boolean UNTHROTTLED = false;
    private static final boolean THROTTLED = true;
    private static final boolean ENEMIES_ON_ARC = true;
    private static final boolean ARC_CLEAR = false;
    private static final boolean IN_TIME = false;
    private static final boolean TIMED_OUT = true;
    private static final boolean CAN_BREAK = true;
    private static final boolean BELOW_BREAK_RATIO = false;
    private static final boolean SHOULD_CONTAIN = true;

    @Test
    void enemiesOnTheArcBelowTheBreakRatioKeepTheContainment() {
        assertEquals(ContainmentVerdict.HOLD,
                containmentVerdict(BASES_SAFE, UNTHROTTLED, ENEMIES_ON_ARC, IN_TIME, BELOW_BREAK_RATIO,
                        SHOULD_CONTAIN));
        assertEquals(ContainmentVerdict.HOLD,
                containmentVerdict(BASES_SAFE, THROTTLED, ENEMIES_ON_ARC, IN_TIME, BELOW_BREAK_RATIO,
                        SHOULD_CONTAIN));
    }

    @Test
    void anEnemyOnTheArcHoldsThePositionRatherThanRepositioning() {
        assertEquals(ContainmentVerdict.HOLD,
                containmentVerdict(BASES_SAFE, UNTHROTTLED, ENEMIES_ON_ARC, IN_TIME, BELOW_BREAK_RATIO,
                        SHOULD_CONTAIN));
        assertEquals(ContainmentVerdict.REPOSITION,
                containmentVerdict(BASES_SAFE, UNTHROTTLED, ARC_CLEAR, IN_TIME, BELOW_BREAK_RATIO,
                        SHOULD_CONTAIN));
    }

    @Test
    void enemiesInsideTheContainedAreaDoNotEndTheEpisode() {
        assertEquals(ContainmentVerdict.REPOSITION,
                containmentVerdict(BASES_SAFE, UNTHROTTLED, ARC_CLEAR, IN_TIME, BELOW_BREAK_RATIO, SHOULD_CONTAIN));
    }

    @Test
    void aboveTheBreakRatioTheArmyPushesIn() {
        assertEquals(ContainmentVerdict.BREAK_ALL,
                containmentVerdict(BASES_SAFE, UNTHROTTLED, ENEMIES_ON_ARC, IN_TIME, CAN_BREAK, SHOULD_CONTAIN));
        assertEquals(ContainmentVerdict.BREAK_ALL,
                containmentVerdict(BASES_SAFE, UNTHROTTLED, ARC_CLEAR, IN_TIME, CAN_BREAK, SHOULD_CONTAIN));
    }

    @Test
    void onlyTheStrengthGateCommitsTheSquad() {
        assertEquals(ContainmentVerdict.BREAK_ALL,
                containmentVerdict(BASES_SAFE, UNTHROTTLED, ENEMIES_ON_ARC, IN_TIME, CAN_BREAK, SHOULD_CONTAIN));
        assertEquals(ContainmentVerdict.RETREAT,
                containmentVerdict(BASES_SAFE, UNTHROTTLED, ENEMIES_ON_ARC, IN_TIME, BELOW_BREAK_RATIO, false));
    }

    @Test
    void theTimeoutDisengagesOnlyTheSquadThatRanOut() {
        assertEquals(ContainmentVerdict.RETREAT,
                containmentVerdict(BASES_SAFE, UNTHROTTLED, ARC_CLEAR, TIMED_OUT, BELOW_BREAK_RATIO, SHOULD_CONTAIN));
        assertEquals(ContainmentVerdict.BREAK_ALL,
                containmentVerdict(BASES_SAFE, UNTHROTTLED, ARC_CLEAR, TIMED_OUT, CAN_BREAK, SHOULD_CONTAIN));
    }

    @Test
    void containmentNoLongerApplicableFallsThroughToRetreat() {
        assertEquals(ContainmentVerdict.RETREAT,
                containmentVerdict(BASES_SAFE, UNTHROTTLED, ARC_CLEAR, IN_TIME, BELOW_BREAK_RATIO, false));
    }

    @Test
    void basesUnderAttackIsTheOnlyExitFromAThrottledFrame() {
        assertEquals(ContainmentVerdict.BREAK_ALL,
                containmentVerdict(BASES_ATTACKED, THROTTLED, ARC_CLEAR, IN_TIME, BELOW_BREAK_RATIO, SHOULD_CONTAIN));
        for (boolean engaged : new boolean[] {ARC_CLEAR, ENEMIES_ON_ARC}) {
            for (boolean timedOut : new boolean[] {IN_TIME, TIMED_OUT}) {
                for (boolean canBreak : new boolean[] {BELOW_BREAK_RATIO, CAN_BREAK}) {
                    for (boolean shouldContain : new boolean[] {SHOULD_CONTAIN, false}) {
                        assertEquals(ContainmentVerdict.HOLD,
                                containmentVerdict(BASES_SAFE, THROTTLED, engaged, timedOut, canBreak, shouldContain),
                                "a throttled frame with safe bases must keep the arc");
                    }
                }
            }
        }
    }
}
