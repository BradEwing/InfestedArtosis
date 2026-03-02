package macro;

import bwapi.Game;
import bwapi.Position;
import bwapi.Race;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.GameState;
import info.tracking.ObservedUnitTracker;
import info.tracking.StrategyTracker;
import util.Time;
import info.UnitTypeCount;
import macro.plan.Plan;
import macro.plan.PlanType;
import macro.plan.UnitPlan;
import macro.plan.UpgradePlan;

import bwapi.Unit;
import info.BaseData;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Reactions updates the ProductionQueue and GameState when particular enemy strategies are detected.
 * This may involve removing plans from the queue, or updating priority.
 */
public class Reactions {

    private static final Predicate<Plan> IS_SPAWNING_POOL = p ->
            p.getType() == PlanType.BUILDING && p.getPlannedUnit() == UnitType.Zerg_Spawning_Pool;

    private static final Predicate<Plan> IS_HATCHERY = p ->
            p.getType() == PlanType.BUILDING && p.getPlannedUnit() == UnitType.Zerg_Hatchery;

    private static final Predicate<Plan> IS_EXTRACTOR = p ->
            p.getType() == PlanType.BUILDING && p.getPlannedUnit() == UnitType.Zerg_Extractor;

    private static final Predicate<Plan> IS_DRONE = p ->
            p.getType() == PlanType.UNIT && p.getPlannedUnit() == UnitType.Zerg_Drone;

    private static final int FFE_DRONE_BOOST = 8;

    private GameState gameState;
    private boolean ffeReactionApplied = false;

    public Reactions(GameState gameState) {
        this.gameState = gameState;
    }

    public void onFrame() {
        cannonRushReaction();
        scvRushReaction();
        twoGateReaction();
        zvzSunkenReaction();
        ffeReaction();
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

        ProductionQueue productionQueue = gameState.getProductionQueue();

        productionQueue.setPriorityWhere(IS_SPAWNING_POOL, 0);
        productionQueue.removeWhere(IS_HATCHERY, gameState::setImpossiblePlan);

        BaseData baseData = gameState.getBaseData();

        productionQueue.removeWhere(IS_EXTRACTOR, plan -> {
            gameState.setImpossiblePlan(plan);
            if (plan.getBuildPosition() != null) {
                baseData.unreserveExtractor(plan.getBuildPosition());
            }
        });

        Set<Plan> scheduledExtractors = gameState.getPlansScheduled()
                .stream()
                .filter(IS_EXTRACTOR)
                .collect(Collectors.toSet());
        for (Plan plan : scheduledExtractors) {
            gameState.getPlansScheduled().remove(plan);
            cancelExtractorPlan(plan, baseData);
        }

        Set<Plan> buildingExtractors = gameState.getPlansBuilding()
                .stream()
                .filter(IS_EXTRACTOR)
                .collect(Collectors.toSet());
        for (Plan plan : buildingExtractors) {
            gameState.getPlansBuilding().remove(plan);
            cancelExtractorPlan(plan, baseData);
        }

        Set<Plan> morphingExtractors = gameState.getPlansMorphing()
                .stream()
                .filter(IS_EXTRACTOR)
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
            productionQueue.removeWhere(IS_DRONE, gameState::setImpossiblePlan);
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

        ProductionQueue productionQueue = gameState.getProductionQueue();

        productionQueue.setPriorityWhere(IS_SPAWNING_POOL, 0);

        int droneCount = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int zerglingCount = gameState.getUnitTypeCount().get(UnitType.Zerg_Zergling);

        if (droneCount >= 8 && zerglingCount < 8) {
            productionQueue.removeWhere(IS_DRONE, gameState::setImpossiblePlan);
        }
    }

    private void twoGateReaction() {
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        if (!strategyTracker.isDetectedStrategy("2Gate")) {
            return;
        }

        ProductionQueue productionQueue = gameState.getProductionQueue();

        if (gameState.canPlanUpgrade(UpgradeType.Metabolic_Boost)) {
            gameState.getTechProgression().setPlannedMetabolicBoost(true);
            UpgradePlan upgradePlan = new UpgradePlan(UpgradeType.Metabolic_Boost, gameState.getGameTime().getFrames());
            productionQueue.add(upgradePlan);
        }

        int zerglingCount = gameState.getUnitTypeCount().get(UnitType.Zerg_Zergling);
        if (zerglingCount > 6) {
            productionQueue.setPriorityWhere(
                    p -> p.getType() == PlanType.UPGRADE
                            && ((UpgradePlan) p).getPlannedUpgrade() == UpgradeType.Metabolic_Boost,
                    0);
        }

        BaseData baseData = gameState.getBaseData();
        if (baseData.getMyBases().size() == 1) {
            baseData.setAllowSunkenAtMain(true);
        }
    }

    private void zvzSunkenReaction() {
        if (gameState.getOpponentRace() != Race.Zerg) {
            return;
        }

        BaseData baseData = gameState.getBaseData();
        int ourBaseCount = baseData.currentBaseCount();
        int enemyDepots = gameState.enemyResourceDepotCount();
        int ourZerglings = gameState.ourLivingUnitCount(UnitType.Zerg_Zergling);
        int enemyZerglings = gameState.enemyUnitCount(UnitType.Zerg_Zergling);

        boolean enemyUpAHatchery = enemyDepots > ourBaseCount;
        boolean enemyUpZerglings = enemyZerglings - ourZerglings >= 3;

        if ((enemyUpAHatchery || enemyUpZerglings) && ourBaseCount == 1) {
            baseData.setAllowSunkenAtMain(true);
        }
    }

    private void ffeReaction() {
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        if (!strategyTracker.isDetectedStrategy("FFE")) {
            return;
        }

        if (!ffeReactionApplied) {
            ffeReactionApplied = true;
            ProductionQueue productionQueue = gameState.getProductionQueue();
            UnitTypeCount unitTypeCount = gameState.getUnitTypeCount();
            int priority = gameState.getGameTime().getFrames();
            for (int i = 0; i < FFE_DRONE_BOOST; i++) {
                unitTypeCount.planUnit(UnitType.Zerg_Drone);
                gameState.addPlannedWorker(1);
                productionQueue.add(new UnitPlan(UnitType.Zerg_Drone, priority));
            }
        }

        Time time = gameState.getGameTime();
        if (time.greaterThan(new Time(7, 0))) {
            return;
        }

        ProductionQueue productionQueue = gameState.getProductionQueue();
        int minPriority = productionQueue.minPriority();

        productionQueue.setPriorityWhere(IS_DRONE.or(IS_HATCHERY), minPriority);
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
