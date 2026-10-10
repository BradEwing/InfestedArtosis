package telemetry;

/**
 * The branch of the squad decision where the repeat-advance gate read a squad.
 */
public enum BunkerAdvanceEntry {
    /** A sim ADVANCE of a squad not yet in FIGHT. */
    ADVANCE,
    /** A sim ENGAGE of a squad not yet in FIGHT. */
    ENGAGE,
    /** A fight lock holding a squad in FIGHT that the gate had not read under its id. */
    FIGHT_LOCK
}
