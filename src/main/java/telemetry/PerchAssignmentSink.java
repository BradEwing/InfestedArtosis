package telemetry;

import bwapi.Position;
import unit.managed.ManagedUnit;

/**
 * Receives perch assignments. Implementations are registered with {@link PerchAssignments} and must
 * never throw: they run inside the per frame scout loop, where an escaped exception kills the JVM.
 */
public interface PerchAssignmentSink {

    /**
     * A scout was given a perch position and moved to {@code UnitRole.PERCH}.
     *
     * @param scout the unit being perched, still at the position it was assigned from
     * @param perch the perch position it was given
     * @param watchTarget the position the perch is meant to watch
     * @param usedPerchTile true when {@code perch} is a computed perch tile, false when the scout
     *     holds over the watch target itself because no perch tile was selected
     */
    void onPerchAssigned(ManagedUnit scout, Position perch, Position watchTarget, boolean usedPerchTile);
}
