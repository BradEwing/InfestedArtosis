package info.tracking.zerg;

import bwapi.Race;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NinePoolMainHatchTest {

    @Test
    void ninePoolWithAnInMainHatcheryIsDetected() {
        assertTrue(NinePoolMainHatch.matches(ZergOpener.NINE_POOL, true, false, false));
    }

    @Test
    void notDetectedWithoutTheNinePoolReading() {
        assertFalse(NinePoolMainHatch.matches(null, true, false, false));
        assertFalse(NinePoolMainHatch.matches(ZergOpener.TWELVE_POOL, true, false, false));
        assertFalse(NinePoolMainHatch.matches(ZergOpener.TWELVE_HATCH, true, false, false));
    }

    @Test
    void notDetectedWithOnlyTheMainDepot() {
        assertFalse(NinePoolMainHatch.matches(ZergOpener.NINE_POOL, false, false, false));
    }

    @Test
    void notDetectedWhenTheNaturalIsTaken() {
        assertFalse(NinePoolMainHatch.matches(ZergOpener.NINE_POOL, true, true, false));
    }

    @Test
    void notDetectedOnceLairTechIsSeen() {
        assertFalse(NinePoolMainHatch.matches(ZergOpener.NINE_POOL, true, false, true));
    }

    @Test
    void sharesTwoHatchLingsCutoffAndIsZergOnly() {
        NinePoolMainHatch strategy = new NinePoolMainHatch(new ZergOpenerReading());

        assertEquals(TwoHatchLing.DETECTION_CUTOFF, NinePoolMainHatch.DETECTION_CUTOFF);
        assertEquals("9PoolMainHatch", strategy.getName());
        assertEquals(Race.Zerg, strategy.getRace());
    }
}
