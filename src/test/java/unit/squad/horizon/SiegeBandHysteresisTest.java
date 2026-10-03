package unit.squad.horizon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.CombatSimulator.CombatResult.ADVANCE;
import static unit.squad.CombatSimulator.CombatResult.ENGAGE;
import static unit.squad.CombatSimulator.CombatResult.RETREAT;

class SiegeBandHysteresisTest {

    private static final double THRESH = 1.44;
    private static final int HELD_SINCE = 1000;
    private static final int SETTLED = HELD_SINCE + SiegeBandHysteresis.MIN_HOLD_FRAMES;

    @Test
    void theBandRunsFromFourHundredToNineHundredTwelvePixels() {
        assertTrue(SiegeBandHysteresis.inBand(400));
        assertTrue(SiegeBandHysteresis.inBand(912));
        assertFalse(SiegeBandHysteresis.inBand(399));
        assertFalse(SiegeBandHysteresis.inBand(913));
        assertFalse(SiegeBandHysteresis.inBand(Double.POSITIVE_INFINITY));
    }

    @Test
    void aRawRetreatAlwaysStands() {
        assertEquals(RETREAT, SiegeBandHysteresis.apply(RETREAT, ENGAGE, HELD_SINCE, HELD_SINCE + 1, 0.1, THRESH,
                true));
        assertEquals(RETREAT, SiegeBandHysteresis.apply(RETREAT, RETREAT, HELD_SINCE, HELD_SINCE + 1, 0.1, THRESH,
                true));
    }

    @Test
    void aHeldEngageIsNeverKeptAgainstARawRetreat() {
        assertEquals(RETREAT, SiegeBandHysteresis.apply(RETREAT, ENGAGE, HELD_SINCE, SETTLED + 500, 1.3, THRESH,
                true));
    }

    @Test
    void rawEngageStandsOutsideTheBand() {
        assertEquals(ENGAGE, SiegeBandHysteresis.apply(ENGAGE, RETREAT, HELD_SINCE, HELD_SINCE + 1, 0.5, THRESH,
                false));
    }

    @Test
    void rawEngageStandsWithNoRetreatHeld() {
        assertEquals(ENGAGE, SiegeBandHysteresis.apply(ENGAGE, null, 0, 5, 2.0, THRESH, true));
        assertEquals(ENGAGE, SiegeBandHysteresis.apply(ENGAGE, ADVANCE, HELD_SINCE, HELD_SINCE + 1, 2.0, THRESH,
                true));
        assertEquals(ENGAGE, SiegeBandHysteresis.apply(ENGAGE, ENGAGE, HELD_SINCE, HELD_SINCE + 1, 2.0, THRESH, true));
    }

    @Test
    void advanceIsNeverHeld() {
        assertEquals(ADVANCE, SiegeBandHysteresis.apply(ADVANCE, RETREAT, HELD_SINCE, HELD_SINCE + 1, 0, THRESH,
                true));
    }

    @Test
    void aHeldRetreatSurvivesAClearRiseInsideTheHoldWindow() {
        assertEquals(RETREAT, SiegeBandHysteresis.apply(ENGAGE, RETREAT, HELD_SINCE, SETTLED - 1, 5.0, THRESH,
                true));
    }

    @Test
    void aHeldRetreatSurvivesAShallowRiseAfterTheHoldWindow() {
        double shallow = THRESH * (1 + SiegeBandHysteresis.MARGIN) - 0.01;
        assertEquals(RETREAT, SiegeBandHysteresis.apply(ENGAGE, RETREAT, HELD_SINCE, SETTLED, shallow, THRESH, true));
    }

    @Test
    void aHeldRetreatIsReplacedByAClearRiseAfterTheHoldWindow() {
        double clear = THRESH * (1 + SiegeBandHysteresis.MARGIN);
        assertEquals(ENGAGE, SiegeBandHysteresis.apply(ENGAGE, RETREAT, HELD_SINCE, SETTLED, clear, THRESH, true));
    }

    @Test
    void theNearestTankIsTheSmallestDistanceSeen() {
        double nearest = Double.POSITIVE_INFINITY;
        for (double d : new double[]{600, 200, 700}) nearest = SiegeBandHysteresis.nearer(nearest, d);

        assertEquals(200, nearest, 0);
        assertFalse(SiegeBandHysteresis.inBand(nearest));
    }

    @Test
    void theHoldOutlastsTheGroundRetreatLock() {
        int lockEnds = HELD_SINCE + unit.squad.Squad.GROUND_RETREAT_LOCK_FRAMES;

        assertTrue(SiegeBandHysteresis.MIN_HOLD_FRAMES > unit.squad.Squad.GROUND_RETREAT_LOCK_FRAMES);
        assertEquals(RETREAT, SiegeBandHysteresis.apply(ENGAGE, RETREAT, HELD_SINCE, lockEnds, 5.0, THRESH, true));
        assertEquals(RETREAT, SiegeBandHysteresis.apply(ENGAGE, RETREAT, HELD_SINCE, lockEnds
                + SiegeBandHysteresis.POST_LOCK_HOLD_FRAMES - 1, 5.0, THRESH, true));
        assertEquals(ENGAGE, SiegeBandHysteresis.apply(ENGAGE, RETREAT, HELD_SINCE, lockEnds
                + SiegeBandHysteresis.POST_LOCK_HOLD_FRAMES, 5.0, THRESH, true));
    }

    @Test
    void aRatioOfOnePointFourTimesTheThresholdIsHeldAfterTheWindow() {
        assertEquals(RETREAT, SiegeBandHysteresis.apply(ENGAGE, RETREAT, HELD_SINCE, SETTLED, THRESH * 1.4, THRESH,
                true));
    }

    @Test
    void theBandClockCountsElapsedFramesPerBandState() {
        HorizonCombatSimulator.BandClock clock = new HorizonCombatSimulator.BandClock();

        clock.tick(100, HorizonCombatSimulator.SIEGE_BAND_NONE);
        clock.tick(110, HorizonCombatSimulator.SIEGE_BAND_IN);
        clock.tick(130, HorizonCombatSimulator.SIEGE_BAND_HELD);
        clock.tick(135, HorizonCombatSimulator.SIEGE_BAND_NONE);

        assertEquals(10, clock.inBandFrames);
        assertEquals(5, clock.heldFrames);
    }

    @Test
    void aGapBetweenSimRunsAddsNoMoreThanTheCap() {
        HorizonCombatSimulator.BandClock clock = new HorizonCombatSimulator.BandClock();

        clock.tick(100, HorizonCombatSimulator.SIEGE_BAND_IN);
        clock.tick(2980, HorizonCombatSimulator.SIEGE_BAND_IN);

        assertEquals(HorizonCombatSimulator.MAX_SIM_GAP_FRAMES, clock.inBandFrames);
    }

}
