package telemetry;

/**
 * Which hysteresis lock held a squad on its current status when the combat sim asked for a
 * different one.
 *
 * <p>Named from the status the lock protects, matching Squad.startRetreatLock and
 * Squad.startFightLock. The three band entries name a ground squad held in FIGHT by the fight band
 * (FightBand) where the single engage cut would have let it go.
 */
public enum SquadLock {
    RETREAT,
    FIGHT,
    /**
     * The sim's band turned an engaged squad's RETREAT read inside the band into ENGAGE.
     */
    FIGHT_BAND,
    /**
     * The fight lock held a RETREAT read inside the band, which it releases at the engage threshold without the band.
     */
    FIGHT_LOCK_BAND,
    /**
     * The commit window a turn back out of a retreat opened held a RETREAT read at or above the retreat threshold.
     */
    COMMIT_WINDOW
}
