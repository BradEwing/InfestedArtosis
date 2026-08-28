package macro;

import bwapi.Game;
import bwapi.Position;
import bwapi.Race;
import bwapi.TilePosition;
import bwapi.UnitType;
import bwapi.UpgradeType;
import bwem.Base;
import info.GameState;
import info.map.BuildingPlanner;
import info.tracking.ObservedUnitTracker;
import info.tracking.StrategyTracker;
import util.Time;
import macro.plan.Plan;
import macro.plan.PlanType;
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

    private static final Predicate<Plan> IS_CREEP_COLONY = p ->
            p.getType() == PlanType.BUILDING && p.getPlannedUnit() == UnitType.Zerg_Creep_Colony;

    private static final Predicate<Plan> IS_LAIR = p ->
            p.getType() == PlanType.BUILDING && p.getPlannedUnit() == UnitType.Zerg_Lair;

    private static final Predicate<Plan> IS_EXPANSION_HATCHERY = IS_HATCHERY.and(p -> !p.isMacroHatchery());

    private static final Predicate<Plan> IS_SPEED_UPGRADE = p ->
            p.getType() == PlanType.UPGRADE && ((UpgradePlan) p).getPlannedUpgrade() == UpgradeType.Metabolic_Boost;

    /**
     * Sits behind emergency defense so an unaffordable upgrade can never tie with, and so deny a
     * schedule slot to, the emergency creep colony, while still jumping ahead of tech and normal
     * production. Priority 0 stays reserved for emergency reactions.
     */
    private static final int SPEED_UPGRADE_PRIORITY = 2;

    private static final Time EARLY_RUSH_WINDOW = new Time(5, 0);
    private static final Time EARLY_RUSH_HARD_DEADLINE = new Time(8, 0);
    private static final int EARLY_RUSH_SAFE_ZERGLINGS = 12;

    private GameState gameState;

    public Reactions(GameState gameState) {
        this.gameState = gameState;
    }

    public void onFrame() {
        cannonRushReaction();
        scvRushReaction();
        earlyRushReaction();
        twoGateReaction();
        zvzSunkenReaction();
        ffeReaction();
        clearMainSunkenOnExpansion();
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
        cancelAllExtractors(baseData);

        if (gameState.getTechProgression().isSpawningPool()) {
            productionQueue.removeWhere(IS_DRONE, gameState::setImpossiblePlan);
        }

        allowSunkenAtMainIfSingleBase(baseData);
    }

    private void earlyRushReaction() {
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        if (!strategyTracker.isDetectedStrategy("EarlyRush")) {
            return;
        }

        if (gameState.getGameTime().greaterThan(EARLY_RUSH_HARD_DEADLINE)) {
            gameState.setEarlyRushed(false);
            gameState.setEarlyRushDenyGas(false);
            gameState.setEarlyRushDelayLair(false);
            gameState.setEarlyRushMacroHatch(false);
            return;
        }

        int attackersAtBase = gameState.visibleEnemyMobileGroundCombatUnitsAtOurBases();
        int zerglingCount = gameState.ourUnitCount(UnitType.Zerg_Zergling);
        boolean withinRushWindow = gameState.getGameTime().lessThanOrEqual(EARLY_RUSH_WINDOW);
        boolean preparing = withinRushWindow && zerglingCount < EARLY_RUSH_SAFE_ZERGLINGS;
        if (attackersAtBase == 0 && !preparing) {
            gameState.setEarlyRushed(false);
            gameState.setEarlyRushDenyGas(false);
            gameState.setEarlyRushDelayLair(false);
            gameState.setEarlyRushMacroHatch(false);
            return;
        }

        gameState.setEarlyRushed(true);

        gameState.setEarlyRushDenyGas(!preserveGasForSpeed());
        gameState.setEarlyRushDelayLair(gameState.getOpponentRace() == Race.Protoss);
        gameState.setEarlyRushMacroHatch(gameState.getOpponentRace() == Race.Protoss);

        ProductionQueue productionQueue = gameState.getProductionQueue();
        productionQueue.setPriorityWhere(IS_SPAWNING_POOL, 0);
        productionQueue.removeWhere(IS_EXPANSION_HATCHERY, gameState::setImpossiblePlan);

        if (gameState.isEarlyRushDelayLair()) {
            cancelQueuedLairs();
        }

        BaseData baseData = gameState.getBaseData();
        if (gameState.isEarlyRushDenyGas()) {
            cancelAllExtractors(baseData);
        } else {
            planSpeedUpgrade(productionQueue);
        }

        int droneCount = gameState.ourUnitCount(UnitType.Zerg_Drone);
        if (droneCount >= 8 && zerglingCount < 8) {
            productionQueue.removeWhere(IS_DRONE, gameState::setImpossiblePlan);
        }

        allowSunkenAtMainIfSingleBase(baseData);
    }

    /**
     * Drops Lair plans still waiting in the production queue. Cancelling rather than demoting is
     * required: plan priority controls only the order the queue is drained, not whether a plan is
     * eligible, so a demoted Lair is still built as soon as it is affordable. Scheduled Lairs are
     * cancelled by ProductionManager, which owns the scheduledBuildings slot they hold.
     */
    private void cancelQueuedLairs() {
        gameState.getProductionQueue().removeWhere(IS_LAIR, gameState::setImpossiblePlan);
    }

    /**
     * Relaxes the main-base sunken restriction only while the main is genuinely our sole base.
     * Reserved and morphing expansions count against that: a base joins myBases when its hatchery
     * completes, so a count of completed bases alone treats a natural that is queued or still
     * morphing as if it did not exist, and sites the sunken at the main. The clear path stays on
     * completed bases so a committed but not yet online expansion cannot tear down defense that
     * has already been planned at the main.
     */
    private void allowSunkenAtMainIfSingleBase(BaseData baseData) {
        if (baseData.currentAndReservedCount() == 1) {
            baseData.setAllowSunkenAtMain(true);
        }
    }

    /**
     * Decides whether the early-rush gas cut spares Metabolic Boost. Yes against Protoss: the rush is zealots,
     * earlyRushDelayLair already holds the Lair so the gas buys nothing else, and emergency defense outranks
     * SPEED_UPGRADE_PRIORITY. No against Terran and Zerg, where the minerals belong in zerglings instead.
     *
     * <p>A detected 2Gate qualifies on its own. Requiring committed gas made the test read its own result: denying gas
     * blocks canPlanExtractor(), the only path by which an Extractor is ever planned.
     */
    private boolean preserveGasForSpeed() {
        if (gameState.getOpponentRace() != Race.Protoss || gameState.getTechProgression().isMetabolicBoost()) {
            return false;
        }

        return gameState.getBaseData().numExtractor() > 0 || gameState.getStrategyTracker().isDetectedStrategy("2Gate");
    }

    /**
     * Queues Metabolic Boost and pulls it ahead of normal production. Shared by the 2Gate and the
     * early-rush paths so gas preserved for speed is actually spent on it.
     *
     * <p>Guarded on a standing Extractor, the same count canPlanUpgrade reads, so this cannot reprioritise a
     * Metabolic Boost it did not queue after gas was denied earlier in the same frame.
     */
    private void planSpeedUpgrade(ProductionQueue productionQueue) {
        if (gameState.ourUnitCount(UnitType.Zerg_Extractor) < 1) {
            return;
        }

        if (gameState.canPlanUpgrade(UpgradeType.Metabolic_Boost)) {
            gameState.getTechProgression().setPlannedMetabolicBoost(true);
            UpgradePlan upgradePlan = new UpgradePlan(UpgradeType.Metabolic_Boost, gameState.getGameTime().getFrames());
            productionQueue.add(upgradePlan);
        }

        productionQueue.setPriorityWhere(IS_SPEED_UPGRADE, SPEED_UPGRADE_PRIORITY);
    }

    private void cancelAllExtractors(BaseData baseData) {
        ProductionQueue productionQueue = gameState.getProductionQueue();
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

        planSpeedUpgrade(productionQueue);

        BaseData baseData = gameState.getBaseData();
        allowSunkenAtMainIfSingleBase(baseData);
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

        if (enemyUpAHatchery || enemyUpZerglings) {
            allowSunkenAtMainIfSingleBase(baseData);
        }
    }

    private void ffeReaction() {
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        if (!strategyTracker.isDetectedStrategy("FFE")) {
            return;
        }

        Time time = gameState.getGameTime();
        if (time.greaterThan(new Time(7, 0))) {
            return;
        }

        ProductionQueue productionQueue = gameState.getProductionQueue();
        int minPriority = productionQueue.minPriority();

        productionQueue.setPriorityWhere(IS_DRONE.or(IS_HATCHERY), minPriority);
    }

    private void clearMainSunkenOnExpansion() {
        BaseData baseData = gameState.getBaseData();
        if (!baseData.isAllowSunkenAtMain()) {
            return;
        }
        if (baseData.currentBaseCount() < 2) {
            return;
        }

        baseData.setAllowSunkenAtMain(false);

        Base mainBase = baseData.getMainBase();
        BuildingPlanner buildingPlanner = gameState.getBuildingPlanner();
        Set<TilePosition> mainCreepTiles = buildingPlanner.findSurroundingCreepTiles(mainBase, false, true);

        Predicate<Plan> isAtMain = p -> {
            TilePosition pos = p.getBuildPosition();
            return pos != null && mainCreepTiles.contains(pos);
        };

        Predicate<Plan> isMainCreepColony = IS_CREEP_COLONY.and(isAtMain);

        ProductionQueue productionQueue = gameState.getProductionQueue();
        productionQueue.removeWhere(isMainCreepColony, plan -> {
            gameState.setImpossiblePlan(plan);
            buildingPlanner.unreservePlannedBuildingTiles(plan.getBuildPosition(), UnitType.Zerg_Creep_Colony);
        });
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
