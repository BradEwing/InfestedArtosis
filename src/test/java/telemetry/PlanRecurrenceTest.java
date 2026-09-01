package telemetry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanRecurrenceTest {

    private static final int THRESHOLD = PlanRecurrence.THRESHOLD;

    private static final int WINDOW_FRAMES = PlanRecurrence.WINDOW_FRAMES;

    @Test
    void reportsOnTheThresholdCancellation() {
        PlanRecurrence recurrence = new PlanRecurrence();

        for (int i = 1; i < THRESHOLD; i++) {
            assertFalse(recurrence.record(i));
        }

        assertTrue(recurrence.record(THRESHOLD));
    }

    @Test
    void staysQuietBelowTheThreshold() {
        PlanRecurrence recurrence = new PlanRecurrence();

        for (int i = 0; i < THRESHOLD - 1; i++) {
            assertFalse(recurrence.record(i));
        }
    }

    @Test
    void cancellationsOutsideTheWindowNeverAccumulate() {
        PlanRecurrence recurrence = new PlanRecurrence();

        for (int i = 0; i < THRESHOLD * 3; i++) {
            assertFalse(recurrence.record(i * WINDOW_FRAMES));
        }
    }

    /**
     * The window must roll, not grow: five cancellations early in one window and a sixth a full
     * window later are two separate events, not a recurrence.
     */
    @Test
    void theWindowRollsAtItsBoundary() {
        PlanRecurrence recurrence = new PlanRecurrence();

        for (int i = 0; i < THRESHOLD - 1; i++) {
            assertFalse(recurrence.record(i));
        }

        assertFalse(recurrence.record(WINDOW_FRAMES));
        assertFalse(recurrence.record(WINDOW_FRAMES + 1));
    }

    @Test
    void reportsAgainOnTheNextThreshold() {
        PlanRecurrence recurrence = new PlanRecurrence();

        for (int i = 0; i < THRESHOLD; i++) {
            recurrence.record(i);
        }
        for (int i = 0; i < THRESHOLD - 1; i++) {
            assertFalse(recurrence.record(THRESHOLD + i));
        }

        assertTrue(recurrence.record(2 * THRESHOLD - 1));
    }
}
