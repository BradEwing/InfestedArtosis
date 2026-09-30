package telemetry;

/**
 * What let a RETREAT verdict through an air squad's armed engage commitment.
 *
 * <p>Written on the row of the frame an air squad in FIGHT with an armed commitment takes a RETREAT verdict the
 * commitment does not hold against. When more than one term applies, the first in declaration order is written.
 */
public enum CommitmentRelease {
    /**
     * The verdict sampled a building that can attack air.
     */
    STATIC_AA,

    /**
     * The verdict's ratio fell below the release fraction of the engage threshold, or it had no threshold.
     */
    RATIO,

    /**
     * The commitment's window ran out.
     */
    EXPIRED,

    /**
     * The flock lost the release fraction of its peak hit points since the commitment was armed.
     */
    HP,

    /**
     * The flock held no hit points the commitment measures, so it had nothing to hold.
     */
    EMPTY_FLOCK,

    /**
     * No armed commitment was released on this row.
     */
    NONE
}
