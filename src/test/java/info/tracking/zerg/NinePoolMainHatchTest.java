package info.tracking.zerg;

import bwapi.Race;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NinePoolMainHatchTest {

    @Test
    void inMainHatcheryWithNoNaturalAndNoLairTechIsDetected() {
        assertTrue(NinePoolMainHatch.matches(true, false, false));
    }

    @Test
    void notDetectedWithOnlyTheMainDepot() {
        assertFalse(NinePoolMainHatch.matches(false, false, false));
    }

    @Test
    void notDetectedWhenTheNaturalIsTaken() {
        assertFalse(NinePoolMainHatch.matches(true, true, false));
    }

    @Test
    void notDetectedOnceLairTechIsSeen() {
        assertFalse(NinePoolMainHatch.matches(true, false, true));
    }

    @Test
    void naturalDepotSeenOnceKeepsCounting() {
        NinePoolMainHatch strategy = new NinePoolMainHatch();

        assertFalse(strategy.observeNaturalDepot(false));
        assertTrue(strategy.observeNaturalDepot(true));
        assertTrue(strategy.observeNaturalDepot(false));
    }

    @Test
    void sharesTwoHatchLingsCutoffAndIsZergOnly() {
        NinePoolMainHatch strategy = new NinePoolMainHatch();

        assertEquals(TwoHatchLing.DETECTION_CUTOFF, NinePoolMainHatch.DETECTION_CUTOFF);
        assertEquals("9PoolMainHatch", strategy.getName());
        assertEquals(Race.Zerg, strategy.getRace());
    }
}
