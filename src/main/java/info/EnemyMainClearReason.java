package info;

/**
 * Why the enemy main stopped being the starting location it was.
 */
public enum EnemyMainClearReason {
    /**
     * The starting location was in our vision with no enemy building within the enemy-base check radius.
     */
    NO_BUILDING_SEEN,
    /**
     * The enemy depot on the starting location was destroyed.
     */
    DEPOT_DESTROYED,
    /**
     * A depot seen on another starting location replaced a main assigned from weaker evidence.
     */
    REPLACED_BY_DEPOT
}
