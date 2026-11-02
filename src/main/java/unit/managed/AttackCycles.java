package unit.managed;

import lombok.Getter;

/**
 * Splits a unit's attacks into cycles, read once a frame, each counted since the unit was first managed.
 *
 * <p>A cycle runs from one attack start to the next. It is counted only when the unit held a live fight target on
 * every frame between the two starts and kept the role it attacked in, so a squad retreat, a chase to a new target
 * or a role change between two shots does not stretch it. The frames after the weapon cooldown ended and before the
 * next attack started are added only for cycles that are counted.
 *
 * <p>Frames in cooldown inside an anti-air reach, and the hit points and shields lost in cooldown, are counted on
 * every frame the weapon is cooling down, whatever the cycle.
 */
@Getter
public final class AttackCycles {

    /** Cycles counted. */
    private int cycles;
    /** Frames between the two attack starts of every counted cycle, summed. */
    private int cycleFrames;
    /** Frames with the weapon cooling down while inside the reach of an enemy that can attack the unit. */
    private int cooldownFramesInThreat;
    /** Hit points plus shields lost on frames with the weapon cooling down. */
    private int cooldownDamageTaken;
    /** Frames of counted cycles with the weapon ready and no attack started. */
    private int readyFrames;

    private int lastStartFrame = -1;
    private boolean cycleHolds;
    private Object cycleRole;
    private int pendingReadyFrames;
    private int lastPool = -1;

    /**
     * Reads one frame.
     *
     * @param frame current frame
     * @param attackStarted true when the unit started an attack on this frame
     * @param cooldown frames left on the weapon cooldown, the larger of its weapons
     * @param targetHeld true when the unit holds a fight target that still exists
     * @param role the unit's role
     * @param inThreat true when the unit stands inside the reach of an enemy that can attack it
     * @param pool hit points plus shields
     */
    public void observe(int frame, boolean attackStarted, int cooldown, boolean targetHeld, Object role,
                        boolean inThreat, int pool) {
        if (cooldown > 0) {
            if (lastPool >= 0 && pool < lastPool) {
                cooldownDamageTaken += lastPool - pool;
            }
            if (inThreat) {
                cooldownFramesInThreat++;
            }
        }
        lastPool = pool;

        if (attackStarted) {
            if (lastStartFrame >= 0 && cycleHolds && targetHeld && role == cycleRole) {
                cycles++;
                cycleFrames += frame - lastStartFrame;
                readyFrames += pendingReadyFrames;
            }
            lastStartFrame = frame;
            cycleHolds = targetHeld;
            cycleRole = role;
            pendingReadyFrames = 0;
            return;
        }

        if (!targetHeld || role != cycleRole) {
            cycleHolds = false;
        }
        if (lastStartFrame >= 0 && cooldown <= 0) {
            pendingReadyFrames++;
        }
    }
}
