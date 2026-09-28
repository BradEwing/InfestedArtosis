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
 *
 * <p>While a stalemate is detected and our supply used reaches {@link #COMMIT_SUPPLY_USED}, the ground army commits:
 * every ground squad fights toward the enemy whatever the combat sim reads, and no squad may take an arc, until the
 * ground army falls below half the supply it committed with or no enemy target is known, see {@link #onFrame}. The
 * army then remaxes under the normal rules, and commits again once it is maxed while the stalemate is still detected.
 */
public class ContainmentStalemate {

    /**
     * Supply used, in BWAPI half-supply, at or above which a detected stalemate commits the ground army: 380, 190 of
     * the 200 cap. Production has all but stopped there, so waiting longer adds no army, while the 10 supply of slack
     * keeps a maxed army that has lost a few units or a remax that is a larva cycle short from missing the trigger.
     */
    static final int COMMIT_SUPPLY_USED = 380;

    /**
     * The commit releases once the ground army is below this share of the supply it committed with: half. By then the
     * attack has spent its trade, and the combat sim takes the rest home to remax instead of feeding it in.
     */
    static final double COMMIT_RELEASE_SHARE = 0.5;

    /**
     * What a frame's {@link #onFrame} did to the commit.
     */
    enum CommitChange {
        NONE,
        STARTED,
        RELEASED
    }

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
    private boolean committing;
    private int committedSupply;
    private int commits;

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

    /**
     * Starts or releases the maxed-army commit for this frame.
     *
     * @param supplyUsed our supply used, in BWAPI half-supply
     * @param armySupply supply of our ground fight squads, in BWAPI half-supply
     * @param targetKnown true while an enemy building or the enemy main is known to march on
     * @return STARTED on the frame the commit starts, RELEASED on the frame it ends, else NONE
     */
    public CommitChange onFrame(int supplyUsed, int armySupply, boolean targetKnown) {
        if (committing) {
            if (!commitReleases(armySupply, committedSupply, targetKnown)) {
                return CommitChange.NONE;
            }
            committing = false;
            return CommitChange.RELEASED;
        }
        if (!commitStarts(detected, supplyUsed, armySupply, targetKnown)) {
            return CommitChange.NONE;
        }
        committing = true;
        committedSupply = armySupply;
        commits++;
        return CommitChange.STARTED;
    }

    /**
     * Whether no squad may take an arc: inside a stalemate's hold window or while the army is committed.
     *
     * @param currentFrame current frame
     * @return true when containment entry is barred by the stalemate
     */
    public boolean barsEntry(int currentFrame) {
        return committing || holdsEntry(currentFrame);
    }

    /**
     * @return true while the ground army is committed
     */
    public boolean isCommitting() {
        return committing;
    }

    /**
     * @return ground army supply the running or last commit started with, in BWAPI half-supply
     */
    public int getCommittedSupply() {
        return committedSupply;
    }

    /**
     * @return commits started this game
     */
    public int getCommits() {
        return commits;
    }

    /**
     * Whether a frame starts the maxed-army commit.
     *
     * @param detected true while a stalemate is detected
     * @param supplyUsed our supply used, in BWAPI half-supply
     * @param armySupply supply of our ground fight squads, in BWAPI half-supply
     * @param targetKnown true while an enemy building or the enemy main is known to march on
     * @return true for a detected stalemate with supply used at {@link #COMMIT_SUPPLY_USED}, a ground army to commit
     *     and a target to march on
     */
    static boolean commitStarts(boolean detected, int supplyUsed, int armySupply, boolean targetKnown) {
        return detected && targetKnown && armySupply > 0 && supplyUsed >= COMMIT_SUPPLY_USED;
    }

    /**
     * Whether a running commit releases.
     *
     * @param armySupply supply of our ground fight squads, in BWAPI half-supply
     * @param committedSupply ground army supply the commit started with
     * @param targetKnown true while an enemy building or the enemy main is known to march on
     * @return true once the army is below {@link #COMMIT_RELEASE_SHARE} of its committed supply or no target is known
     */
    static boolean commitReleases(int armySupply, int committedSupply, boolean targetKnown) {
        return !targetKnown || armySupply < committedSupply * COMMIT_RELEASE_SHARE;
    }

    /**
     * Whether a committed squad keeps retreating from a Psionic Storm rather than fighting: it retreated from one
     * and its retreat lock still holds. The commit itself runs on; the squad fights again once the lock expires.
     *
     * @param status the squad's status
     * @param retreatLocked true while the squad's retreat lock holds
     * @return true while the storm retreat holds the squad
     */
    static boolean stormRetreatHolds(SquadStatus status, boolean retreatLocked) {
        return status == SquadStatus.RETREAT && retreatLocked;
    }
}
