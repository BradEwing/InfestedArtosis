package telemetry;

/**
 * Why a base check did not go where the scheduler would otherwise have sent it.
 */
public enum BaseCheckSkip {
    /** The route to the base passes a remembered static-defence death site. */
    DEATH_ROUTE,
    /** The route runs past a static defence that a check already out also passes. */
    SHARED_DEFENCE,
    /** A check already out was recalled because its route passes a death site recorded just now. */
    DEATH_RECALL
}
