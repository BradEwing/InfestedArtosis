package unit.squad.horizon;

import unit.squad.CombatSimulator.CombatResult;
import unit.squad.SquadStatus;

/**
 * The band between a matchup's retreat threshold and its engage threshold, in which a ground squad keeps the verdict
 * it already acts on.
 *
 * <p>A squad that is not engaged needs its ratio to reach the engage threshold, see
 * {@link HorizonCombatSimulator#engageThreshold}, before it fights. A squad that is engaged and in contact stays in
 * FIGHT until its ratio falls below the retreat threshold, see {@link HorizonCombatSimulator#retreatThreshold}; one
 * that is engaged but out of contact stays only while the fight scores at least even, see {@link #holdThreshold}. A
 * ratio inside the band therefore keeps FIGHT for an engaged squad and keeps RETREAT for one that is not, and a
 * squad that retreated on a measured read waits for a measured ENGAGE before turning back, see
 * {@link #blindTurnBackHeld}. Every rule here is off when the switch passed to it is off, so the single engage cut
 * decides as before.
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

    /**
     * The ratio at which the sim scores a fight even. An engaged squad out of contact is held in FIGHT only down to
     * this ratio, see {@link #holdThreshold}.
     */
    public static final double EVEN_RATIO = 1.0;

    private FightBand() {
    }

    /**
     * The ratio below which an engaged squad's RETREAT read stands. In contact, where leaving costs the units the
     * enemy catches, it is the retreat threshold. Out of contact, where the squad can still leave for free, the squad
     * is held only on a fight the sim scores at least even: the larger of the retreat threshold and
     * {@link #EVEN_RATIO}, never above the engage threshold.
     *
     * @param inContact whether a member attacked or was hit recently, see {@link unit.squad.Squad#isInContact}
     * @param retreatThreshold the retreat threshold, at most the engage threshold
     * @param engageThreshold the engage threshold
     * @return the hold threshold
     */
    public static double holdThreshold(boolean inContact, double retreatThreshold, double engageThreshold) {
        if (inContact) return retreatThreshold;
        return Math.min(engageThreshold, Math.max(retreatThreshold, EVEN_RATIO));
    }

    /**
     * Whether one unit is in contact this frame: it has a weapon cooling down from a shot, is starting an attack, or
     * is under attack.
     *
     * @param groundWeaponCooldown the unit's ground weapon cooldown
     * @param airWeaponCooldown the unit's air weapon cooldown
     * @param startingAttack whether the unit is starting an attack
     * @param underAttack whether the unit is under attack
     * @return true when the unit is in contact
     */
    public static boolean unitInContact(int groundWeaponCooldown, int airWeaponCooldown, boolean startingAttack,
                                        boolean underAttack) {
        return groundWeaponCooldown > 0 || airWeaponCooldown > 0 || startingAttack || underAttack;
    }

    /**
     * Whether a read is a measured RETREAT that opens or extends a retreating ground squad's blind turn-back gate, see
     * {@link unit.squad.Squad#noteMeasuredRetreat}.
     *
     * @param enabled whether the band is switched on for this squad
     * @param result this frame's verdict
     * @param enemyMeasured whether the sim measured a real enemy this frame
     * @return true for a measured RETREAT with the band on
     */
    public static boolean measuredRetreat(boolean enabled, CombatResult result, boolean enemyMeasured) {
        return enabled && enemyMeasured && result == CombatResult.RETREAT;
    }

    /**
     * Whether a retreating ground squad is kept in RETREAT on a read that measured no enemy. Such a read is an
     * ADVANCE, which on its own would turn the squad back into FIGHT and march it on the enemy it just left. Inside
     * the gate a squad turns back only on a measured ENGAGE, a read at or above the engage threshold; once the gate
     * has run out, an ADVANCE turns it back as before.
     *
     * @param enabled whether the band is switched on for this squad
     * @param status the squad's status before this frame's verdict
     * @param result this frame's verdict
     * @param gated whether the squad is inside its blind turn-back gate, see
     *     {@link unit.squad.Squad#isBlindTurnBackGated}
     * @return true when the squad stays in RETREAT
     */
    public static boolean blindTurnBackHeld(boolean enabled, SquadStatus status, CombatResult result,
                                            boolean gated) {
        return enabled && gated && status == SquadStatus.RETREAT && result == CombatResult.ADVANCE;
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
     * Whether a ground squad counts as engaged for the band: the switch is on, the squad is in FIGHT, and either it
     * is committed to the fight (its fight lock or turn-back commit is active) or its previous sim read, taken no
     * more than {@code maxGapFrames} ago, was ENGAGE. The commitment carries the engagement across a merge or split,
     * which starts a squad with no previous read but with the folded locks of its sources, and across a single read
     * that was not ENGAGE. A FIGHT squad held by a contain break or a collapse commit holds a fight lock too, so it
     * is engaged as well.
     *
     * @param enabled whether the band is switched on
     * @param status the squad's status before this frame's verdict
     * @param committed whether the squad's fight lock or turn-back commit is active this frame
     * @param previous the squad's previous verdict, null when it has none
     * @param previousFrame the frame of that verdict
     * @param frame the current frame
     * @param maxGapFrames the longest gap between reads that still continues the engagement
     * @return true when the squad is engaged
     */
    public static boolean engaged(boolean enabled, SquadStatus status, boolean committed, CombatResult previous,
                                  int previousFrame, int frame, int maxGapFrames) {
        if (!enabled || status != SquadStatus.FIGHT) return false;
        return committed || previous == CombatResult.ENGAGE && frame - previousFrame <= maxGapFrames;
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
