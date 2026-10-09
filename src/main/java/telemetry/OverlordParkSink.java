package telemetry;

import bwapi.Position;
import unit.managed.UnitRole;
import unit.squad.OverlordParking;

/**
 * Receives Overlord parking events. Implementations are registered with {@link OverlordParks} and must never
 * throw: they run inside the per frame squad loop, where an escaped exception kills the JVM.
 */
public interface OverlordParkSink {

    /**
     * A parked Overlord's anchor changed.
     *
     * @param frame the current frame
     * @param unitId the Overlord's unit id
     * @param position where the Overlord is
     * @param from the previous anchor, or null for none
     * @param to the new anchor
     * @param reason why it changed
     */
    void onAnchorChanged(int frame, int unitId, Position position, Position from, Position to,
                         OverlordParking.Reason reason);

    /**
     * One of our Overlords died.
     *
     * @param frame the current frame
     * @param unitId the Overlord's unit id
     * @param position where it died
     * @param role its role when it died
     * @param sporeDistance pixels to the nearest completed own Spore Colony, or -1 with none
     * @param parked whether it was in the Overlord squad
     */
    void onOverlordDied(int frame, int unitId, Position position, UnitRole role, double sporeDistance,
                        boolean parked);
}
