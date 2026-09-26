package macro;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OverlordHoldTest {

    @Test
    void isFreeBeforeAnyHold() {
        assertEquals(OverlordHold.Phase.FREE, new OverlordHold().update(false));
    }

    @Test
    void releasesOnlyOnTheFrameTheHoldTurnsOff() {
        OverlordHold hold = new OverlordHold();

        assertEquals(OverlordHold.Phase.HELD, hold.update(true));
        assertEquals(OverlordHold.Phase.HELD, hold.update(true));
        assertEquals(OverlordHold.Phase.RELEASED, hold.update(false));
        assertEquals(OverlordHold.Phase.FREE, hold.update(false));
    }

    @Test
    void releasesAgainAfterTheHoldReturns() {
        OverlordHold hold = new OverlordHold();

        hold.update(true);
        hold.update(false);

        assertEquals(OverlordHold.Phase.HELD, hold.update(true));
        assertEquals(OverlordHold.Phase.RELEASED, hold.update(false));
    }
}
