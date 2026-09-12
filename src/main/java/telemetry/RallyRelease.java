package telemetry;

/**
 * What ended a squad's stay at the rally point.
 *
 * <p>Written on the row that closes a RALLY episode. Without it a long dwell is indistinguishable
 * from a stall, because the row leaving RALLY records the new status but not the term that allowed
 * it.
 */
public enum RallyRelease {
    /**
     * Enemies came inside the squad detection radius, so the squad evaluated a fight where it
     * stood.
     */
    CLOSE_THREATS,

    /**
     * The squad reached the move out threshold and was cleared to launch.
     */
    MOVE_OUT_THRESHOLD,

    /**
     * The squad was already committed and far enough from the rally point that the threshold no
     * longer recalls it.
     */
    COMMITTED_DOWNFIELD,

    /**
     * The squad stopped existing: merged into another squad, emptied by losses, or disbanded for
     * want of targets. The episode ends here rather than going unlogged.
     */
    DISBANDED,

    /**
     * This row does not close a RALLY episode.
     */
    NONE
}
