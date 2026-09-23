package info.tracking.zerg;

import bwapi.Race;
import org.junit.jupiter.api.Test;
import util.Time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwoHatchLingTest {

    private static final Time ORIGIN_GAME_FIRE = new Time(5622);

    @Test
    void inMainSecondHatchWithALingFloodIsDetected() {
        assertTrue(TwoHatchLing.matches(ORIGIN_GAME_FIRE, true, 16, false, false));
    }

    @Test
    void detectsUpToTheCutoff() {
        assertTrue(TwoHatchLing.matches(TwoHatchLing.DETECTION_CUTOFF, true, 20, false, false));
    }

    @Test
    void notDetectedAfterTheCutoff() {
        Time late = new Time(TwoHatchLing.DETECTION_CUTOFF.getFrames() + 1);
        assertFalse(TwoHatchLing.matches(late, true, 20, false, false));
    }

    @Test
    void notDetectedWithOnlyTheMainDepot() {
        assertFalse(TwoHatchLing.matches(ORIGIN_GAME_FIRE, false, 24, false, false));
    }

    @Test
    void notDetectedWithFewerThanSixteenLings() {
        assertFalse(TwoHatchLing.matches(ORIGIN_GAME_FIRE, true, TwoHatchLing.ZERGLING_THRESHOLD - 1, false, false));
    }

    @Test
    void notDetectedWhenTheNaturalIsTaken() {
        assertFalse(TwoHatchLing.matches(ORIGIN_GAME_FIRE, true, 20, true, false));
    }

    @Test
    void notDetectedOnceLairTechIsSeen() {
        assertFalse(TwoHatchLing.matches(ORIGIN_GAME_FIRE, true, 20, false, true));
    }

    @Test
    void naturalDepotSeenOnceKeepsCountingAfterItDies() {
        TwoHatchLing strategy = new TwoHatchLing();

        assertFalse(strategy.observeNaturalDepot(false));
        assertTrue(strategy.observeNaturalDepot(true));
        assertTrue(strategy.observeNaturalDepot(false));
    }

    @Test
    void isAZergOnlyStrategy() {
        TwoHatchLing strategy = new TwoHatchLing();
        assertEquals("2HatchLing", strategy.getName());
        assertEquals(Race.Zerg, strategy.getRace());
    }
}
