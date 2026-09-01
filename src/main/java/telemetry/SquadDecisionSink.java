package telemetry;

import unit.squad.CombatSimulator;
import unit.squad.Squad;

/**
 * Receives the inputs SquadManager used to pick a squad status. Implementations must never throw:
 * they run inside the per frame squad loop, where an escaped exception kills the JVM.
 */
public interface SquadDecisionSink {

    /**
     * The combat sim verdict for a squad, together with the lock state read just before it.
     */
    void onSimEvaluated(Squad squad, CombatSimulator.CombatResult result, boolean retreatLocked, boolean fightLocked);

    /**
     * A lock kept the squad on its current status after the simulator asked for the other one.
     */
    void onLockSuppressed(Squad squad, SquadLock lock);

    /**
     * A split that would have fragmented the squad below the move out floor.
     *
     * <p>Fires for every declined split, whether the squad is committed or not. The
     * {@code committed} column on the resulting row records which case applies.
     */
    void onSplitSuppressed(Squad squad, int moveOutThreshold, int squadStrength, int outlierStrength);

    /**
     * The containment verdict for a squad that was eligible to enter an arc this frame.
     *
     * <p>canBreakContainment is only meaningful when shouldContain is true.
     */
    void onContainmentEvaluated(Squad squad, boolean shouldContain, boolean canBreakContainment, boolean entered);
}
