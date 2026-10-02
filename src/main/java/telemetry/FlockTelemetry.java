package telemetry;

/**
 * Static dispatch point for air flock telemetry. With no sink registered, every method is a no-op.
 */
public final class FlockTelemetry {

    private static FlockSink sink;

    private FlockTelemetry() {
    }

    public static void register(FlockSink flockSink) {
        sink = flockSink;
    }

    public static void clear() {
        sink = null;
    }

    /**
     * @return true when a sink is registered, so a caller can skip building rows nobody reads
     */
    public static boolean enabled() {
        return sink != null;
    }

    public static void row(FlockRow row) {
        FlockSink current = sink;
        if (current == null) {
            return;
        }
        current.onRow(row);
    }
}
