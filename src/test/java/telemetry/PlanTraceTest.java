package telemetry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanTraceTest {

    private static final int FRAME = 9649;

    @Test
    void aStaleRowIsReportedOncePerStateUntilTheStateChanges() {
        PlanTrace trace = new PlanTrace(FRAME);

        assertTrue(trace.markStaleReported());
        assertFalse(trace.markStaleReported());

        trace.clearStaleReported();

        assertTrue(trace.markStaleReported());
    }
}
