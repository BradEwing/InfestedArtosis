package macro.plan;

/**
 * What the dispatch gate did with a scheduled drone-built building on the frame it evaluated it.
 *
 * <p>RECALLED is the same predicate applied to a builder already walking, so a plan can report
 * DISPATCH on one frame and RECALLED on a later one without anything else about it changing.
 */
public enum BuilderDispatchDecision {
    DISPATCH,
    HOLD_PATH_THREAT,
    HOLD_SITE_THREAT,
    RECALLED,
}
