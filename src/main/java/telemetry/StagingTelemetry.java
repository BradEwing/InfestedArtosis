package telemetry;

/**
 * Static dispatch point for standoff staging telemetry. With no sink registered, every method is a no-op.
 */
public final class StagingTelemetry {

    private static StagingSink sink;

    private StagingTelemetry() {
    }

    public static void register(StagingSink stagingSink) {
        sink = stagingSink;
    }

    public static void clear() {
        sink = null;
    }

    public static void standoffEnter(int frame, StagingRow row) {
        StagingSink current = sink;
        if (current == null) {
            return;
        }
        current.onStandoffEnter(frame, row);
    }

    public static void standoffExit(int frame, String reason, StagingRow row) {
        StagingSink current = sink;
        if (current == null) {
            return;
        }
        current.onStandoffExit(frame, reason, row);
    }

    public static void reinforcement(int frame, StagingRow row) {
        StagingSink current = sink;
        if (current == null) {
            return;
        }
        current.onReinforcement(frame, row);
    }
}
