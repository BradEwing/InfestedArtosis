package telemetry;

import unit.squad.CombatSimulator;
import unit.squad.Squad;

/**
 * Static dispatch point for squad status decision events. With no sink registered, every method
 * is a no-op.
 */
public final class SquadDecisions {

    private static SquadDecisionSink sink;

    private SquadDecisions() {
    }

    public static void register(SquadDecisionSink squadDecisionSink) {
        sink = squadDecisionSink;
    }

    public static void clear() {
        sink = null;
    }

    public static void simEvaluated(Squad squad, CombatSimulator.CombatResult result, boolean retreatLocked,
                                    boolean fightLocked) {
        SquadDecisionSink current = sink;
        if (current == null) {
            return;
        }
        current.onSimEvaluated(squad, result, retreatLocked, fightLocked);
    }

    public static void lockSuppressed(Squad squad, SquadLock lock) {
        SquadDecisionSink current = sink;
        if (current == null) {
            return;
        }
        current.onLockSuppressed(squad, lock);
    }

    public static void containmentEvaluated(Squad squad, boolean shouldContain, boolean canBreakContainment,
                                            boolean entered) {
        SquadDecisionSink current = sink;
        if (current == null) {
            return;
        }
        current.onContainmentEvaluated(squad, shouldContain, canBreakContainment, entered);
    }

    public static void splitSuppressed(Squad squad, int moveOutThreshold, int squadStrength, int outlierStrength) {
        SquadDecisionSink current = sink;
        if (current == null) {
            return;
        }
        current.onSplitSuppressed(squad, moveOutThreshold, squadStrength, outlierStrength);
    }
}
