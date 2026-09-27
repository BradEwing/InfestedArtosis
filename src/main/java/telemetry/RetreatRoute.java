package telemetry;

/**
 * Which way a retreating ground squad was sent on the frame its retreat targets were planned.
 *
 * <p>Written in the retreat_route column on rows of a frame that planned ground retreat targets, and NONE on every
 * other row.
 */
public enum RetreatRoute {
    /**
     * No ground retreat was planned this frame.
     */
    NONE,

    /**
     * Every member walks the ground path home, which runs clear of the enemy.
     */
    HOME,

    /**
     * The direct path home runs through the enemy, and at least one member walks a longer path around it.
     */
    DETOUR,

    /**
     * No member has a path home clear of the enemy, so each backs off to the reachable point farthest from it.
     */
    CORNERED,

    /**
     * The home point had no walkable tile near it, so the squad backed straight away from the enemy.
     */
    AWAY
}
