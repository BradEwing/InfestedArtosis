package macro.plan;

/**
 * Which SpeedlingAllIn reaction against Protoss created a plan, written to the speedling_p_reaction
 * column of the plan telemetry.
 */
public enum SpeedlingReaction {
    /** A creep colony or sunken the Protoss sunken target asked for. */
    SPEEDLING_P_SUNKEN,
    /** A hatchery planned because the build was larva bound with its bank piling up. */
    LARVA_BOUND_HATCH
}
