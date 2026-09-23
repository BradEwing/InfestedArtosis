package telemetry;

import bwapi.Position;
import bwapi.UnitType;
import info.tracking.EnemyReachMemory;

/**
 * Static dispatch point for enemy reach telemetry. With no sink registered, every method is a no-op.
 */
public final class ReachTelemetry {

    private static ReachSink sink;

    private ReachTelemetry() {
    }

    public static void register(ReachSink reachSink) {
        sink = reachSink;
    }

    public static void clear() {
        sink = null;
    }

    public static void reachRaised(int frame, UnitType type, int oldReach, int newReach,
                                   EnemyReachMemory.Source source, Position victim, boolean capped) {
        ReachSink current = sink;
        if (current == null) {
            return;
        }
        current.onReachRaised(frame, type, oldReach, newReach, source, victim, capped);
    }
}
