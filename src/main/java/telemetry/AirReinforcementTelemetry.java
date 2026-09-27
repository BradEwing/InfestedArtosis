package telemetry;

/**
 * Static dispatch point for air reinforcement telemetry. With no sink registered, every method is a no-op.
 */
public final class AirReinforcementTelemetry {

    private static AirReinforcementSink sink;

    private AirReinforcementTelemetry() {
    }

    public static void register(AirReinforcementSink airReinforcementSink) {
        sink = airReinforcementSink;
    }

    public static void clear() {
        sink = null;
    }

    public static void row(AirReinforcementRow row) {
        AirReinforcementSink current = sink;
        if (current == null) {
            return;
        }
        current.onRow(row);
    }
}
