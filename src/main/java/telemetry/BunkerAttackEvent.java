package telemetry;

import lombok.Getter;

/**
 * A ground squad seen in FIGHT within the gate's range of a living Bunker for the first time under its id, as one
 * BUNKER_ATTACK row reports it.
 */
@Getter
public final class BunkerAttackEvent {

    private final int frame;
    private final String squadId;
    private final int squadSize;
    private final int squadX;
    private final int squadY;
    private final BunkerAdvanceEntry gateRead;
    private final BunkerAdvanceEvent.Bunker bunker;

    /**
     * @param frame the frame the squad was first seen in FIGHT near the Bunker
     * @param squadId the squad's id
     * @param squadSize units in the squad
     * @param squadX the squad centre's x in pixels
     * @param squadY the squad centre's y in pixels
     * @param gateRead the branch where the gate read the squad or a squad it was formed from, or null when it never
     *     did
     * @param bunker the nearest living Bunker
     */
    public BunkerAttackEvent(int frame, String squadId, int squadSize, int squadX, int squadY,
                             BunkerAdvanceEntry gateRead, BunkerAdvanceEvent.Bunker bunker) {
        this.frame = frame;
        this.squadId = squadId;
        this.squadSize = squadSize;
        this.squadX = squadX;
        this.squadY = squadY;
        this.gateRead = gateRead;
        this.bunker = bunker;
    }
}
