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
