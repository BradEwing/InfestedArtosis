package strategy.buildorder.terran;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThreeHatchLurkerTest {

    private static final int BASE_TARGET = 8;

    @Test
    void theTargetStandsWhileEnoughHydralisksExistToReachIt() {
        assertEquals(BASE_TARGET, ThreeHatchLurker.reachableLurkerTarget(BASE_TARGET, 0, 8));
        assertEquals(BASE_TARGET, ThreeHatchLurker.reachableLurkerTarget(BASE_TARGET, 3, 12));
    }

    @Test
    void theTargetFallsToWhatTheProducersCanReach() {
        assertEquals(2, ThreeHatchLurker.reachableLurkerTarget(BASE_TARGET, 0, 2));
        assertEquals(5, ThreeHatchLurker.reachableLurkerTarget(BASE_TARGET, 3, 2));
    }

    @Test
    void noHydraliskAndNoLurkerAsksForNothing() {
        assertEquals(0, ThreeHatchLurker.reachableLurkerTarget(BASE_TARGET, 0, 0));
    }

    @Test
    void lurkersAlreadyOnTheFieldCountTowardsTheTarget() {
        assertEquals(BASE_TARGET, ThreeHatchLurker.reachableLurkerTarget(BASE_TARGET, 8, 0));
    }
}
