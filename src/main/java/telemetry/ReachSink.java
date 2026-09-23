package telemetry;

import bwapi.Position;
import bwapi.UnitType;
import info.tracking.EnemyReachMemory;

/**
 * Receives increases of learned enemy reach. Implementations are registered with {@link ReachTelemetry} and must
 * never throw: they run inside the per frame information update, where an escaped exception kills the JVM.
 */
public interface ReachSink {

    /**
     * A type's known ground reach rose, or a hit no known enemy accounts for left a new mark.
     *
     * @param frame current frame
     * @param type enemy type whose reach rose, or null for a new hurt mark
     * @param oldReach reach known before, or -1 for a new hurt mark
     * @param newReach reach known now, or the mark's radius
     * @param source what raised it
     * @param victim where the unit that was hit stood, or null for a reach read from the API
     * @param capped true when the observed reach was past the type's cap and newReach is the cap
     */
    void onReachRaised(int frame, UnitType type, int oldReach, int newReach, EnemyReachMemory.Source source,
                       Position victim, boolean capped);
}
