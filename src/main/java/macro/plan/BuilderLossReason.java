package macro.plan;

/**
 * Why a dispatched builder no longer counts as the executor of its building plan.
 *
 * <p>ROLE_CHANGED is a builder whose role is no longer BUILD, so {@code ManagedUnit.build()} never
 * runs for it. PLAN_UNBOUND is a builder still in BUILD whose plan reference or plan assignment no
 * longer names the plan it was dispatched for. STRAYED is a builder still bound to the plan that has
 * receded from an affordable site, as {@link BuilderStray} measures it. DIED is a builder killed
 * while its plan was in BUILDING, which cancels the plan rather than returning it to SCHEDULE.
 */
public enum BuilderLossReason {
    ROLE_CHANGED(BuilderDispatchDecision.LOST_ROLE_CHANGED),
    PLAN_UNBOUND(BuilderDispatchDecision.LOST_PLAN_UNBOUND),
    STRAYED(BuilderDispatchDecision.LOST_STRAYED),
    DIED(BuilderDispatchDecision.LOST_DIED);

    private final BuilderDispatchDecision decision;

    BuilderLossReason(BuilderDispatchDecision decision) {
        this.decision = decision;
    }

    /** The dispatch decision the release is reported under. */
    public BuilderDispatchDecision decision() {
        return decision;
    }

    /**
     * The reason a dispatched builder is lost, or null while it still executes its plan. A role
     * change is read before the plan binding, and the binding before the stray.
     *
     * @param roleIsBuild whether the builder's role is still BUILD
     * @param planBound whether the builder's plan and its plan assignment both still name the plan
     * @param strayed whether {@link BuilderStray} reports the builder strayed from an affordable site
     */
    public static BuilderLossReason of(boolean roleIsBuild, boolean planBound, boolean strayed) {
        if (!roleIsBuild) {
            return ROLE_CHANGED;
        }
        if (!planBound) {
            return PLAN_UNBOUND;
        }
        if (strayed) {
            return STRAYED;
        }
        return null;
    }
}
