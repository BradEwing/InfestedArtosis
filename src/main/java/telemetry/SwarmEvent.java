package telemetry;

/**
 * Squad decision rows written about one of our active Dark Swarms.
 */
public enum SwarmEvent {

    /**
     * Sampled every {@code SquadManager} swarm sample interval for each melee squad within the commit radius of an
     * active swarm that covers enemies, locked or not, with the status the squad holds.
     */
    SWARM_ACTIVE,

    /**
     * A melee squad took a swarm lock.
     */
    SWARM_COMMIT,

    /**
     * A melee squad dropped its swarm lock.
     */
    SWARM_EXPIRED;

    /**
     * @return the decision path a row of this event names
     */
    public DecisionPath path() {
        switch (this) {
            case SWARM_COMMIT:
                return DecisionPath.SWARM_COMMIT;
            case SWARM_EXPIRED:
                return DecisionPath.SWARM_EXPIRED;
            default:
                return DecisionPath.SWARM_ACTIVE;
        }
    }
}
