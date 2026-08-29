package macro.plan;

/**
 * The single condition that stopped a plan being scheduled on the frame it was examined.
 *
 * <p>Returned by the scheduling attempt itself rather than recomputed afterwards, so the recorded
 * blocker cannot drift away from the check that actually rejected the plan.
 */
public enum PlanBlocker {

    /** The plan was scheduled; nothing is blocking it. */
    NONE,

    /** Available minerals or gas, after existing reservations, do not cover the plan. */
    RESOURCES,

    /** A building is unaffordable and the single build-ahead slot is already claimed. */
    BUILD_AHEAD_SLOT_TAKEN,

    /** No larva is free to morph the unit. */
    NO_LARVA,

    /** A colony morph has no completed, unassigned creep colony to morph from. */
    NO_CREEP_COLONY,

    /** Every building that could run the upgrade or research is busy or already assigned. */
    NO_PRODUCER,

    /** The plan type has no scheduling path. */
    UNSUPPORTED_PLAN_TYPE,
}
