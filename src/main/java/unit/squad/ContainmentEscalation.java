package unit.squad;

/**
 * Tracks contains that time out and are re-entered in a row, and escalates the run into a committed attack once
 * the enemy is known to defend with static defence only.
 *
 * <p>A contain that times out is let go and, because the strength gate still reads the same, is usually re-entered
 * on the next frame. Each entry that follows a timeout counts as a re-entry. Any other end to a contain, a break,
 * attrition, an outranging hit or containment ceasing to apply, clears the run. Once
 * {@link #ESCALATE_AFTER_REENTRIES} re-entries have timed out again against a static-only defence, the timeout
 * escalates instead of retreating, and no squad may take an arc for {@link #ENTRY_HOLD_FRAMES}, so the combat sim
 * decides the attack rather than the next contain.
 */
public class ContainmentEscalation {

    /**
     * Re-entries after a timeout that a further timeout escalates: the contain has then held its arc for three
     * timeouts running without the gate or a threat moving it.
     */
    static final int ESCALATE_AFTER_REENTRIES = 2;

    /**
     * Frames after an escalation during which no squad may take an arc: one containment timeout, so the combat sim
     * decides the attack for as long as a single contain would have held.
     */
    static final int ENTRY_HOLD_FRAMES = 1400;

    private boolean timedOutLast;
    private int reentries;
    private int holdUntilFrame;

    /**
     * Records that a squad took a containment arc.
     */
    public void onEntered() {
        if (timedOutLast) {
            reentries++;
            timedOutLast = false;
        }
    }

    /**
     * Records that a contain timed out, and reports whether this timeout escalates.
     *
     * @param staticOnly true when the enemy has no known army outside its static defence
     * @param currentFrame current frame
     * @return true when the contain escalates into a committed attack instead of retreating
     */
    public boolean onTimedOut(boolean staticOnly, int currentFrame) {
        if (escalates(reentries, staticOnly)) {
            reentries = 0;
            timedOutLast = false;
            holdUntilFrame = currentFrame + ENTRY_HOLD_FRAMES;
            return true;
        }
        timedOutLast = true;
        return false;
    }

    /**
     * Records that a contain ended some other way than by timing out, which clears the run of re-entries.
     */
    public void onEndedOtherwise() {
        reentries = 0;
        timedOutLast = false;
    }

    /**
     * Whether an escalation still bars squads from taking an arc.
     *
     * @param currentFrame current frame
     * @return true inside the hold window of the last escalation
     */
    public boolean holdsEntry(int currentFrame) {
        return currentFrame < holdUntilFrame;
    }

    public int getReentries() {
        return reentries;
    }

    /**
     * Whether a timed-out contain escalates.
     *
     * @param reentries re-entries after a timeout in the current run
     * @param staticOnly true when the enemy has no known army outside its static defence
     * @return true when the run has reached {@link #ESCALATE_AFTER_REENTRIES} against a static-only defence
     */
    static boolean escalates(int reentries, boolean staticOnly) {
        return staticOnly && reentries >= ESCALATE_AFTER_REENTRIES;
    }
}
