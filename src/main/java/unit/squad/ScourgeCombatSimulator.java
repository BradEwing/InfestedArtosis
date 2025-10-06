package unit.squad;

import bwapi.Unit;
import bwapi.UnitType;
import info.GameState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Combat simulator implementation for Scourge squads.
 * Evaluates whether scourge should kamikaze attack based on kill potential.
 * Scourge will engage if they can kill at least 1 enemy air unit.
 */
public class ScourgeCombatSimulator implements CombatSimulator {

    @Override
    public CombatResult evaluate(Squad squad, GameState gameState) {
        Set<Unit> enemyUnits = gameState.getDetectedEnemyUnits();
        Set<Unit> enemyBuildings = gameState.getEnemyBuildings();

        // Get all enemy air units within reasonable range
        List<Unit> airTargets = new ArrayList<>();
        for (Unit enemy : enemyUnits) {
            if (enemy.isFlying() && enemy.isDetected() && !enemy.isInvincible()) {
                airTargets.add(enemy);
            }
        }
        for (Unit building : enemyBuildings) {
            if (building.isFlying() && building.isDetected() && !building.isInvincible()) {
                airTargets.add(building);
            }
        }

        if (airTargets.isEmpty()) {
            return CombatResult.RETREAT;
        }

        return canKillTarget(squad, airTargets) ? CombatResult.ENGAGE : CombatResult.RETREAT;
    }

    /**
     * Evaluates if the scourge squad can kill at least one enemy air unit.
     */
    private boolean canKillTarget(Squad squad, List<Unit> airTargets) {
        int squadSize = squad.size();
        if (squadSize == 0) {
            return false;
        }

        int scourgeDAMAGE = 110;

        List<Unit> prioritizedTargets = prioritizeTargets(airTargets);

        int remainingScourge = squadSize;

        for (Unit target : prioritizedTargets) {
            if (remainingScourge <= 0) {
                break;
            }

            int targetHP = target.getHitPoints() + target.getShields();
            int scourgeNeeded = (int) Math.ceil((double) targetHP / scourgeDAMAGE);

            if (scourgeNeeded <= remainingScourge) {
                return true;
            }

            if (isHighPriorityTarget(target)) {
                remainingScourge = Math.max(0, remainingScourge - scourgeNeeded);
            }
        }

        return false;
    }

    private List<Unit> prioritizeTargets(List<Unit> airTargets) {
        List<Unit> highPriority = new ArrayList<>();
        List<Unit> mediumPriority = new ArrayList<>();
        List<Unit> lowPriority = new ArrayList<>();

        for (Unit target : airTargets) {
            UnitType type = target.getType();

            if (isLowPriorityTarget(type)) {
                lowPriority.add(target);
            } else if (isFloatingBuilding(type)) {
                mediumPriority.add(target);
            } else {
                highPriority.add(target);
            }
        }

        // Combine lists in priority order
        List<Unit> prioritized = new ArrayList<>();
        prioritized.addAll(highPriority);
        prioritized.addAll(mediumPriority);
        prioritized.addAll(lowPriority);

        return prioritized;
    }

    private boolean isHighPriorityTarget(Unit target) {
        UnitType type = target.getType();
        return !isFloatingBuilding(type) && !isLowPriorityTarget(type);
    }

    private boolean isFloatingBuilding(UnitType type) {
        return type.isFlyingBuilding();
    }

    private boolean isLowPriorityTarget(UnitType type) {
        return type == UnitType.Zerg_Overlord ||
                type == UnitType.Protoss_Interceptor;
    }
}