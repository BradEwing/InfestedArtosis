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
    INSUFFICIENT_GATHERERS,
    TECH_MISSING,
    UNSUPPORTED_PLAN_TYPE;

    /**
     * The reason recorded when a sweep cancels a plan this blocker holds. Blockers without a
     * reason of their own report the prerequisite as missing.
     */
    public PlanCancelReason cancelReason() {
        switch (this) {
            case INSUFFICIENT_GATHERERS:
                return PlanCancelReason.INSUFFICIENT_GATHERERS;
            case TECH_MISSING:
                return PlanCancelReason.TECH_MISSING;
            case NO_LARVA:
                return PlanCancelReason.NO_LARVA;
            default:
                return PlanCancelReason.PREREQUISITE_MISSING;
        }
    }
}
