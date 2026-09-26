package telemetry;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.UnitType;
import info.BuilderThreat;
import info.EnemyMainClearReason;
import info.EnemyMainEvidence;
import macro.plan.BuilderDispatchDecision;
import macro.plan.BuilderLossReason;
import macro.plan.BuilderReading;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.PlanCancelSource;
import macro.plan.PlanState;
import strategy.buildorder.GasBoundHiveTech;
import strategy.buildorder.LarvaBoundMacroHatchery;

import java.util.List;

/**
 * Static dispatch point for plan lifecycle events.
 *
 * <p>Plans are created and mutated from managers that have no telemetry dependency, so the hooks
 * reach the logger through this holder rather than through a constructor argument. With no sink
 * registered every entry point is a single null check: no allocation, no BWAPI call, no file
 * handle.
 */
public final class PlanEvents {

    private static PlanEventSink sink;

    private PlanEvents() {
    }

    public static void register(PlanEventSink planEventSink) {
        sink = planEventSink;
    }

    public static void clear() {
        sink = null;
    }

    public static void enqueued(Plan plan) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onEnqueue(plan);
    }

    public static void enqueued(List<Plan> plans) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onEnqueueAll(plans);
    }

    public static void stateChanged(Plan plan, PlanState from, PlanState to) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onStateChange(plan, from, to);
    }

    public static void blocked(Plan plan, PlanBlocker blocker) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onBlocked(plan, blocker);
    }

    public static void stale(Plan plan) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onStale(plan);
    }

    public static void promoted(Plan plan) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onPromote(plan);
    }

    public static void buildAheadHold(Plan holder, int heldFrames, int starvedBehind) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onBuildAheadHold(holder, heldFrames, starvedBehind);
    }

    public static void buildAheadEvicted(Plan holder, int heldFrames, int starvedBehind) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onBuildAheadEvict(holder, heldFrames, starvedBehind);
    }

    public static void buildAheadYielded(Plan holder, int heldFrames, Plan emergency) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onBuildAheadYield(holder, heldFrames, emergency);
    }

    public static void withheld(UnitType unitType, PlanBlocker blocker) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onWithheld(unitType, blocker);
    }

    public static void macroHatcheryGate(LarvaBoundMacroHatchery.Gate gate, boolean techReady, int hatcheries,
                                         int outstandingMacroHatcheries) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onMacroHatcheryGate(gate, techReady, hatcheries, outstandingMacroHatcheries);
    }

    public static void hiveTechGate(GasBoundHiveTech.Gate gate, UnitType structure, int availableGas,
                                    int requiredGas, int extractorsCompleted) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onHiveTechGate(gate, structure, availableGas, requiredGas, extractorsCompleted);
    }

    public static void unplannedCancel(UnitType unitType, PlanCancelSource cancelSource) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onUnplannedCancel(unitType, cancelSource);
    }

    public static void blockerDiverted(Plan plan, Position mineral) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onBlockerDivert(plan, mineral);
    }

    public static void builderDispatchDecision(Plan plan, BuilderDispatchDecision decision, BuilderThreat threat) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onBuilderDispatchDecision(plan, decision, threat);
    }

    public static void builderLost(Plan plan, BuilderLossReason reason, BuilderReading builder) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onBuilderLost(plan, reason, builder);
    }

    public static void builderRedispatched(Plan plan, BuilderLossReason reason, BuilderReading lost,
                                           BuilderReading taker) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onBuilderRedispatch(plan, reason, lost, taker);
    }

    public static void expansionBackoff(int lostExpansionBuilders, int expansionHeldUntilFrame) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onExpansionBackoff(lostExpansionBuilders, expansionHeldUntilFrame);
    }

    public static void colonyBuilderBackoff(TilePosition base, int lostColonyBuilders, int colonyHeldUntilFrame) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onColonyBuilderBackoff(base, lostColonyBuilders, colonyHeldUntilFrame);
    }

    public static void strategyDetected(String detectionLabel) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onStrategyDetected(detectionLabel);
    }


    public static void baseLost(TilePosition base, boolean innerBase) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onBaseLost(base, innerBase);
    }

    public static void geyserDepleted(TilePosition geyser, TilePosition base, int initialResources,
                                      int extractorCompletedFrame) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onGeyserDepleted(geyser, base, initialResources, extractorCompletedFrame);
    }

    public static void rallyPointChanged(TilePosition base, String reason) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onRallyPointChanged(base, reason);
    }

    public static void enemyMainAssigned(TilePosition main, EnemyMainEvidence evidence, UnitType source,
                                         Position sourcePosition) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onEnemyMainAssigned(main, evidence, source, sourcePosition);
    }

    public static void enemyMainCleared(TilePosition main, EnemyMainClearReason reason) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onEnemyMainCleared(main, reason);
    }

    public static void enemyMainScouted(TilePosition main) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onEnemyMainScouted(main);
    }
}
