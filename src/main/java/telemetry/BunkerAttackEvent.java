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
    private final int readFrame;
    private final int ledgerFrame;
    private final BunkerAdvanceEvent.Bunker bunker;

    /**
     * @param frame the frame the squad was first seen in FIGHT near the Bunker
     * @param squad the squad: its id, units and centre
     * @param gateRead the branch where the gate read the squad or a squad it was formed from after the latest loss
     *     on record at the Bunker, or null when it did not
     * @param readFrame the frame of the gate's latest read of the squad or a squad it was formed from, or -1 when it
     *     never read one
     * @param ledgerFrame the frame of the latest loss on record at the Bunker, or -1 when none is
     * @param bunker the nearest living Bunker
     */
    public BunkerAttackEvent(int frame, BunkerAdvanceEvent.Squad squad, BunkerAdvanceEntry gateRead, int readFrame,
                             int ledgerFrame, BunkerAdvanceEvent.Bunker bunker) {
        this.frame = frame;
        this.squadId = squad.getId();
        this.squadSize = squad.getSize();
        this.squadX = squad.getX();
        this.squadY = squad.getY();
        this.gateRead = gateRead;
        this.readFrame = readFrame;
        this.ledgerFrame = ledgerFrame;
        this.bunker = bunker;
    }
}
