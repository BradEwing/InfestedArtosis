package unit.managed;

import lombok.Getter;

/**
 * Splits a unit's attacks into cycles, read once a frame, each counted since the unit was first managed.
 *
 * <p>A cycle runs from one attack start to the next. It is counted only when, on every frame between the two starts,
 * the unit held the same live fight target and the role it attacked in, so a squad retreat, a switch to another
 * target or a role change between two shots does not stretch it. A counted cycle is also an in-reach cycle when the
 * target stood within the unit's weapon range, as the game reports it, at both starts. The frames after the weapon
 * cooldown ended and before the next attack started are added only for counted cycles.
 *
 * <p>Cooldown frames, frames in cooldown inside an anti-air reach, and the hit points and shields lost in cooldown are
 * counted on every frame the weapon is cooling down, whatever the cycle. Hit points and shields lost are also counted
 * on every frame, as the sum of the drops, so regeneration does not hide them.
 *
 * <p>The kite counters record how each planned cooldown came out, see {@link CooldownKite.Outcome}, counting each
 * cooldown's first plan; a cooldown planned again is counted once more as a re-plan, not again by outcome.
 */
@Getter
public final class AttackCycles {

    /** Cycles counted. */
    private int cycles;
    /** Frames between the two attack starts of every counted cycle, summed. */
    private int cycleFrames;
    /** Counted cycles with the target within weapon range at both starts. */
    private int inReachCycles;
    /** Frames of every in-reach cycle, summed. */
    private int inReachCycleFrames;
    /** Frames with the weapon cooling down. */
    private int cooldownFrames;
    /** Frames with the weapon cooling down while inside the reach of an enemy that can attack the unit. */
    private int cooldownFramesInThreat;
    /** Hit points plus shields lost on frames with the weapon cooling down. */
    private int cooldownDamageTaken;
    /** Frames of counted cycles with the weapon ready and no attack started. */
    private int readyFrames;
    /** Hit points plus shields lost on every frame, summed over the drops. */
    private int hitPointsLost;
    /** Cooldowns planned. */
    private int kitePlans;
    /** Plans that backed off. */
    private int kiteBackoffs;
    /** Plans with a counted threat in reach but no back-off that clears it. */
    private int kiteNoClear;
    /** Plans with no counted threat in reach. */
    private int kiteNoThreat;
    /** Cooldowns planned with no counted threat that were planned again once a static threat came near. */
    private int kiteReplans;

    private int lastStartFrame = -1;
    private boolean cycleHolds;
    private boolean startInReach;
    private UnitRole cycleRole;
    private int cycleTargetId = -1;
    private int pendingReadyFrames;
    private int lastPool = -1;

    /**
     * Reads one frame.
     *
     * @param frame current frame
     * @param attackStarted true when the unit started an attack on this frame
     * @param cooldown frames left on the weapon cooldown, the larger of its weapons
     * @param targetId id of the live fight target the unit holds, -1 for none
     * @param targetInReach true when that target stands within the unit's weapon range
     * @param role the unit's role
     * @param inThreat true when the unit stands inside the reach of an enemy that can attack it
     * @param pool hit points plus shields
     */
    public void observe(int frame, boolean attackStarted, int cooldown, int targetId, boolean targetInReach,
                        UnitRole role, boolean inThreat, int pool) {
        if (lastPool >= 0 && pool < lastPool) {
            hitPointsLost += lastPool - pool;
            if (cooldown > 0) {
                cooldownDamageTaken += lastPool - pool;
            }
        }
        lastPool = pool;
        if (cooldown > 0) {
            cooldownFrames++;
            if (inThreat) {
                cooldownFramesInThreat++;
            }
        }

        boolean holds = targetId >= 0 && targetId == cycleTargetId && role == cycleRole;
        if (attackStarted) {
            if (lastStartFrame >= 0 && cycleHolds && holds) {
                cycles++;
                cycleFrames += frame - lastStartFrame;
                readyFrames += pendingReadyFrames;
                if (startInReach && targetInReach) {
                    inReachCycles++;
                    inReachCycleFrames += frame - lastStartFrame;
                }
            }
            lastStartFrame = frame;
            cycleHolds = targetId >= 0;
            startInReach = targetInReach;
            cycleRole = role;
            cycleTargetId = targetId;
            pendingReadyFrames = 0;
            return;
        }

        if (!holds) {
            cycleHolds = false;
        }
        if (lastStartFrame >= 0 && cooldown <= 0) {
            pendingReadyFrames++;
        }
    }

    /**
     * Counts one planned cooldown.
     *
     * @param outcome how the plan came out
     */
    public void notePlan(CooldownKite.Outcome outcome) {
        kitePlans++;
        switch (outcome) {
            case BACK_OFF:
                kiteBackoffs++;
                break;
            case NO_CLEAR:
                kiteNoClear++;
                break;
            default:
                kiteNoThreat++;
                break;
        }
    }

    /**
     * Counts one cooldown planned again after it was planned with no counted threat.
     */
    public void noteReplan() {
        kiteReplans++;
    }
}
