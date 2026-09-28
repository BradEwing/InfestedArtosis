package unit.squad;

/**
 * Detects a contain that can neither break nor escalate, and takes the army out of it.
 *
 * <p>Against a mobile enemy army a contain's strength gate may never clear, and {@link ContainmentEscalation} never
 * fires because the defence is not static only, so each timeout retreats and the arc is re-taken straight away. A
 * timeout is a stalemate when the enemy has army outside its static defence and either the contain has already been
 * re-entered {@link #STALEMATE_AFTER_REENTRIES} times in a row after a timeout, counted by
 * {@link ContainmentEscalation}, or the break is out of reach even at the supply cap, see
 * {@link ContainmentEvaluator#breakUnreachable}.
 *
 * <p>A stalemate timeout retreats like any timeout, and then no squad may take an arc for {@link #HOLD_FRAMES}. The
 * army is left to the combat sim, which attacks what it can beat and retreats from what it cannot, so the enemy army
 * is met or let out instead of waited on at the choke. Once detected, the stalemate stays detected: the next contain
 * that times out is a stalemate at once, until a contain ends some other way than by timing out.
 *
 * <p>{@link #isDetected()} is the signal for the rest of the bot that the army cannot win by holding the enemy in.
 */
public class ContainmentStalemate {

    /**
     * Re-entries after a timeout that a further timeout against an army-backed defence reads as a stalemate: the
     * contain has then held its arc for three timeouts running without the gate clearing, the same run
     * {@link ContainmentEscalation#ESCALATE_AFTER_REENTRIES} asks of a static-only defence.
     */
    static final int STALEMATE_AFTER_REENTRIES = ContainmentEscalation.ESCALATE_AFTER_REENTRIES;

    /**
     * Frames after a stalemate during which no squad may take an arc: two containment timeouts, about two minutes,
     * long enough for the released army to cross to the enemy and fight, and longer than the
     * {@link ContainmentEscalation#REENTRY_WINDOW_FRAMES}, so the next contain starts a new run of re-entries.
     */
    static final int HOLD_FRAMES = 2 * SquadManager.CONTAINMENT_TIMEOUT_FRAMES;

    private boolean detected;
    private int detections;
    private int lastDetectedFrame = -1;
    private int holdUntilFrame;

    /**
     * Records that a contain timed out without escalating, and reports whether the timeout is a stalemate: one that
     * {@link #isStalemate} reads as such, or any timeout against an army-backed defence while a stalemate is
     * detected.
     *
     * @param reentries re-entries after a timeout in the current run, before this timeout
     * @param staticOnly true when the enemy has no known army outside its static defence
     * @param breakUnreachable true when the break would need more than the supply cap
     * @param currentFrame current frame
     * @return true when the contain is left as a stalemate and entry is held
     */
    public boolean onTimedOut(int reentries, boolean staticOnly, boolean breakUnreachable, int currentFrame) {
        if (staticOnly) {
            return false;
        }
        if (!detected && !isStalemate(reentries, false, breakUnreachable)) {
            return false;
        }
        detected = true;
        detections++;
        lastDetectedFrame = currentFrame;
        holdUntilFrame = currentFrame + HOLD_FRAMES;
        return true;
    }

    /**
     * Records that a contain ended some other way than by timing out, which clears a detected stalemate: the gate
     * cleared, the enemy moved the contain, or containment stopped applying.
     */
    public void onEndedOtherwise() {
        detected = false;
    }

    /**
     * Whether a stalemate still bars squads from taking an arc.
     *
     * @param currentFrame current frame
     * @return true inside the hold window of the last stalemate
     */
    public boolean holdsEntry(int currentFrame) {
        return currentFrame < holdUntilFrame;
    }

    /**
     * @return true while the last contain to end ended as a stalemate
     */
    public boolean isDetected() {
        return detected;
    }

    /**
     * @return stalemates detected this game
     */
    public int getDetections() {
        return detections;
    }

    /**
     * @return frame of the last stalemate, -1 when none
     */
    public int getLastDetectedFrame() {
        return lastDetectedFrame;
    }

    /**
     * Whether a timeout is a stalemate on its own evidence.
     *
     * @param reentries re-entries after a timeout in the current run, before this timeout
     * @param staticOnly true when the enemy has no known army outside its static defence
     * @param breakUnreachable true when the break would need more than the supply cap
     * @return true against an army-backed defence once the run reaches {@link #STALEMATE_AFTER_REENTRIES} or the
     *     break is out of reach
     */
    static boolean isStalemate(int reentries, boolean staticOnly, boolean breakUnreachable) {
        return !staticOnly && (breakUnreachable || reentries >= STALEMATE_AFTER_REENTRIES);
    }
}
