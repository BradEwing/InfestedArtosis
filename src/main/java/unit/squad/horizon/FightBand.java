package unit.squad.horizon;

import unit.squad.CombatSimulator.CombatResult;
import unit.squad.SquadStatus;

/**
 * The band between a matchup's retreat threshold and its engage threshold, in which a ground squad keeps the verdict
 * it already acts on.
 *
 * <p>A squad that is not engaged needs its ratio to reach the engage threshold, see
 * {@link HorizonCombatSimulator#engageThreshold}, before it fights. A squad that is engaged stays in FIGHT until its
 * ratio falls below the retreat threshold, see {@link HorizonCombatSimulator#retreatThreshold}. A ratio inside the
 * band therefore keeps FIGHT for an engaged squad and keeps RETREAT for one that is not. Every rule here is off when
 * the switch passed to it is off, so the single engage cut decides as before.
 */
public final class FightBand {

    /**
     * Where a ratio sits against the two thresholds.
     */
    public enum Zone {
        BELOW_RETREAT,
        IN_BAND,
        CLEARS_ENGAGE
    }

    private FightBand() {
    }

    /**
     * Where a ratio sits against the retreat and engage thresholds.
     *
     * @param ratio the sim's overall strength ratio
     * @param retreatThreshold the retreat threshold, at most the engage threshold
     * @param engageThreshold the engage threshold
     * @return BELOW_RETREAT under the retreat threshold, CLEARS_ENGAGE at or above the engage threshold, and IN_BAND
     *     between them
     */
    public static Zone zone(double ratio, double retreatThreshold, double engageThreshold) {
        if (ratio < retreatThreshold) return Zone.BELOW_RETREAT;
        if (ratio >= engageThreshold) return Zone.CLEARS_ENGAGE;
        return Zone.IN_BAND;
    }

    /**
     * The retreat threshold a squad is judged against: the matchup's retreat threshold, lowered to the engage
     * threshold when static defence cover has brought the engage threshold below it, so the band is never inverted.
     *
     * @param matchupRetreatThreshold the matchup retreat threshold, see {@link HorizonCombatSimulator#retreatThreshold}
     * @param engageThreshold the engage threshold the squad is judged against this frame
     * @return the retreat threshold
     */
    public static double retreatThreshold(double matchupRetreatThreshold, double engageThreshold) {
        return Math.min(matchupRetreatThreshold, engageThreshold);
    }

    /**
     * Whether a ground squad counts as engaged for the band: the switch is on, the squad is in FIGHT, and its
     * previous sim read, taken no more than {@code maxGapFrames} ago, was ENGAGE.
     *
     * @param enabled whether the band is switched on
     * @param status the squad's status before this frame's verdict
     * @param previous the squad's previous verdict, null when it has none
     * @param previousFrame the frame of that verdict
     * @param frame the current frame
     * @param maxGapFrames the longest gap between reads that still continues the engagement
     * @return true when the squad is engaged
     */
    public static boolean engaged(boolean enabled, SquadStatus status, CombatResult previous, int previousFrame,
                                  int frame, int maxGapFrames) {
        return enabled && status == SquadStatus.FIGHT && previous == CombatResult.ENGAGE
                && frame - previousFrame <= maxGapFrames;
    }

    /**
     * The verdict a ground squad reports after the band. An engaged squad's RETREAT stands only when the ratio is
     * below the retreat threshold, and is ENGAGE otherwise. Any other verdict, and every verdict of a squad that is
     * not engaged, stands as the single engage cut gave it.
     *
     * @param raw the verdict from the engage threshold alone, see {@link HorizonCombatSimulator#selectResult}
     * @param engaged whether the squad is engaged, see {@link #engaged}
     * @param ratio the ratio the raw verdict was taken from
     * @param retreatThreshold the retreat threshold
     * @return the verdict to report
     */
    public static CombatResult apply(CombatResult raw, boolean engaged, double ratio, double retreatThreshold) {
        if (!engaged || raw != CombatResult.RETREAT) return raw;
        return ratio < retreatThreshold ? CombatResult.RETREAT : CombatResult.ENGAGE;
    }

    /**
     * The frame a run of band-held verdicts began, or -1 when this frame's verdict is not band-held.
     *
     * @param held whether the band turned this frame's RETREAT into ENGAGE
     * @param previousSinceFrame the frame the previous read's run began, -1 when that read was not band-held
     * @param frame the current frame
     * @return the start of the run, or -1
     */
    public static int heldSinceFrame(boolean held, int previousSinceFrame, int frame) {
        if (!held) return -1;
        return previousSinceFrame >= 0 ? previousSinceFrame : frame;
    }

    /**
     * The ratio below which a RETREAT read releases a fight lock: the retreat threshold with the band on, so the lock
     * holds through the band, and the engage threshold with it off.
     *
     * @param enabled whether the band is switched on for this squad
     * @param engageThreshold the engage threshold the sim judged the read against
     * @param retreatThreshold the retreat threshold the sim judged the read against
     * @return the release threshold
     */
    public static double fightLockReleaseThreshold(boolean enabled, double engageThreshold, double retreatThreshold) {
        return enabled ? retreatThreshold : engageThreshold;
    }

    /**
     * Whether a read keeps a ground squad above the retreat threshold, so its fight lock renews whatever supply it
     * has lost: the band is on, the read measured a real enemy, and the ratio is at or above the retreat threshold.
     *
     * @param enabled whether the band is switched on for this squad
     * @param enemyMeasured whether the sim measured a real enemy this frame
     * @param ratio the sim's overall strength ratio this frame
     * @param retreatThreshold the retreat threshold the sim judged the read against
     * @return true when the read holds the squad above the retreat threshold
     */
    public static boolean holdsAboveRetreat(boolean enabled, boolean enemyMeasured, double ratio,
                                            double retreatThreshold) {
        return enabled && enemyMeasured && retreatThreshold > 0 && ratio >= retreatThreshold;
    }
}
