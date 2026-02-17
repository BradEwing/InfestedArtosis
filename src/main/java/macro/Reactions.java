package macro;

import bwapi.Position;
import bwapi.UnitType;
import info.GameState;
import info.tracking.ObservedUnitTracker;
import info.tracking.StrategyTracker;
import macro.plan.Plan;
import macro.plan.PlanType;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Reactions updates the ProductionQueue when particular enemy strategies are detected.
 * This may involve removing plans from the queue, or updating priority.
 */
public class Reactions {

    private GameState gameState;

    public Reactions(GameState gameState) {
        this.gameState = gameState;
    }

    public void onFrame() {
        cannonRushReaction();
        scvRushReaction();
    }

    private void scvRushReaction() {
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        if (!strategyTracker.isDetectedStrategy("SCVRush")) {
            return;
        }

        int zerglingCount = gameState.getUnitTypeCount().get(UnitType.Zerg_Zergling);
        if (zerglingCount >= 12) {
            gameState.setScvRushed(false);
            return;
        }

        gameState.setScvRushed(true);

        PriorityQueue<Plan> productionQueue = gameState.getProductionQueue();

        for (Plan plan : productionQueue) {
            if (plan.getType() == PlanType.BUILDING
                    && plan.getPlannedUnit() == UnitType.Zerg_Spawning_Pool) {
                plan.setPriority(0);
            }
        }

        List<Plan> hatchPlansToRemove = new ArrayList<>();
        for (Plan plan : productionQueue) {
            if (plan.getType() == PlanType.BUILDING
                    && plan.getPlannedUnit() == UnitType.Zerg_Hatchery) {
                hatchPlansToRemove.add(plan);
            }
        }
        for (Plan plan : hatchPlansToRemove) {
            productionQueue.remove(plan);
            gameState.setImpossiblePlan(plan);
        }

        if (gameState.getTechProgression().isSpawningPool()) {
            List<Plan> dronePlansToRemove = new ArrayList<>();
            for (Plan plan : productionQueue) {
                if (plan.getType() == PlanType.UNIT
                        && plan.getPlannedUnit() == UnitType.Zerg_Drone) {
                    dronePlansToRemove.add(plan);
                }
            }
            for (Plan plan : dronePlansToRemove) {
                productionQueue.remove(plan);
                gameState.setImpossiblePlan(plan);
            }
        }

        if (gameState.getBaseData().getMyBases().size() == 1) {
            gameState.getBaseData().setAllowSunkenAtMain(true);
        }
    }

    private void cannonRushReaction() {
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        if (!strategyTracker.isDetectedStrategy("CannonRush")) {
            return;
        }

        Set<Position> basePositions = gameState.getBaseData().getMyBasePositions();

        ObservedUnitTracker tracker = gameState.getObservedUnitTracker();
        int completedCannons = tracker.getCompletedBuildingCountNearPositions(
                UnitType.Protoss_Photon_Cannon, basePositions, 512);
        int livingEnemyBuildings = tracker.getLivingBuildingCountNearPositions(basePositions, 512);

        if (livingEnemyBuildings == 0) {
            gameState.setCannonRushed(false);
            gameState.setCannonRushDefend(false);
            return;
        }

        gameState.setCannonRushed(true);
        gameState.setCannonRushDefend(completedCannons < 2);

        PriorityQueue<Plan> productionQueue = gameState.getProductionQueue();

        for (Plan plan : productionQueue) {
            if (plan.getType() == PlanType.BUILDING
                    && plan.getPlannedUnit() == UnitType.Zerg_Spawning_Pool) {
                plan.setPriority(0);
            }
        }

        int droneCount = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int zerglingCount = gameState.getUnitTypeCount().get(UnitType.Zerg_Zergling);

        if (droneCount >= 8 && zerglingCount < 8) {
            List<Plan> dronePlansToRemove = new ArrayList<>();
            for (Plan plan : productionQueue) {
                if (plan.getType() == PlanType.UNIT
                        && plan.getPlannedUnit() == UnitType.Zerg_Drone) {
                    dronePlansToRemove.add(plan);
                }
            }
            for (Plan plan : dronePlansToRemove) {
                productionQueue.remove(plan);
                gameState.setImpossiblePlan(plan);
            }
        }
    }
}
