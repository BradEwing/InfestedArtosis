package macro.plan;

/**
 * What the dispatch gate did with a scheduled drone-built building on the frame it evaluated it.
 *
 * <p>RECALLED is the same predicate applied to a builder already walking, so a plan can report
 * DISPATCH on one frame and RECALLED on a later one without anything else about it changing.
 * DISPATCH_HOME_SITE is a departure a threat reading would otherwise have held, waved through
 * because the site is a base we already hold.
 */
public enum BuilderDispatchDecision {
    DISPATCH,
    DISPATCH_HOME_SITE,
    HOLD_PATH_THREAT,
    HOLD_SITE_THREAT,
    RECALLED;

    /** Whether the builder leaves, or stays out, under this decision. */
    public boolean isDispatch() {
        return this == DISPATCH || this == DISPATCH_HOME_SITE;
    }
}
