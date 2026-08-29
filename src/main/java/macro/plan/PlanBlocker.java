package macro.plan;

/**
 * Conditions that can prevent a plan from being scheduled.
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
