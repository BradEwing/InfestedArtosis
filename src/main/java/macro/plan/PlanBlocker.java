package macro.plan;

/** Conditions that can prevent a plan from being scheduled. */
public enum PlanBlocker {
    NONE,
    RESOURCES,
    BUILD_AHEAD_SLOT_TAKEN,
    BUILD_AHEAD_BACKOFF,
    NO_INCOME,
    NO_BUILD_POSITION,
    NO_LARVA,
    NO_CREEP_COLONY,
    NO_PRODUCER,
    UNSUPPORTED_PLAN_TYPE,
}
