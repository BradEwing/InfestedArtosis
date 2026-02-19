package macro;

import bwapi.Game;
import bwapi.Position;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.GameState;
import info.tracking.ObservedUnitTracker;
import info.tracking.StrategyTracker;
import macro.plan.Plan;
import macro.plan.PlanType;
import macro.plan.UpgradePlan;

import bwapi.Unit;
import info.BaseData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;

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
        twoGateReaction();
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

        BaseData baseData = gameState.getBaseData();

        List<Plan> extractorPlansToRemove = new ArrayList<>();
        for (Plan plan : productionQueue) {
            if (plan.getType() == PlanType.BUILDING
                    && plan.getPlannedUnit() == UnitType.Zerg_Extractor) {
                extractorPlansToRemove.add(plan);
            }
        }
        for (Plan plan : extractorPlansToRemove) {
            productionQueue.remove(plan);
            gameState.setImpossiblePlan(plan);
            if (plan.getBuildPosition() != null) {
                baseData.unreserveExtractor(plan.getBuildPosition());
            }
        }

        Set<Plan> scheduledExtractors = gameState.getPlansScheduled()
                .stream()
                .filter(p -> p.getType() == PlanType.BUILDING && p.getPlannedUnit() == UnitType.Zerg_Extractor)
                .collect(Collectors.toSet());
        for (Plan plan : scheduledExtractors) {
            gameState.getPlansScheduled().remove(plan);
            cancelExtractorPlan(plan, baseData);
        }

        Set<Plan> buildingExtractors = gameState.getPlansBuilding()
                .stream()
                .filter(p -> p.getType() == PlanType.BUILDING && p.getPlannedUnit() == UnitType.Zerg_Extractor)
                .collect(Collectors.toSet());
        for (Plan plan : buildingExtractors) {
            gameState.getPlansBuilding().remove(plan);
            cancelExtractorPlan(plan, baseData);
        }

        Set<Plan> morphingExtractors = gameState.getPlansMorphing()
                .stream()
                .filter(p -> p.getType() == PlanType.BUILDING && p.getPlannedUnit() == UnitType.Zerg_Extractor)
                .collect(Collectors.toSet());
        for (Plan plan : morphingExtractors) {
            gameState.getPlansMorphing().remove(plan);
            cancelExtractorPlan(plan, baseData);
        }

        Game game = gameState.getGame();
        for (Unit unit : game.self().getUnits()) {
            if (unit.getType() == UnitType.Zerg_Extractor && !unit.isCompleted()) {
                unit.cancelMorph();
            }
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

        if (baseData.getMyBases().size() == 1) {
            baseData.setAllowSunkenAtMain(true);
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

    private void twoGateReaction() {
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        if (!strategyTracker.isDetectedStrategy("2Gate")) {
            return;
        }

        PriorityQueue<Plan> productionQueue = gameState.getProductionQueue();

        if (gameState.canPlanUpgrade(UpgradeType.Metabolic_Boost)) {
            gameState.getTechProgression().setPlannedMetabolicBoost(true);
            UpgradePlan upgradePlan = new UpgradePlan(UpgradeType.Metabolic_Boost, gameState.getGameTime().getFrames());
            productionQueue.add(upgradePlan);
        }

        int zerglingCount = gameState.getUnitTypeCount().get(UnitType.Zerg_Zergling);
        if (zerglingCount > 6) {
            for (Plan plan : productionQueue) {
                if (plan.getType() == PlanType.UPGRADE && ((UpgradePlan) plan).getPlannedUpgrade() == UpgradeType.Metabolic_Boost) {
                    plan.setPriority(0);
                }
            }
        }
    }

    private void cancelExtractorPlan(Plan plan, BaseData baseData) {
        Unit assignedDrone = null;
        for (Map.Entry<Unit, Plan> entry : gameState.getAssignedPlannedItems().entrySet()) {
            if (entry.getValue() == plan) {
                assignedDrone = entry.getKey();
                break;
            }
        }
        gameState.cancelPlan(assignedDrone, plan);
        if (plan.getBuildPosition() != null) {
            baseData.unreserveExtractor(plan.getBuildPosition());
        }
    }
}
