package telemetry;

/**
 * Static dispatch point for air harass telemetry. With no sink registered, every method is a no-op.
 */
public final class HarassTelemetry {

    private static HarassSink sink;

    private HarassTelemetry() {
    }

    public static void register(HarassSink harassSink) {
        sink = harassSink;
    }

    public static void clear() {
        sink = null;
    }

    public static void row(HarassRow row) {
        HarassSink current = sink;
        if (current == null) {
            return;
        }
        current.onRow(row);
    }
}
