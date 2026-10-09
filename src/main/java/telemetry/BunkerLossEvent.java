package telemetry;

import lombok.Getter;

/**
 * A retreat booked as a loss at a Bunker, as one LOSS_RECORDED row reports it.
 */
@Getter
public final class BunkerLossEvent {

    private final int frame;
    private final String squadId;
    private final int squadSize;
    private final int unitsLost;
    private final double price;
    private final BunkerAdvanceEvent.Bunker bunker;

    /**
     * @param frame the frame of the retreat
     * @param squadId the retreating squad's id
     * @param squadSize units in the squad
     * @param unitsLost units of ours that died at the Bunker in the window the loss counts
     * @param price the enemy strength the retreat was priced against
     * @param bunker the Bunker the loss was booked against
     */
    public BunkerLossEvent(int frame, String squadId, int squadSize, int unitsLost, double price,
                           BunkerAdvanceEvent.Bunker bunker) {
        this.frame = frame;
        this.squadId = squadId;
        this.squadSize = squadSize;
        this.unitsLost = unitsLost;
        this.price = price;
        this.bunker = bunker;
    }
}
