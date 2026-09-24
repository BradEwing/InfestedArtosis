package info;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.UnitType;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.PlanState;
import telemetry.PlanEventSink;

import java.util.List;

/**
 * Records the enemy main plan events as one line each, in the order they arrive.
 */
final class EnemyMainEventRecorder implements PlanEventSink {

    private final List<String> events;

    EnemyMainEventRecorder(List<String> events) {
        this.events = events;
    }

    @Override
    public void onEnqueue(Plan plan) {
    }

    @Override
    public void onStateChange(Plan plan, PlanState from, PlanState to) {
    }

    @Override
    public void onBlocked(Plan plan, PlanBlocker blocker) {
    }

    @Override
    public void onEnemyMainAssigned(TilePosition main, EnemyMainEvidence evidence, UnitType source,
                                    Position sourcePosition) {
        events.add("ENEMY_MAIN_ASSIGNED " + main + " " + evidence + " " + source + " " + sourcePosition);
    }

    @Override
    public void onEnemyMainCleared(TilePosition main, EnemyMainClearReason reason) {
        events.add("ENEMY_MAIN_CLEARED " + main + " " + reason);
    }

    @Override
    public void onEnemyMainScouted(TilePosition main) {
        events.add("ENEMY_MAIN_SCOUTED " + main);
    }
}
