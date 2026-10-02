package unit.squad.horizon;

import bwapi.UnitType;
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
    void onlySiegedTanksInsideTheBandHoldTheVerdict() {
        assertTrue(SiegeBandHysteresis.inBand(UnitType.Terran_Siege_Tank_Siege_Mode, 400));
        assertTrue(SiegeBandHysteresis.inBand(UnitType.Terran_Siege_Tank_Siege_Mode, 912));
        assertFalse(SiegeBandHysteresis.inBand(UnitType.Terran_Siege_Tank_Siege_Mode, 399));
        assertFalse(SiegeBandHysteresis.inBand(UnitType.Terran_Siege_Tank_Siege_Mode, 913));
        assertFalse(SiegeBandHysteresis.inBand(UnitType.Terran_Siege_Tank_Tank_Mode, 600));
        assertFalse(SiegeBandHysteresis.inBand(UnitType.Terran_Marine, 600));
    }

    @Test
    void rawVerdictStandsOutsideTheBand() {
        assertEquals(RETREAT, SiegeBandHysteresis.apply(RETREAT, ENGAGE, HELD_SINCE, HELD_SINCE + 1, 0.5, THRESH,
                false));
    }

    @Test
    void rawVerdictStandsWithNothingHeld() {
        assertEquals(ENGAGE, SiegeBandHysteresis.apply(ENGAGE, null, 0, 5, 2.0, THRESH, true));
        assertEquals(RETREAT, SiegeBandHysteresis.apply(RETREAT, ADVANCE, HELD_SINCE, HELD_SINCE + 1, 0.5, THRESH,
                true));
    }

    @Test
    void advanceIsNeverHeld() {
        assertEquals(ADVANCE, SiegeBandHysteresis.apply(ADVANCE, ENGAGE, HELD_SINCE, HELD_SINCE + 1, 0, THRESH, true));
    }

    @Test
    void heldEngageSurvivesAnyDropInsideTheHoldWindow() {
        assertEquals(ENGAGE, SiegeBandHysteresis.apply(RETREAT, ENGAGE, HELD_SINCE, SETTLED - 1, 0.1, THRESH, true));
    }

    @Test
    void heldEngageSurvivesAShallowDipAfterTheHoldWindow() {
        double shallow = THRESH * (1 - SiegeBandHysteresis.MARGIN) + 0.01;
        assertEquals(ENGAGE, SiegeBandHysteresis.apply(RETREAT, ENGAGE, HELD_SINCE, SETTLED, shallow, THRESH, true));
    }

    @Test
    void heldEngageIsReplacedByADeepDipAfterTheHoldWindow() {
        double deep = THRESH * (1 - SiegeBandHysteresis.MARGIN) - 0.01;
        assertEquals(RETREAT, SiegeBandHysteresis.apply(RETREAT, ENGAGE, HELD_SINCE, SETTLED, deep, THRESH, true));
    }

    @Test
    void heldRetreatSurvivesAShallowRiseAfterTheHoldWindow() {
        double shallow = THRESH * (1 + SiegeBandHysteresis.MARGIN) - 0.01;
        assertEquals(RETREAT, SiegeBandHysteresis.apply(ENGAGE, RETREAT, HELD_SINCE, SETTLED, shallow, THRESH, true));
    }

    @Test
    void heldRetreatIsReplacedByAClearRiseAfterTheHoldWindow() {
        double clear = THRESH * (1 + SiegeBandHysteresis.MARGIN);
        assertEquals(ENGAGE, SiegeBandHysteresis.apply(ENGAGE, RETREAT, HELD_SINCE, SETTLED, clear, THRESH, true));
    }

    @Test
    void heldRetreatSurvivesAClearRiseInsideTheHoldWindow() {
        assertEquals(RETREAT, SiegeBandHysteresis.apply(ENGAGE, RETREAT, HELD_SINCE, SETTLED - 1, 5.0, THRESH,
                true));
    }
}
