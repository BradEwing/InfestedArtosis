package util;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelTimeTest {

    private static final double MAIN_TO_NATURAL_PIXELS = 1120;

    @Test
    void aWalkCostsItsDistanceOverTopSpeedPlusTheBuffer() {
        assertEquals(100 + TravelTime.PATHING_BUFFER_FRAMES, TravelTime.framesToReach(400, 4));
    }

    @Test
    void aUnitStandingOnTheTargetStillPaysTheBuffer() {
        assertEquals(TravelTime.PATHING_BUFFER_FRAMES, TravelTime.framesToReach(0, 4));
    }

    @Test
    void anImmobileUnitFallsBackToTheBuffer() {
        assertEquals(TravelTime.PATHING_BUFFER_FRAMES, TravelTime.framesToReach(400, 0));
    }

    @Test
    void theWalkToTheNaturalOutlastsAnAffordablePlansIncomePrediction() {
        int travelFrames = TravelTime.framesToReach(MAIN_TO_NATURAL_PIXELS, UnitType.Zerg_Drone.topSpeed());

        assertTrue(travelFrames > 380);
    }
}
