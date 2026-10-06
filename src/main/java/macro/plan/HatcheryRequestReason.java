package macro.plan;

/**
 * Which rule was true on the frame a hatchery plan was created. The expansion reasons are read
 * before the plan exists, so the plan's own raise of the floating-minerals bar is not in them.
 */
public enum HatcheryRequestReason {
    /** {@link info.GameState#isFloatingMinerals()} was true and base parity was not behind. */
    FLOATING_MINERALS,
    /** Base parity was behind and minerals were not floating. */
    BEHIND_ON_BASES,
    /** Both the floating-minerals and the base parity rules were true. */
    FLOATING_AND_BEHIND,
    /** Neither rule was true: the build's own schedule or base target asked for the expansion. */
    BUILD_ORDER,
    /** A macro hatchery, planned for larva rather than to claim a base. */
    MACRO,
    /** A macro hatchery a build released once its first army wave was scheduled; the excess sweep leaves it standing. */
    RELEASE;

    /**
     * The reason an expansion request is recorded under.
     *
     * @param floatingMinerals {@link info.GameState#isFloatingMinerals()} before the plan was created
     * @param behindOnBases the build order's base parity rule before the plan reserved its base
     */
    public static HatcheryRequestReason forExpansion(boolean floatingMinerals, boolean behindOnBases) {
        if (floatingMinerals) {
            return behindOnBases ? FLOATING_AND_BEHIND : FLOATING_MINERALS;
        }
        return behindOnBases ? BEHIND_ON_BASES : BUILD_ORDER;
    }
}
