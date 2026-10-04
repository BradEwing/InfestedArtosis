package telemetry;

/**
 * Static dispatch point for Lurker burrow telemetry. With no sink registered, every method is a no-op.
 */
public final class BurrowTelemetry {

    private static BurrowSink sink;

    private BurrowTelemetry() {
    }

    public static void register(BurrowSink burrowSink) {
        sink = burrowSink;
    }

    public static void clear() {
        sink = null;
    }

    public static void burrowCommand(BurrowCommand command) {
        BurrowSink current = sink;
        if (current == null) {
            return;
        }
        current.onBurrowCommand(command);
    }
}
