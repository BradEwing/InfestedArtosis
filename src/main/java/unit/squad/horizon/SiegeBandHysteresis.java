package unit.squad.horizon;

import unit.squad.CombatSimulator.CombatResult;
import unit.squad.Squad;

/**
 * Holds a ground squad's ENGAGE or RETREAT verdict steady while a sieged tank sits in the band just beyond its fire.
 *
 * <p>A sieged tank between {@link #BAND_NEAR} and {@link #BAND_FAR} px from the squad is priced fully or not at
 * all as the squad steps across the simulator's engagement radius, so the raw verdict flips as the squad approaches
 * and backs off. With the nearest sieged tank in that band, a RETREAT is replaced by ENGAGE only once
 * {@link #MIN_HOLD_FRAMES} have passed since it was set, which outlasts the squad's retreat lock by
 * {@link #POST_LOCK_HOLD_FRAMES}, and the ratio clears the engage threshold by {@link #MARGIN}.
 */
final class SiegeBandHysteresis {

    /**
     * Nearest distance from the squad centre at which a sieged tank holds a RETREAT.
     */
    static final double BAND_NEAR = 400;

    /**
     * Farthest distance from the squad centre at which a sieged tank holds a RETREAT.
     */
    static final double BAND_FAR = 912;

    /**
     * Fraction of the engage threshold the ratio must exceed it by to replace a held RETREAT.
     */
    static final double MARGIN = 0.5;

    /**
     * Frames the hold runs after the ground retreat lock that every RETREAT starts has ended.
     */
    static final int POST_LOCK_HOLD_FRAMES = 120;

    /**
     * Frames a RETREAT is held before the band allows it to be replaced, counted from when it was set: the ground
     * retreat lock plus {@link #POST_LOCK_HOLD_FRAMES}.
     */
    static final int MIN_HOLD_FRAMES = Squad.GROUND_RETREAT_LOCK_FRAMES + POST_LOCK_HOLD_FRAMES;

    private SiegeBandHysteresis() {
    }

    /**
     * The nearer of two sieged tank distances.
     *
     * @param nearestSoFar pixels to the nearest sieged tank seen so far, infinite when none
     * @param candidate pixels to another sieged tank
     * @return the smaller distance
     */
    static double nearer(double nearestSoFar, double candidate) {
        return Math.min(nearestSoFar, candidate);
    }

    /**
     * Whether the nearest sieged tank sits inside the band. A sieged tank nearer than {@link #BAND_NEAR} is
     * already shelling the squad, so the verdict is never held on account of a tank farther out.
     *
     * @param nearestSiegedTank pixels from the squad centre to the nearest sieged tank, infinite when none is known
     * @return true when that distance is at least {@link #BAND_NEAR} and at most {@link #BAND_FAR}
     */
    static boolean inBand(double nearestSiegedTank) {
        return nearestSiegedTank >= BAND_NEAR && nearestSiegedTank <= BAND_FAR;
    }

    /**
     * The verdict a ground squad reports this frame.
     *
     * <p>The hold is one-sided: it keeps a RETREAT from reverting to ENGAGE, and never keeps an ENGAGE against a raw
     * RETREAT, so the squad always turns back on the frame the sim sees the fight is lost. Outside the band, or with
     * no held RETREAT, the raw verdict stands. In the band, a held RETREAT survives until the ratio reaches the
     * engage threshold plus {@link #MARGIN}, and is also kept until {@link #MIN_HOLD_FRAMES} have passed since it
     * was set. An ADVANCE raw verdict is never held, as it reports that no enemy is measured.
     *
     * @param raw the verdict for this frame's strengths
     * @param held the RETREAT the squad is holding, null when it holds none
     * @param heldSinceFrame the frame the held verdict was set
     * @param frame the current frame
     * @param ratio the ratio the raw verdict was taken from
     * @param engageThresh the matchup engage threshold
     * @param tankInBand whether the nearest sieged tank sits in the band, see {@link #inBand}
     * @return the verdict to report
     */
    static CombatResult apply(CombatResult raw, CombatResult held, int heldSinceFrame, int frame, double ratio,
                              double engageThresh, boolean tankInBand) {
        if (!tankInBand || raw != CombatResult.ENGAGE || held != CombatResult.RETREAT) return raw;
        if (frame - heldSinceFrame < MIN_HOLD_FRAMES) return held;
        if (ratio < engageThresh * (1 + MARGIN)) return held;
        return raw;
    }
}
