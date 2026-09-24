package macro.plan;

/**
 * What the dispatch gate did with a scheduled drone-built building on the frame it evaluated it.
 *
 * <p>RECALLED is the same predicate applied to a builder already walking, so a plan can report
 * DISPATCH on one frame and RECALLED on a later one without anything else about it changing.
 * DISPATCH_HOME_SITE is a departure a threat reading would otherwise have held, waved through
 * because the site is a base we already hold.
 *
 * <p>The LOST_ decisions release a walking builder that has left its plan, one per
 * {@link BuilderLossReason}. The plan returns to SCHEDULE without an executor, so the DISPATCH that
 * follows it names a newly assigned builder. LOST_DIED is never a gate decision: it is the reason
 * a BUILDER_LOST row gives for a builder killed on its walk, whose plan is cancelled rather than
 * dispatched again.
 */
public enum BuilderDispatchDecision {
    DISPATCH,
    DISPATCH_HOME_SITE,
    HOLD_PATH_THREAT,
    HOLD_SITE_THREAT,
    RECALLED,
    LOST_ROLE_CHANGED,
    LOST_PLAN_UNBOUND,
    LOST_STRAYED,
    LOST_DIED;

    /** Whether the builder leaves, or stays out, under this decision. */
    public boolean isDispatch() {
        return this == DISPATCH || this == DISPATCH_HOME_SITE;
    }
}
