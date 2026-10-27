package telemetry;

/**
 * Static dispatch point for gas cut telemetry. With no sink registered, every method is a no-op.
 */
public final class GasCutTelemetry {

    private static GasCutSink sink;

    private GasCutTelemetry() {
    }

    public static void register(GasCutSink gasCutSink) {
        sink = gasCutSink;
    }

    public static void clear() {
        sink = null;
    }

    public static void gasCut(int frame, String event, boolean applied, String planType, int planGas,
                              int availableGas, int gasWorkers) {
        GasCutSink current = sink;
        if (current == null) {
            return;
        }
        current.onGasCut(frame, event, applied, planType, planGas, availableGas, gasWorkers);
    }
}
