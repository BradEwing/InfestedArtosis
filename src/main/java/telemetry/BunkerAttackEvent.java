package telemetry;

import lombok.Getter;

/**
 * A ground squad in FIGHT within reach of a living Bunker, reported when it first is under its id and again when what
 * is on record at the Bunker, or what the gate weighed, changes, as one BUNKER_ATTACK row reports it.
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
    private final boolean exempt;
    private final BunkerAdvanceEvent.Bunker bunker;

    /**
     * @param frame the frame the squad was first seen in FIGHT near the Bunker
     * @param squad the squad: its id, units and centre
     * @param gateRead the branch where the gate read the squad or a squad it was formed from after the latest loss
     *     on record at the Bunker, or null when it did not
     * @param readFrame the frame of the gate's latest read of the squad or a squad it was formed from, or -1 when it
     *     never read one
     * @param ledgerFrame the frame of the latest loss on record at the Bunker, or -1 when none is
     * @param exempt whether the build is exempt from the gate, so the squad was never going to be read
     * @param bunker the nearest living Bunker
     */
    public BunkerAttackEvent(int frame, BunkerAdvanceEvent.Squad squad, BunkerAdvanceEntry gateRead, int readFrame,
                             int ledgerFrame, boolean exempt, BunkerAdvanceEvent.Bunker bunker) {
        this.frame = frame;
        this.squadId = squad.getId();
        this.squadSize = squad.getSize();
        this.squadX = squad.getX();
        this.squadY = squad.getY();
        this.gateRead = gateRead;
        this.readFrame = readFrame;
        this.ledgerFrame = ledgerFrame;
        this.exempt = exempt;
        this.bunker = bunker;
    }
}
