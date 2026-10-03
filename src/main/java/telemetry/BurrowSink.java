package telemetry;

import bwapi.Position;

/**
 * Receives every burrow and unburrow command a Lurker issues. Implementations are registered with
 * {@link BurrowTelemetry} and must never throw: they run inside the per frame unit update, where an escaped exception
 * kills the JVM.
 */
public interface BurrowSink {

    /**
     * A Lurker was ordered to burrow or unburrow.
     *
     * @param frame current frame
     * @param unitId the Lurker's unit id
     * @param burrow true for a burrow command, false for an unburrow command
     * @param reason why the command was issued
     * @param role name of the Lurker's role
     * @param position where the Lurker stood
     * @param hitPoints the Lurker's hit points
     * @param containPoint the Lurker's contain point, or null when it has none
     */
    void onBurrowCommand(int frame, int unitId, boolean burrow, BurrowReason reason, String role, Position position,
                         int hitPoints, Position containPoint);
}
