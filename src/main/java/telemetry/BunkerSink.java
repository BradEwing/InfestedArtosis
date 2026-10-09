package telemetry;

/**
 * Receives the Bunker telemetry events: repeat-advance decisions, Bunker hold changes and Bunker stance changes.
 */
public interface BunkerSink {

    /**
     * A repeat-advance gate decision about a squad and a Bunker in range.
     *
     * @param event the decision
     */
    void onAdvance(BunkerAdvanceEvent event);

    /**
     * The Bunker hold of the enemy natural or main started or ended.
     *
     * @param frame the frame of the change
     * @param event HOLD_START or HOLD_END
     * @param reason what the change was: NATURAL, MAIN or NATURAL+MAIN for a start, BROKEN or CLEARED for an end
     */
    void onHold(int frame, String event, String reason);

    /**
     * A Bunker stance, or a Drone round it opened, started or ended.
     *
     * @param event the change
     */
    void onStance(BunkerStanceEvent event);

    /**
     * A retreat was booked as a loss at a Bunker.
     *
     * @param event the loss
     */
    void onLoss(BunkerLossEvent event);

    /**
     * A ground squad was first seen in FIGHT near a living Bunker under its id.
     *
     * @param event the attack
     */
    void onAttack(BunkerAttackEvent event);
}
