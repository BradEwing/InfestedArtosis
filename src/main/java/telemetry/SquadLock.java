package telemetry;

/**
 * Which hysteresis lock held a squad on its current status when the combat sim asked for a
 * different one.
 *
 * <p>Named from the status the lock protects, matching Squad.startRetreatLock and
 * Squad.startFightLock.
 */
public enum SquadLock {
    RETREAT,
    FIGHT
}
