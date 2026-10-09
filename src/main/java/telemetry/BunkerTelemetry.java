package telemetry;

/**
 * Static dispatch point for Bunker telemetry. With no sink registered, every method is a no-op.
 */
public final class BunkerTelemetry {

    private static BunkerSink sink;

    private BunkerTelemetry() {
    }

    public static void register(BunkerSink bunkerSink) {
        sink = bunkerSink;
    }

    public static void clear() {
        sink = null;
    }

    public static boolean enabled() {
        return sink != null;
    }

    public static void advance(BunkerAdvanceEvent event) {
        BunkerSink current = sink;
        if (current == null) {
            return;
        }
        current.onAdvance(event);
    }

    public static void hold(int frame, String event, String reason) {
        BunkerSink current = sink;
        if (current == null) {
            return;
        }
        current.onHold(frame, event, reason);
    }

    public static void stance(BunkerStanceEvent event) {
        BunkerSink current = sink;
        if (current == null) {
            return;
        }
        current.onStance(event);
    }

    public static void loss(BunkerLossEvent event) {
        BunkerSink current = sink;
        if (current == null) {
            return;
        }
        current.onLoss(event);
    }

    public static void attack(BunkerAttackEvent event) {
        BunkerSink current = sink;
        if (current == null) {
            return;
        }
        current.onAttack(event);
    }
}
