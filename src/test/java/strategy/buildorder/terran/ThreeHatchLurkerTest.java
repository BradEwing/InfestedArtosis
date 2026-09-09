package strategy.buildorder.terran;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void thirdHatchWaitsForTwoFieldedLurkers() {
        assertFalse(ThreeHatchLurker.hasFieldedLurkersForThirdHatch(0));
        assertFalse(ThreeHatchLurker.hasFieldedLurkersForThirdHatch(1));
        assertTrue(ThreeHatchLurker.hasFieldedLurkersForThirdHatch(2));
    }

    @Test
    void metabolicBoostWaitsForFieldedLurkers() {
        assertFalse(ThreeHatchLurker.shouldPlanMetabolicBoost(12, 2));
        assertTrue(ThreeHatchLurker.shouldPlanMetabolicBoost(12, 3));
    }

    @Test
    void metabolicBoostWaitsForTwelveZerglings() {
        assertFalse(ThreeHatchLurker.shouldPlanMetabolicBoost(11, 3));
        assertTrue(ThreeHatchLurker.shouldPlanMetabolicBoost(12, 3));
    }
}
