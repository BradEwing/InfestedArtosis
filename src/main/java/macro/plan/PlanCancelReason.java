package macro.plan;

/**
 * Canonical reasons a plan is cancelled, shared by the call sites in {@link PlanCancelSite}.
 */
public enum PlanCancelReason {

    /** A prerequisite building or tech the plan needs does not exist and is not planned. */
    PREREQUISITE_MISSING,

    /** The prerequisite for this plan sits later in the same queue, so the plan can never drain. */
    PREREQUISITE_QUEUED_LATER,

    /** Free supply is already above the threshold that justified the overlord. */
    EXCESS_SUPPLY,

    /** Larva are floating with enough hatcheries, so another hatchery buys nothing. */
    EXCESS_HATCHERY,

    /** The unit that must morph into this plan does not exist. */
    NO_MORPH_SOURCE,

    /** The drone, larva or building executing the plan died or morphed into something else. */
    EXECUTOR_LOST,

    /** Larva deadlock recovery released every plan the larva were holding. */
    LARVA_DEADLOCK,

    /** The creep colony this morph was paired with was itself cancelled. */
    PAIRED_PLAN_CANCELLED,

    /** A defensive reaction dropped economy or expansion plans to fund defence. */
    REACTION_DEFENSE,

    /** A reaction cut gas income, so the extractor is no longer wanted. */
    REACTION_GAS_DENIED,

    /** A reaction pushed tech back past the point where this plan made sense. */
    REACTION_TECH_DELAYED,

    /** The build position is no longer buildable or no longer permitted. */
    POSITION_INVALID,

    /** An upgrade or research stopped progressing at the building that owned it. */
    RESEARCH_INTERRUPTED,

    /** The plan was already cancelled when its executor tried to release it. */
    ALREADY_IMPOSSIBLE,

    /** No call site claimed the cancellation. A row carrying this is a missing hook, not a cause. */
    UNKNOWN,
}
