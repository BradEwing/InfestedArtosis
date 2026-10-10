package telemetry;

/**
 * Why the repeat-advance gate allowed or held an advance on a Bunker, written on the BUNKER_ADVANCE row.
 */
public enum BunkerAdvanceReason {
    /**
     * No Bunker within range of the squad is a remembered loss and none stands in range: nothing to decide.
     */
    NO_BUNKER(false),

    /**
     * A Bunker stands in range and no loss against it is on record, so the advance is the first attack on it, or the
     * first since the record ended.
     */
    NO_LOSS(false),

    /**
     * The squad is not mostly melee, so the gate does not apply.
     */
    NOT_MELEE(false),

    /**
     * The sim reads the Bunker as one the squad breaks, so the advance is the attack the gate leaves alone.
     */
    SIM_BREAKS(false),

    /**
     * The squad's strength reached the strength the lost engagement priced; the record ended.
     */
    RELEASED_STRENGTH(false),

    /**
     * The Bunker was seen damaged since the loss; the record ended.
     */
    RELEASED_DAMAGE(false),

    /**
     * The Bunker is no longer a living observed Bunker; the record ended.
     */
    RELEASED_DEAD(false),

    /**
     * The timeout ran out since the loss; the record ended.
     */
    RELEASED_TIMEOUT(false),

    /**
     * The build is an all-in by design and the gate does not apply to it; the record, if any, stands.
     */
    EXEMPT_BUILD(false),

    /**
     * The squad is within the re-hold margin below the strength the lost engagement priced and was not held before:
     * the advance is allowed and the record stands.
     */
    IN_PRICE_BAND(false),

    /**
     * The squad lost an engagement at the Bunker, the sim priced it as a loss, and nothing has released the record.
     */
    HELD_LOSS(true);

    private final boolean held;

    BunkerAdvanceReason(boolean held) {
        this.held = held;
    }

    /**
     * @return true when the advance is held back
     */
    public boolean isHeld() {
        return held;
    }
}
