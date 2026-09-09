package strategy.buildorder.opener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreeHatchBeforePoolTest {

    @Test
    void poolOpenersWaitForTheirNamedSupply() {
        assertFalse(NinePoolSpeed.shouldPlanPool(17));
        assertTrue(NinePoolSpeed.shouldPlanPool(18));
        assertFalse(Overpool.shouldPlanPool(17, 2));
        assertFalse(Overpool.shouldPlanPool(18, 1));
        assertTrue(Overpool.shouldPlanPool(18, 2));
        assertFalse(TwelvePool.shouldPlanPool(23));
        assertTrue(TwelvePool.shouldPlanPool(24));
    }

    @Test
    void theTwelveHatchWaitsForTwelveSupply() {
        assertFalse(TwelveHatch.shouldPlanHatchery(18, 1));
        assertFalse(TwelveHatch.shouldPlanHatchery(23, 1));
        assertTrue(TwelveHatch.shouldPlanHatchery(24, 1));
        assertFalse(TwelveHatch.shouldPlanHatchery(24, 2));
    }

    @Test
    void theFirstHatcheryWaitsForTwelveSupply() {
        assertFalse(ThreeHatchBeforePool.shouldPlanFirstHatchery(12, 1));
        assertFalse(ThreeHatchBeforePool.shouldPlanFirstHatchery(23, 1));
        assertTrue(ThreeHatchBeforePool.shouldPlanFirstHatchery(24, 1));
        assertFalse(ThreeHatchBeforePool.shouldPlanFirstHatchery(24, 2));
    }

    @Test
    void theSecondHatcheryWaitsForFourteenSupply() {
        assertFalse(ThreeHatchBeforePool.shouldPlanSecondHatchery(27, 2));
        assertTrue(ThreeHatchBeforePool.shouldPlanSecondHatchery(28, 2));
        assertFalse(ThreeHatchBeforePool.shouldPlanSecondHatchery(28, 3));
    }
}
