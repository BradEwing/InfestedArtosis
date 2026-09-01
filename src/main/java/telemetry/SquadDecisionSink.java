package telemetry;

import unit.squad.CombatSimulator;
import unit.squad.Squad;

/**
 * Receives the inputs SquadManager used to pick a squad status. Implementations are registered with
 * {@link SquadDecisions} and must never throw: they run inside the per frame squad loop, where an
 * escaped exception kills the JVM.
 */
public interface SquadDecisionSink {

    /**
     * The combat sim verdict for a squad, together with the lock state read just before it.
     *
     * @param squad squad the verdict was computed for
     * @param result verdict the simulator returned
     * @param retreatLocked whether the retreat hysteresis lock was held at decision time
     * @param fightLocked whether the fight hysteresis lock was held at decision time
     */
    void onSimEvaluated(Squad squad, CombatSimulator.CombatResult result, boolean retreatLocked, boolean fightLocked);

    /**
     * A lock kept the squad on its current status after the simulator asked for the other one.
     *
     * @param squad squad whose transition was suppressed
     * @param lock lock that suppressed it
     */
    void onLockSuppressed(Squad squad, SquadLock lock);

    /**
     * A split that commitment rules would have fragmented below the move out floor.
     *
     * <p>Fires on every declined split, committed squad or not: the gate applies unconditionally, and
     * the {@code committed} column on the resulting row separates the two populations.
     *
     * @param squad squad whose split was declined
     * @param moveOutThreshold strength each side needs to stay cleared to move out
     * @param squadStrength strength of the squad before the split
     * @param outlierStrength strength of the units that would have left for the sibling
     */
    void onSplitSuppressed(Squad squad, int moveOutThreshold, int squadStrength, int outlierStrength);

    /**
     * The containment verdict for a squad that was eligible to enter an arc this frame.
     *
     * <p>canBreakContainment is only meaningful when shouldContain holds: the production expression
     * short circuits, so the evaluator is not consulted otherwise.
     *
     * @param squad squad the verdict was computed for
     * @param shouldContain ContainmentEvaluator.shouldContain
     * @param canBreakContainment ContainmentEvaluator.canBreakContainment, false when unevaluated
     * @param entered whether the squad actually took an arc and moved to CONTAIN
     */
    void onContainmentEvaluated(Squad squad, boolean shouldContain, boolean canBreakContainment, boolean entered);
}
