package telemetry;

import bwapi.Position;
import unit.managed.UnitRole;
import unit.squad.OverlordParking;

/**
 * Static dispatch point for Overlord parking telemetry. With no sink registered, every method is a no-op.
 */
public final class OverlordParks {

    private static OverlordParkSink sink;

    private OverlordParks() {
    }

    public static void register(OverlordParkSink overlordParkSink) {
        sink = overlordParkSink;
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

    public static void anchorChanged(int frame, int unitId, Position position, Position from, Position to,
                                     OverlordParking.Reason reason) {
        OverlordParkSink current = sink;
        if (current == null) {
            return;
        }
        current.onAnchorChanged(frame, unitId, position, from, to, reason);
    }

    public static void died(int frame, int unitId, Position position, UnitRole role, double sporeDistance,
                            boolean parked) {
        OverlordParkSink current = sink;
        if (current == null) {
            return;
        }
        current.onOverlordDied(frame, unitId, position, role, sporeDistance, parked);
    }
}
