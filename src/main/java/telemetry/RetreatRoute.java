package telemetry;

/**
 * Which way a retreating ground squad was sent by the retreat plan it follows.
 *
 * <p>Written in the retreat_route column on rows of a frame that planned ground retreat targets or kept the last
 * plan's, which includes the LOCK_SUPPRESSED rows of the retreat lock, and NONE on every other row.
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
    AWAY,

    /**
     * The enemy stands on home, so no path home clears it: each member walks the path home around every other threat
     * up to the edge of the danger radius of the enemy on home, or backs out of a danger radius. Not cornered, so the
     * squad does not turn to fight for it.
     */
    HOME_CONTESTED
}
