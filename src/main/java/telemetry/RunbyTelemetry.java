package telemetry;

/**
 * Static dispatch point for runby telemetry. With no sink registered, every method is a no-op.
 */
public final class RunbyTelemetry {

    private static RunbySink sink;

    private RunbyTelemetry() {
    }

    public static void register(RunbySink runbySink) {
        sink = runbySink;
    }

    public static void clear() {
        sink = null;
    }

    public static void tick(RunbyTick tick) {
        RunbySink current = sink;
        if (current == null) {
            return;
        }
        current.onTick(tick);
    }
}
