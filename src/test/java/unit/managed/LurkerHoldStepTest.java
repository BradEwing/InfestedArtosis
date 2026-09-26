package unit.managed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LurkerHoldStepTest {

    private static final double FAR = 300;
    private static final double ARRIVED = Lurker.HOLD_ARRIVAL_DISTANCE;

    @Test
    void aBurrowedLurkerInFireUnburrowsFirst() {
        assertEquals(Lurker.HoldStep.UNBURROW, Lurker.holdStep(true, FAR));
    }

    @Test
    void anUnburrowedLurkerWalksToItsHoldPoint() {
        assertEquals(Lurker.HoldStep.MOVE, Lurker.holdStep(false, FAR));
        assertEquals(Lurker.HoldStep.MOVE, Lurker.holdStep(false, ARRIVED + 1));
    }

    @Test
    void itReburrowsOnArrival() {
        assertEquals(Lurker.HoldStep.BURROW, Lurker.holdStep(false, ARRIVED));
    }

    @Test
    void onceReburrowedAtThePointItHolds() {
        assertEquals(Lurker.HoldStep.HOLD, Lurker.holdStep(true, ARRIVED));
        assertEquals(Lurker.HoldStep.HOLD, Lurker.holdStep(true, 0));
    }

    @Test
    void evadeThenHoldRunsUnburrowMoveBurrowHold() {
        assertEquals(Lurker.HoldStep.UNBURROW, Lurker.holdStep(true, FAR));
        assertEquals(Lurker.HoldStep.MOVE, Lurker.holdStep(false, FAR));
        assertEquals(Lurker.HoldStep.BURROW, Lurker.holdStep(false, 10));
        assertEquals(Lurker.HoldStep.HOLD, Lurker.holdStep(true, 10));
    }
}
