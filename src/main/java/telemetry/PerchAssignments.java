package telemetry;

import bwapi.Position;
import unit.managed.ManagedUnit;

/**
 * Static dispatch point for perch assignments. With no sink registered, every method is a no-op.
 */
public final class PerchAssignments {

    private static PerchAssignmentSink sink;

    private PerchAssignments() {
    }

    public static void register(PerchAssignmentSink perchAssignmentSink) {
        sink = perchAssignmentSink;
    }

    public static void clear() {
        sink = null;
    }

    public static void assigned(ManagedUnit scout, Position perch, Position watchTarget, boolean usedPerchTile) {
        PerchAssignmentSink current = sink;
        if (current == null) {
            return;
        }
        current.onPerchAssigned(scout, perch, watchTarget, usedPerchTile);
    }
}
