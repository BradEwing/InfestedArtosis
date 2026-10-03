package info.tracking.zerg;

import lombok.Builder;
import lombok.Value;
import util.Time;

/**
 * What we have scouted of a Zerg opponent's opening, as {@link ZergOpenerReading#classify} reads it.
 */
@Value
@Builder
public class ZergOpenerEvidence {
    /**
     * Current game time.
     */
    Time time;
    /**
     * Drones the enemy has produced as far as we have seen, see {@link info.tracking.DroneEquivalents}.
     */
    int equivalents;
    /**
     * A sign the Spawning Pool was started before any 12 pool starts one: the Pool seen early, seen complete
     * early, or a Zergling seen early.
     */
    boolean earlyPool;
    /**
     * The Spawning Pool was still morphing when observed at a time every 9 pool or Overpool has finished it.
     */
    boolean latePool;
    /**
     * An enemy Spawning Pool has been observed.
     */
    boolean poolSeen;
    /**
     * Our vision has covered the enemy main, see info.ScoutData#getEnemyMainScoutedFrame.
     */
    boolean mainScouted;
    /**
     * The latest frame the enemy natural's depot site was in our vision, or null if it never was.
     */
    Time naturalLastSeen;
    /**
     * The first frame a depot was seen at the enemy natural, or null if none has been.
     */
    Time naturalDepotFirstSeen;
}
