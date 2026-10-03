package telemetry;

import bwapi.Position;

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

    public static void burrowCommand(int frame, int unitId, boolean burrow, BurrowReason reason, String role,
                                     Position position, int hitPoints, Position containPoint) {
        BurrowSink current = sink;
        if (current == null) {
            return;
        }
        current.onBurrowCommand(frame, unitId, burrow, reason, role, position, hitPoints, containPoint);
    }
}
