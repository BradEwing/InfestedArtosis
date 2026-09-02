package strategy.buildorder.terran;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerranBaseTest {

    private static final int DRONES_AT_POOL_COMPLETION = 10;
    private static final int UNSCOUTED_ZERGLING_FLOOR = 4;

    @Test
    void yieldsTheLarvaToZerglingsOnThePoolCompletionFrame() {
        assertFalse(TerranBase.shouldDroneBeforeZerglings(DRONES_AT_POOL_COMPLETION, 0, UNSCOUTED_ZERGLING_FLOOR));
    }

    @Test
    void keepsYieldingWhileTheZerglingFloorIsUnmet() {
        assertFalse(TerranBase.shouldDroneBeforeZerglings(DRONES_AT_POOL_COMPLETION, 2, UNSCOUTED_ZERGLING_FLOOR));
    }

    @Test
    void resumesDroningOnceTheZerglingFloorIsMet() {
        assertTrue(TerranBase.shouldDroneBeforeZerglings(DRONES_AT_POOL_COMPLETION, UNSCOUTED_ZERGLING_FLOOR, UNSCOUTED_ZERGLING_FLOOR));
    }

    @Test
    void dronesFreelyWhenNoZerglingsAreOwed() {
        assertTrue(TerranBase.shouldDroneBeforeZerglings(DRONES_AT_POOL_COMPLETION, 0, 0));
    }

    @Test
    void stopsTheEarlyDroneGateAtTheTarget() {
        assertFalse(TerranBase.shouldDroneBeforeZerglings(TerranBase.EARLY_DRONE_TARGET, UNSCOUTED_ZERGLING_FLOOR, UNSCOUTED_ZERGLING_FLOOR));
    }
}
