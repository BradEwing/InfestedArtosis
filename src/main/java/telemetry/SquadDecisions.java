package telemetry;

import bwapi.Position;
import bwapi.UnitType;
import unit.managed.ManagedUnit;
import unit.squad.CombatSimulator;
import unit.squad.ContainmentCollapse;
import unit.squad.DefenseSim;
import unit.squad.RunbyState;
import unit.squad.Squad;

import java.util.List;

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

    public static void pathTaken(Squad squad, DecisionPath path) {
        SquadDecisionSink current = sink;
        if (current == null) {
            return;
        }
        current.onPathTaken(squad, path);
    }

    public static void containmentEvaluated(Squad squad, boolean shouldContain, boolean canBreakContainment,
                                            boolean entered) {
        SquadDecisionSink current = sink;
        if (current == null) {
            return;
        }
        current.onContainmentEvaluated(squad, shouldContain, canBreakContainment, entered);
    }

    public static void containmentPushedBack(Squad squad, Position from, Position to, UnitType enemyType,
                                             int membersMoved) {
        SquadDecisionSink current = sink;
        if (current == null) {
            return;
        }
        current.onContainmentPushedBack(squad, from, to, enemyType, membersMoved);
    }

    public static void outrangedHit(Squad squad, boolean outrangedHit) {
        SquadDecisionSink current = sink;
        if (current == null) {
            return;
        }
        current.onOutrangedHitEvaluated(squad, outrangedHit);
    }

    public static void containmentCollapseEvaluated(Squad squad, ContainmentCollapse.Outcome outcome,
                                                    int enemiesInSector, double ratio, int flanks,
                                                    boolean staticClear) {
        SquadDecisionSink current = sink;
        if (current == null) {
            return;
        }
        current.onContainmentCollapseEvaluated(squad, outcome, enemiesInSector, ratio, flanks, staticClear);
    }

    public static void containArcMeasured(Squad squad, int distance) {
        SquadDecisionSink current = sink;
        if (current == null) {
            return;
        }
        current.onContainArcMeasured(squad, distance);
    }

    public static void moveOutEvaluated(Squad squad, int moveOutThreshold, int squadStrength) {
        SquadDecisionSink current = sink;
        if (current == null) {
            return;
        }
        current.onMoveOutEvaluated(squad, moveOutThreshold, squadStrength);
    }

    public static void containmentEnded(Squad squad, int supplyLost) {
        SquadDecisionSink current = sink;
        if (current == null) {
            return;
        }
        current.onContainmentEnded(squad, supplyLost);
    }

    public static void rallied(Squad squad, RallyReason reason) {
        SquadDecisionSink current = sink;
        if (current == null) {
            return;
        }
        current.onRallied(squad, reason);
    }

    public static void rallyReleased(Squad squad, RallyRelease release) {
        SquadDecisionSink current = sink;
        if (current == null) {
            return;
        }
        current.onRallyReleased(squad, release);
    }

    public static void splitSuppressed(Squad squad, int moveOutThreshold, int squadStrength, int outlierStrength) {
        SquadDecisionSink current = sink;
        if (current == null) {
            return;
        }
        current.onSplitSuppressed(squad, moveOutThreshold, squadStrength, outlierStrength);
    }

    public static void runbyPhaseStarted(Squad squad, RunbyState.Phase from, RunbyState.Phase to, DecisionPath path) {
        SquadDecisionSink current = sink;
        if (current == null) {
            return;
        }
        current.onRunbyPhaseStarted(squad, from, to, path);
    }

    public static void defenseEvaluated(Squad squad, DefenseEvent event, int candidates, List<ManagedUnit> pulled,
                                        List<ManagedUnit> released, DefenseSim sim) {
        SquadDecisionSink current = sink;
        if (current == null) {
            return;
        }
        current.onDefenseEvaluated(squad, event, candidates, pulled, released, sim);
    }
}
