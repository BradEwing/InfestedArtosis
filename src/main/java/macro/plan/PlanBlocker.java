package macro.plan;

/** Conditions that can prevent a plan from being scheduled. */
public enum PlanBlocker {
    NONE,
    RESOURCES,
    BUILD_AHEAD_SLOT_TAKEN,
    BUILD_AHEAD_BACKOFF,
    BUILD_AHEAD_TOO_FAR,
    NO_INCOME,
    NO_BUILD_POSITION,
    NO_LARVA,
    SUPPLY,
    NO_CREEP_COLONY,
    NO_PRODUCER,
    INSUFFICIENT_GATHERERS,
    TECH_MISSING,
    TECH_WAVE_RESERVE,
    /** A research or upgrade short only of minerals, holding the bank against the plans behind it. */
    RESEARCH_MINERALS,
    /** A plan behind a research or upgrade that holds the bank with {@link #RESEARCH_MINERALS}. */
    RESEARCH_CLAIM,
    UNSUPPORTED_PLAN_TYPE;

    /** The cancel reason a sweep records for a plan this blocker holds. */
    public PlanCancelReason cancelReason() {
        switch (this) {
            case INSUFFICIENT_GATHERERS:
                return PlanCancelReason.INSUFFICIENT_GATHERERS;
            case TECH_MISSING:
                return PlanCancelReason.TECH_MISSING;
            case NO_LARVA:
                return PlanCancelReason.NO_LARVA;
            case SUPPLY:
                return PlanCancelReason.SUPPLY_BLOCKED;
            default:
                return PlanCancelReason.PREREQUISITE_MISSING;
        }
    }
}
