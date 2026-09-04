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
import macro.plan.PlanCancelSource;
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
    static final int EARLY_RUSH_SAFE_ZERGLINGS = 12;
    static final int EARLY_RUSH_DRONE_FLOOR = 8;
    static final int EARLY_RUSH_CUT_ZERGLINGS = 8;

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
        productionQueue.removeWhere(IS_HATCHERY, PlanCancelSource.REACTION_SCV_RUSH_HATCHERY, gameState::setImpossiblePlan);

        BaseData baseData = gameState.getBaseData();
        cancelAllExtractors(baseData);

        if (gameState.getTechProgression().isSpawningPool()) {
            productionQueue.removeWhere(IS_DRONE, PlanCancelSource.REACTION_SCV_RUSH_DRONE, gameState::setImpossiblePlan);
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
        int zerglingCount = gameState.ourLivingUnitCount(UnitType.Zerg_Zergling);
        boolean withinRushWindow = gameState.getGameTime().lessThanOrEqual(EARLY_RUSH_WINDOW);
        boolean preparing = isPreparingForEarlyRush(withinRushWindow, zerglingCount);
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
        productionQueue.removeWhere(IS_EXPANSION_HATCHERY, PlanCancelSource.REACTION_EARLY_RUSH_EXPANSION,
                gameState::setImpossiblePlan);

        if (gameState.isEarlyRushDelayLair()) {
            cancelQueuedLairs();
        }

        BaseData baseData = gameState.getBaseData();
        if (gameState.isEarlyRushDenyGas()) {
            cancelAllExtractors(baseData);
        } else {
            planSpeedUpgrade(productionQueue);
        }

        int droneCount = gameState.ourLivingUnitCount(UnitType.Zerg_Drone);
        if (shouldCutDrones(droneCount, zerglingCount)) {
            productionQueue.removeWhere(IS_DRONE, PlanCancelSource.REACTION_EARLY_RUSH_DRONE, gameState::setImpossiblePlan);
        }

        allowSunkenAtMainIfSingleBase(baseData);
    }

    /**
     * Whether the bot is still short of the army an early rush demands, and so must stay in the
     * reaction even with nothing standing in its bases.
     * <p>
     * Counts living zerglings. A queued zergling plan raises the planned count by two, so the
     * forward looking count reports an army the bot cannot fight with yet, and a build order that
     * keeps zergling plans queued holds this predicate false for the whole rush window.
     *
     * @param withinRushWindow whether the game is still inside the early rush window
     * @param livingZerglings zerglings that have hatched
     * @return true while the reaction should stay armed
     */
    static boolean isPreparingForEarlyRush(boolean withinRushWindow, int livingZerglings) {
        return withinRushWindow && livingZerglings < EARLY_RUSH_SAFE_ZERGLINGS;
    }

    /**
     * Whether queued drone plans should be dropped so larva goes to zerglings instead.
     * <p>
     * Both counts are living units. The drone floor is only a floor when it counts drones that
     * exist, and the zergling side asks what the bot can defend with now rather than what its
     * queue will eventually hatch.
     *
     * @param livingDrones drones that have hatched
     * @param livingZerglings zerglings that have hatched
     * @return true when drone production should stop
     */
    static boolean shouldCutDrones(int livingDrones, int livingZerglings) {
        return livingDrones >= EARLY_RUSH_DRONE_FLOOR && livingZerglings < EARLY_RUSH_CUT_ZERGLINGS;
    }

    /**
     * Drops Lair plans still waiting in the production queue. Cancelling rather than demoting is
     * required: plan priority controls only the order the queue is drained, not whether a plan is
     * eligible, so a demoted Lair is still built as soon as it is affordable. Scheduled Lairs are
     * cancelled by ProductionManager, which owns the scheduledBuildings slot they hold.
     */
    private void cancelQueuedLairs() {
        gameState.getProductionQueue().removeWhere(IS_LAIR, PlanCancelSource.REACTION_EARLY_RUSH_LAIR, gameState::setImpossiblePlan);
    }

    /**
     * Relaxes the main-base sunken restriction only while the main is genuinely our sole base.
     */
    private void allowSunkenAtMainIfSingleBase(BaseData baseData) {
        if (baseData.currentAndReservedCount() == 1) {
            baseData.setAllowSunkenAtMain(true);
        }
    }

    private boolean preserveGasForSpeed() {
        if (gameState.getOpponentRace() != Race.Protoss || gameState.getTechProgression().isMetabolicBoost()) {
            return false;
        }

        return gameState.getBaseData().numExtractor() > 0 || gameState.getStrategyTracker().isDetectedStrategy("2Gate");
    }

    /**
     * Queues Metabolic Boost and pulls it ahead of normal production.
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
        productionQueue.removeWhere(IS_EXTRACTOR, PlanCancelSource.REACTION_GAS_DENIED_QUEUED, plan -> {
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
            productionQueue.removeWhere(IS_DRONE, PlanCancelSource.REACTION_CANNON_RUSH_DRONE, gameState::setImpossiblePlan);
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
        productionQueue.removeWhere(isMainCreepColony, PlanCancelSource.REACTION_MAIN_SUNKEN_CLEARED, plan -> {
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
        gameState.cancelPlan(assignedDrone, plan, PlanCancelSource.REACTION_GAS_DENIED_IN_PROGRESS);
        if (plan.getBuildPosition() != null) {
            baseData.unreserveExtractor(plan.getBuildPosition());
        }
    }
}
