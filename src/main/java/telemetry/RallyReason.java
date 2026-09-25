package telemetry;

/**
 * Why a fight squad is sitting at the rally point.
 *
 * <p>Recorded when the squad enters RALLY and carried on every row of that episode, including the
 * row that ends it, so an analyst can separate a dwell the bot is waiting out from one it never
 * intended to leave.
 */
public enum RallyReason {
    /**
     * No enemy is close and the squad is under the move out threshold: the ordinary case, a squad
     * batching reinforcements until it is strong enough to launch. A Defiler only ground squad that
     * takes this branch is recorded as DEFILER_ONLY, not here.
     */
    BELOW_MOVE_OUT,

    /**
     * A ground squad of Defilers only, whichever branch rallied it. SquadManager has a branch that
     * rallies such a squad rather than simulating a fight, and the ground move out threshold carries
     * no Defiler term, so one below it is rallied by the ordinary branch instead. Either way the
     * squad has no release it can reach on its own, so these episodes must be excluded from any
     * dwell metric. Taking precedence over the branch reason is what keeps them separable.
     */
    DEFILER_ONLY,

    /**
     * A reinforcement joined a rallying squad with no enemy inside its detection radius, so the
     * squad went back to staging rather than evaluating a fight.
     */
    STAGING,

    /**
     * The squad was held in place by a caller that had already decided not to advance, and was not
     * retreating.
     */
    HOLD,

    /**
     * An air squad under its move out threshold and not committed had enemies inside its detection radius, but
     * stood outside the home defence radius of every base we hold, so it was held at the rally point instead of
     * simulating the fight.
     */
    AIR_BELOW_MOVE_OUT_AWAY,

    /**
     * Another ground squad holds a containment arc, so the squad was sent to that arc instead of the rally point or
     * a march on an enemy building. The rally point of each member is the arc point nearest it, and the squad is
     * folded into the containing squad by the merge once the two are close.
     */
    JOIN_CONTAIN,

    /**
     * No rally entry has been recorded for this squad. A squad created in RALLY carries this until
     * the first frame something rallies it.
     */
    NONE
}
