package util;

/**
 * Hysteresis gate deciding when a melee attacker whose best pick is saturated stops attacking that target directly
 * and attack-moves past it instead, leaving the choice of what to hit to the game's own auto-acquire.
 *
 * <p>The attacker's targeting pass reports each frame whether its pick was saturated and whether the pick is a
 * re-target: a different target from the one the attacker held, or a first target after it held none or its target
 * died. A saturated re-target enters overflow at once, so a pack re-targeting together when their target dies does
 * not pile onto the next one while a streak builds. A target the attacker already held that becomes saturated
 * enters overflow once the pick has been saturated for {@link #ENTER_FRAMES} consecutive frames. Entering arms the
 * overflow lock for {@link #MIN_HOLD_FRAMES}. An unsaturated re-target leaves overflow at once, even during the lock,
 * so an attacker whose target died and that found an open slot takes it and is counted on it. Otherwise the
 * attacker leaves overflow only once an unsaturated pick has been available for {@link #EXIT_FRAMES} consecutive
 * frames after the lock expired; open frames during the lock do not count. A frame without a report breaks both
 * streaks and ends overflow, so an attacker returning to the fight after a retreat, a rally or a frame without
 * candidates starts over with direct attacks.
 */
public final class MeleeOverflowGate {

    /**
     * Consecutive frames a pick the attacker already held must be saturated before it switches to an attack-move.
     */
    public static final int ENTER_FRAMES = 12;

    /**
     * Consecutive frames after the lock expires that an unsaturated pick must be available before the attacker
     * returns to a direct attack.
     */
    public static final int EXIT_FRAMES = 24;

    /**
     * Frames the attacker holds the attack-move after entering overflow, whatever picks of its held target it is
     * offered.
     */
    public static final int MIN_HOLD_FRAMES = 48;

    private static final int NONE = -1;

    private int lastReportFrame = NONE;
    private int saturatedSince = NONE;
    private int unsaturatedSince = NONE;
    private int overflowStartFrame = NONE;
    private int overflowLockedUntilFrame = NONE;

    /**
     * Reports this frame's pick of a target the attacker already held.
     *
     * @param saturated true when the attacker's pick is saturated
     * @param frame the current frame
     * @return true while the attacker is in overflow
     * @see #observe(boolean, boolean, int)
     */
    public boolean observe(boolean saturated, int frame) {
        return observe(saturated, false, frame);
    }

    /**
     * Reports this frame's pick and returns whether the attacker should attack-move rather than attack its pick.
     * A second report on the same frame replaces the saturation seen on it without extending either streak.
     *
     * @param saturated true when the attacker's pick is saturated
     * @param retargeted true when the pick is not the target the attacker held, or it held none that still exists
     * @param frame the current frame
     * @return true while the attacker is in overflow
     */
    public boolean observe(boolean saturated, boolean retargeted, int frame) {
        if (lastReportFrame == NONE || frame - lastReportFrame > 1) {
            saturatedSince = NONE;
            unsaturatedSince = NONE;
            clearOverflowStart();
        }
        lastReportFrame = frame;
        if (saturated) {
            unsaturatedSince = NONE;
            saturatedSince = saturatedSince == NONE ? frame : saturatedSince;
        } else {
            saturatedSince = NONE;
            unsaturatedSince = unsaturatedSince == NONE ? frame : unsaturatedSince;
        }
        if (!isOverflowing()) {
            if (saturated && retargeted || streak(saturatedSince, frame) >= ENTER_FRAMES) {
                startOverflowLock(frame);
            }
        } else if (!saturated && retargeted
                || !isOverflowLocked(frame) && streak(openSinceLockExpired(), frame) >= EXIT_FRAMES) {
            clearOverflowStart();
        }
        return isOverflowing();
    }

    /**
     * Enters overflow and arms the lock that holds it for {@link #MIN_HOLD_FRAMES}.
     *
     * @param frame frame the attacker enters overflow on
     */
    public void startOverflowLock(int frame) {
        overflowStartFrame = frame;
        overflowLockedUntilFrame = frame + MIN_HOLD_FRAMES;
    }

    /**
     * @return true while the minimum hold after entering overflow has not yet run out
     */
    public boolean isOverflowLocked(int frame) {
        return isOverflowing() && frame < overflowLockedUntilFrame;
    }

    /**
     * Leaves overflow and drops its lock.
     */
    public void clearOverflowStart() {
        overflowStartFrame = NONE;
        overflowLockedUntilFrame = NONE;
    }

    /**
     * @return true while the attacker attack-moves instead of attacking its pick
     */
    public boolean isOverflowing() {
        return overflowStartFrame != NONE;
    }

    private int openSinceLockExpired() {
        return unsaturatedSince == NONE ? NONE : Math.max(unsaturatedSince, overflowLockedUntilFrame);
    }

    private static int streak(int since, int frame) {
        return since == NONE ? 0 : frame - since + 1;
    }
}
