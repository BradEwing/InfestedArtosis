package unit.squad.horizon;

import bwapi.UnitType;
import unit.squad.CombatSimulator.CombatResult;

/**
 * Holds a ground squad's ENGAGE or RETREAT verdict steady while a sieged tank sits in the band just beyond its fire.
 *
 * <p>A sieged tank between {@link #BAND_NEAR} and {@link #BAND_FAR} px from the squad is priced fully or not at
 * all as the squad steps across the simulator's engagement radius, so the raw verdict flips as the squad approaches
 * and backs off. In that band a verdict is replaced only when the ratio clears the engage threshold by
 * {@link #MARGIN} the other way, and only once {@link #MIN_HOLD_FRAMES} have passed since the verdict last changed.
 */
final class SiegeBandHysteresis {

    /**
     * Nearest distance from the squad centre at which a sieged tank holds the verdict.
     */
    static final double BAND_NEAR = 400;

    /**
     * Farthest distance from the squad centre at which a sieged tank holds the verdict.
     */
    static final double BAND_FAR = 912;

    /**
     * Fraction of the engage threshold the ratio must cross, on either side, to replace the held verdict.
     */
    static final double MARGIN = 0.15;

    /**
     * Frames a verdict is held before the band allows it to be replaced.
     */
    static final int MIN_HOLD_FRAMES = 48;

    private SiegeBandHysteresis() {
    }

    /**
     * Whether an enemy is a sieged tank inside the hysteresis band.
     *
     * @param type enemy unit type
     * @param distance pixels from the squad centre to the enemy
     * @return true for a sieged tank at least {@link #BAND_NEAR} and at most {@link #BAND_FAR} px away
     */
    static boolean inBand(UnitType type, double distance) {
        return type == UnitType.Terran_Siege_Tank_Siege_Mode && distance >= BAND_NEAR && distance <= BAND_FAR;
    }

    /**
     * The verdict a ground squad reports this frame.
     *
     * <p>Outside the band, or with no held ENGAGE or RETREAT verdict to keep, the raw verdict stands. In the band, a
     * held ENGAGE survives until the ratio falls below the engage threshold less {@link #MARGIN}, and a held RETREAT
     * until the ratio reaches the engage threshold plus {@link #MARGIN}; either is also kept until
     * {@link #MIN_HOLD_FRAMES} have passed since it was set. An ADVANCE raw verdict is never held, as it reports
     * that no enemy is measured.
     *
     * @param raw the verdict for this frame's strengths
     * @param held the verdict the squad last reported, null when it has none
     * @param heldSinceFrame the frame the held verdict was set
     * @param frame the current frame
     * @param ratio the ratio the raw verdict was taken from
     * @param engageThresh the matchup engage threshold
     * @param tankInBand whether a sieged tank sits in the band
     * @return the verdict to report
     */
    static CombatResult apply(CombatResult raw, CombatResult held, int heldSinceFrame, int frame, double ratio,
                              double engageThresh, boolean tankInBand) {
        if (!tankInBand || raw == CombatResult.ADVANCE || raw == held) return raw;
        if (held != CombatResult.ENGAGE && held != CombatResult.RETREAT) return raw;
        if (frame - heldSinceFrame < MIN_HOLD_FRAMES) return held;
        if (held == CombatResult.ENGAGE && ratio >= engageThresh * (1 - MARGIN)) return held;
        if (held == CombatResult.RETREAT && ratio < engageThresh * (1 + MARGIN)) return held;
        return raw;
    }
}
