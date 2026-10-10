package telemetry;

import lombok.Getter;

/**
 * A change of a Bunker stance, or of the Drone round it opened, as one BUNKER_ECON row reports it.
 */
@Getter
public final class BunkerStanceEvent {

    private final int frame;
    private final String event;
    private final String reason;
    private final int stanceId;
    private final int drones;
    private final int workers;
    private final int extraPlanned;
    private final int extraMade;

    /**
     * @param frame the frame of the change
     * @param event STANCE_START, ROUND_OPEN, ROUND_CLOSE or STANCE_END
     * @param reason why it changed
     * @param stanceId the stance's number in the game, from 1
     * @param drones Drones hatched or in an egg
     * @param workers workers gathering
     * @param extraPlanned the Drones the stance's rounds were set to add so far
     * @param extraMade the Drones the stance's closed rounds added so far
     */
    public BunkerStanceEvent(int frame, String event, String reason, int stanceId, int drones, int workers,
                             int extraPlanned, int extraMade) {
        this.frame = frame;
        this.event = event;
        this.reason = reason;
        this.stanceId = stanceId;
        this.drones = drones;
        this.workers = workers;
        this.extraPlanned = extraPlanned;
        this.extraMade = extraMade;
    }
}
