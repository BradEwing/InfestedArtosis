package macro;

import bwapi.Game;
import bwapi.Position;
import bwapi.Race;
import bwapi.TilePosition;
import bwapi.UnitType;
import bwapi.UpgradeType;
import bwem.Base;
import info.GameState;
import info.TechProgression;
import info.map.BuildingPlanner;
import info.tracking.ObservedUnitTracker;
import info.tracking.StrategyTracker;
import util.OneShotGate;
import util.Time;
import macro.plan.Plan;
import macro.plan.PlanCancelSource;
import macro.plan.PlanType;
import macro.plan.UpgradePlan;
import strategy.buildorder.SunkenTargets;
import telemetry.PlanEvents;

import bwapi.Unit;
import info.BaseData;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
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
    static final int SPEED_UPGRADE_PRIORITY = 2;

    private static final Time EARLY_RUSH_WINDOW = new Time(5, 0);
    private static final Time EARLY_RUSH_HARD_DEADLINE = new Time(8, 0);
    static final int EARLY_RUSH_SAFE_ZERGLINGS = 12;
    static final int EARLY_RUSH_DRONE_FLOOR = 8;
    static final int EARLY_RUSH_CUT_ZERGLINGS = 8;
    static final int EARLY_RUSH_QUIET_FRAMES = 24 * 3;

    private GameState gameState;

    private final OneShotGate expansionCancel = new OneShotGate();
    private final OneShotGate lairCancel = new OneShotGate();
    private final OneShotGate droneCut = new OneShotGate();

    private int quietFrames;

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
        openMainForStaticDefense();
        clearMainSunkenOnExpansion();
    }

    private void scvRushReaction() {
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        if (!strategyTracker.isDetectedStrategy("SCVRush")) {
            return;
        }

        int zerglingCount = gameState.ourLivingUnitCount(UnitType.Zerg_Zergling);
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
            standDownFromEarlyRush();
            return;
        }

        int attackersAtBase = gameState.visibleEnemyMobileGroundCombatUnitsAtOurBases();
        int zerglingCount = gameState.ourLivingUnitCount(UnitType.Zerg_Zergling);
        boolean withinRushWindow = gameState.getGameTime().lessThanOrEqual(EARLY_RUSH_WINDOW);
        boolean preparing = isPreparingForEarlyRush(withinRushWindow, zerglingCount);
        if (attackersAtBase == 0 && !preparing) {
            quietFrames++;
            if (quietFrames >= EARLY_RUSH_QUIET_FRAMES) {
                standDownFromEarlyRush();
                return;
            }
        } else {
            quietFrames = 0;
        }

        gameState.setEarlyRushed(true);

        ProductionQueue productionQueue = gameState.getProductionQueue();
        planSpeedUpgrade(productionQueue);

        Race opponentRace = gameState.getOpponentRace();
        boolean delayLair = shouldDelayLair(opponentRace, gameState.getTechProgression().isPlannedMetabolicBoost(), isSpeedStarted());
        gameState.setEarlyRushDelayLair(delayLair);
        gameState.setEarlyRushMacroHatch(opponentRace == Race.Protoss);

        productionQueue.setPriorityWhere(IS_SPAWNING_POOL, 0);
        if (expansionCancel.fire()) {
            productionQueue.removeWhere(IS_EXPANSION_HATCHERY, PlanCancelSource.REACTION_EARLY_RUSH_EXPANSION,
                    gameState::setImpossiblePlan);
        }

        if (shouldFireLairCancel(delayLair)) {
            cancelQueuedLairs(productionQueue, gameState::setImpossiblePlan);
        }

        int droneCount = gameState.ourLivingUnitCount(UnitType.Zerg_Drone);
        if (shouldFireDroneCut(droneCount, zerglingCount)) {
            productionQueue.removeWhere(IS_DRONE, PlanCancelSource.REACTION_EARLY_RUSH_DRONE, gameState::setImpossiblePlan);
        }

        allowSunkenAtMainIfSingleBase(gameState.getBaseData());
    }

    boolean shouldFireDroneCut(int livingDrones, int livingZerglings) {
        return shouldCutDrones(livingDrones, livingZerglings) && droneCut.fire();
    }

    /**
     * Whether queued Lairs should be dropped this frame. The cancel runs once per delay window and
     * rearms whenever the delay lifts, so a window that reopens drops a Lair queued while it was shut.
     *
     * @param delayLair whether the early rush reaction holds the Lair back this frame
     * @return true on the first frame of each delay window
     */
    boolean shouldFireLairCancel(boolean delayLair) {
        if (!delayLair) {
            lairCancel.rearm();
            return false;
        }
        return lairCancel.fire();
    }

    /**
     * Whether the early rush reaction holds the Lair back, blocking new Lair plans through
     * {@link GameState#canPlanLair()} and dropping queued and scheduled ones.
     *
     * <p>Against Protoss the Lair waits for the whole reaction. Against Zerg it waits only while
     * Metabolic Boost is planned and not yet started, so a Lair claim cannot take the minerals the
     * upgrade is waiting on; it is released as soon as research starts. No other race delays it.
     *
     * @param opponentRace the opponent's race as currently resolved
     * @param speedPlanned whether a Metabolic Boost plan is outstanding
     * @param speedStarted whether Metabolic Boost research has started or finished
     * @return true while the Lair is held back
     */
    static boolean shouldDelayLair(Race opponentRace, boolean speedPlanned, boolean speedStarted) {
        if (opponentRace == Race.Protoss) {
            return true;
        }
        return opponentRace == Race.Zerg && speedPlanned && !speedStarted;
    }

    private boolean isSpeedStarted() {
        return gameState.getTechProgression().isMetabolicBoost()
                || gameState.getPlansBuilding().stream().anyMatch(IS_SPEED_UPGRADE);
    }

    private void standDownFromEarlyRush() {
        gameState.setEarlyRushed(false);
        gameState.setEarlyRushDelayLair(false);
        gameState.setEarlyRushMacroHatch(false);
        rearmEarlyRushCuts();
    }

    void rearmEarlyRushCuts() {
        expansionCancel.rearm();
        lairCancel.rearm();
        droneCut.rearm();
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
     *
     * @param productionQueue the queue the Lairs are removed from
     * @param onCancelled retires each removed plan
     */
    static void cancelQueuedLairs(ProductionQueue productionQueue, Consumer<Plan> onCancelled) {
        productionQueue.removeWhere(IS_LAIR, PlanCancelSource.REACTION_EARLY_RUSH_LAIR, onCancelled);
    }

    /**
     * Relaxes the main-base sunken restriction only while the main is genuinely our sole base.
     *
     * <p>Reserved bases are excluded. A queued expansion is not somewhere a colony can be built, and
     * counting one leaves no base eligible for static defense between the natural being queued and its
     * hatchery completing. This is the inverse of {@link #clearMainSunkenOnExpansion}, which reads the
     * same count so the two halves of the rule agree on what a single base means.
     */
    static void allowSunkenAtMainIfSingleBase(BaseData baseData) {
        if (baseData.currentBaseCount() < 2) {
            baseData.setAllowSunkenAtMain(true);
        }
    }

    private void planSpeedUpgrade(ProductionQueue productionQueue) {
        planSpeedUpgrade(productionQueue,
                gameState.getTechProgression(),
                gameState.ourUnitCount(UnitType.Zerg_Extractor) > 0,
                gameState.canPlanUpgrade(UpgradeType.Metabolic_Boost),
                gameState.getGameTime().getFrames());
    }

    /**
     * Queues Metabolic Boost and pulls it ahead of normal production.
     *
     * <p>The early rush reaction runs this in every matchup and leaves every Extractor standing,
     * since Metabolic Boost cannot be planned without one. Only the SCV rush reaction cancels
     * Extractors.
     *
     * @param productionQueue the queue the upgrade is added to and reprioritized in
     * @param techProgression marks the upgrade planned so it is queued once
     * @param haveExtractor whether a finished Extractor exists
     * @param canPlanSpeed whether Metabolic Boost may be queued now
     * @param currentFrame the current frame, which the new plan takes as its initial priority before
     *     being pulled forward to {@link #SPEED_UPGRADE_PRIORITY}
     */
    static void planSpeedUpgrade(ProductionQueue productionQueue, TechProgression techProgression,
                                 boolean haveExtractor, boolean canPlanSpeed, int currentFrame) {
        if (!haveExtractor) {
            return;
        }

        if (canPlanSpeed) {
            techProgression.setPlannedMetabolicBoost(true);
            productionQueue.add(new UpgradePlan(UpgradeType.Metabolic_Boost, currentFrame));
        }

        productionQueue.setPriorityWhere(IS_SPEED_UPGRADE, SPEED_UPGRADE_PRIORITY);
    }

    private void cancelAllExtractors(BaseData baseData) {
        ProductionQueue productionQueue = gameState.getProductionQueue();
        int currentFrame = gameState.getGameTime().getFrames();
        productionQueue.removeWhere(IS_EXTRACTOR, PlanCancelSource.REACTION_GAS_DENIED_QUEUED, plan -> {
            gameState.setImpossiblePlan(plan);
            if (plan.getBuildPosition() != null) {
                baseData.unreserveExtractor(plan.getBuildPosition(), currentFrame);
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
            if (!isCancellableExtractorMorph(unit.getType(), unit.isCompleted())) {
                continue;
            }
            TilePosition geyserTile = unit.getTilePosition();
            if (unit.cancelMorph()) {
                reclaimCancelledExtractor(baseData, geyserTile, currentFrame);
            }
        }
    }

    /**
     * Selects the extractors the raw cancel loop reaches. A plan that has already transitioned to
     * COMPLETE, which for a building means the morph was issued rather than finished, sits in none of
     * the plan sets swept above, so this loop is the only thing holding its geyser reservation.
     */
    static boolean isCancellableExtractorMorph(UnitType unitType, boolean completed) {
        return unitType == UnitType.Zerg_Extractor && !completed;
    }

    /**
     * Returns the geyser under a cancelled extractor morph and records the cancellation, which no plan
     * transition covers because the raw loop cancels the unit directly. Runs only once the cancel
     * command has been accepted, so an extractor that finishes before the command lands keeps its
     * geyser marked as ours. Emitting on the reclaim rather than on every pass keeps one row per
     * reclaim while the reaction fires each frame.
     *
     * <p>Arms the replan hold alongside the reclaim. This path is the one that repeats: the plan
     * behind a morph already in flight has left every plan set, so nothing else records that the
     * geyser it just handed back came from a cancellation rather than from a lost extractor.
     */
    static boolean reclaimCancelledExtractor(BaseData baseData, TilePosition geyserTile, int currentFrame) {
        if (!baseData.releaseExtractor(geyserTile)) {
            return false;
        }
        baseData.backoffExtractor(currentFrame);
        PlanEvents.unplannedCancel(UnitType.Zerg_Extractor, PlanCancelSource.REACTION_GAS_DENIED_IN_PROGRESS);
        return true;
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

        int droneCount = gameState.ourLivingUnitCount(UnitType.Zerg_Drone);
        int zerglingCount = gameState.ourLivingUnitCount(UnitType.Zerg_Zergling);

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

    /**
     * Opens the main to static defense while a race agnostic sunken floor is asking for one.
     *
     * <p>The count a build order asks for at the main is only reachable once BaseData calls the
     * main eligible, so the reaction layer reads the same {@link SunkenTargets} predicates the
     * build order layer raises its count on. Without this the floors produce a target no base can
     * satisfy: allowSunkenAtMain defaults false and is otherwise granted only by the rush and
     * 2Gate reactions.
     *
     * <p>Barracks pressure holds the main open past the natural, because a bio push of that size
     * arrives at whichever base is closest to the enemy and the main is where the drones are. The
     * 1Base floor only opens a main that is still our sole base, which is the case its own floor
     * could not otherwise reach; once the natural is up that base carries the floor instead.
     */
    private void openMainForStaticDefense() {
        BaseData baseData = gameState.getBaseData();
        if (isUnderBarracksPressure()) {
            baseData.setAllowSunkenAtMain(true);
            return;
        }

        if (isUnderOneBaseFloor()) {
            allowSunkenAtMainIfSingleBase(baseData);
        }
    }

    private boolean isUnderBarracksPressure() {
        return SunkenTargets.isBarracksPressure(gameState.enemyUnitCount(UnitType.Terran_Barracks));
    }

    private boolean isUnderOneBaseFloor() {
        return SunkenTargets.oneBaseSunkens(gameState.getStrategyTracker().isDetectedStrategy(SunkenTargets.ONE_BASE_STRATEGY),
                gameState.getBaseData().getEnemyBases().size(),
                gameState.getGameTime()) > 0;
    }

    /**
     * Whether the main should be closed to static defense again.
     *
     * <p>Reads the same base count as {@link #allowSunkenAtMainIfSingleBase}, so the two halves of
     * the rule agree on what a single base means, and the same Barracks threshold the build orders
     * raise their sunken count on. Pressure holds the main open past the natural: closing it would
     * cancel the colonies the raised count had just asked for there.
     *
     * @param baseData our bases and the current main sunken gate
     * @param underBarracksPressure whether the enemy's observed Barracks read as a bio push
     * @return true when the gate should close and the main's queued colonies be dropped
     */
    static boolean shouldClearMainSunken(BaseData baseData, boolean underBarracksPressure) {
        return baseData.isAllowSunkenAtMain() && !underBarracksPressure && baseData.currentBaseCount() >= 2;
    }

    private void clearMainSunkenOnExpansion() {
        BaseData baseData = gameState.getBaseData();
        if (!shouldClearMainSunken(baseData, isUnderBarracksPressure())) {
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
            baseData.unreserveExtractor(plan.getBuildPosition(), gameState.getGameTime().getFrames());
        }
    }
}
