package macro;

import bwapi.Game;
import bwapi.Position;
import bwapi.Race;
import bwapi.TilePosition;
import bwapi.UnitType;
import bwapi.UpgradeType;
import bwem.Base;
import info.GameState;
import info.Readiness;
import info.TechProgression;
import info.map.BuildingPlanner;
import info.tracking.ObservedUnitTracker;
import info.tracking.StrategyTracker;
import util.OneShotGate;
import util.Time;
import macro.plan.Plan;
import macro.plan.PlanCancelSource;
import macro.plan.PlanState;
import macro.plan.PlanType;
import macro.plan.UpgradePlan;
import strategy.buildorder.BuildOrder;
import strategy.buildorder.SunkenTargets;
import telemetry.PlanEvents;

import bwapi.Unit;
import info.BaseData;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private static final Predicate<Plan> IS_OVERLORD = p ->
            p.getType() == PlanType.UNIT && p.getPlannedUnit() == UnitType.Zerg_Overlord;

    private static final Predicate<Plan> IS_CREEP_COLONY = p ->
            p.getType() == PlanType.BUILDING && p.getPlannedUnit() == UnitType.Zerg_Creep_Colony;

    private static final Predicate<Plan> IS_SUNKEN_COLONY = p ->
            p.getType() == PlanType.BUILDING && p.getPlannedUnit() == UnitType.Zerg_Sunken_Colony;

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

    private static final Time FFE_DEADLINE = new Time(7, 0);

    /**
     * The highest priority the FFE boost may lift a Drone or Hatchery to: behind emergency defense,
     * so the boost never ties with the emergency colony, and never at 0, which is reserved for
     * emergency reactions.
     */
    static final int FFE_BOOST_FLOOR = BuildOrder.EMERGENCY_DEFENSE_PRIORITY + 1;

    private GameState gameState;

    private final OneShotGate expansionCancel = new OneShotGate();
    private final OneShotGate lairCancel = new OneShotGate();
    private final OneShotGate droneCut = new OneShotGate();
    private final OneShotGate ffeBoost = new OneShotGate();

    private int quietFrames;

    private int expansionsUnderConstruction;

    private boolean mainHeldThroughExpansion;

    public Reactions(GameState gameState) {
        this.gameState = gameState;
    }

    public void onFrame() {
        expansionsUnderConstruction = gameState.hatcheriesUnderConstruction(false);
        mainHeldThroughExpansion = false;
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

        holdMainIfSingleBase(baseData);
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
        BaseData baseData = gameState.getBaseData();
        raiseSunkensToEmergency(productionQueue, gameState.getPlansScheduled(), gameState.getPlansMorphing(),
                plan -> plan.getReservedColonyBase() != null && baseData.isEligibleForSunkenColony(plan.getReservedColonyBase()));
        planSpeedUpgrade(productionQueue);

        Race opponentRace = gameState.getOpponentRace();
        boolean delayLair = shouldDelayLair(opponentRace, gameState.getTechProgression().isPlannedMetabolicBoost(), isSpeedStarted(),
                gameState.knownEnemyMobileGroundCombatUnitsAtOurBases());
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

        allowSunkenAtMainIfNoExpansionUnderway(baseData, expansionsUnderConstruction);
    }

    /**
     * Lifts the Sunken Colony plans already waiting at a base the rush defence covers to the
     * priority the rush queues its own colony pairs at.
     *
     * <p>The build order stamps emergency priority only on the pairs it creates while rushed, so a
     * Sunken queued earlier, often with its Creep Colony already standing, would otherwise wait
     * behind every new emergency pair and never take the build-ahead slot from one. Plans still
     * queued are re-inserted through the queue; plans that have claimed their slot but not issued
     * the morph are lifted in place, so an emergency colony cannot take the slot back from them.
     *
     * @param productionQueue the queue holding plans not yet scheduled
     * @param plansScheduled plans holding a schedule claim
     * @param plansMorphing plans handed to a producer, including morphs not yet issued
     * @param atDefendedBase whether a Sunken plan's reserved base is one the rush defence may build at
     */
    static void raiseSunkensToEmergency(ProductionQueue productionQueue, Set<Plan> plansScheduled, Set<Plan> plansMorphing,
                                        Predicate<Plan> atDefendedBase) {
        Predicate<Plan> belowEmergency = plan -> plan.getPriority() > BuildOrder.EMERGENCY_DEFENSE_PRIORITY;
        Predicate<Plan> raisable = IS_SUNKEN_COLONY.and(belowEmergency).and(atDefendedBase);
        productionQueue.setPriorityWhere(raisable, BuildOrder.EMERGENCY_DEFENSE_PRIORITY);

        Predicate<Plan> claimed = raisable.and(plan -> plan.getState() == PlanState.SCHEDULE);
        Stream.concat(plansScheduled.stream(), plansMorphing.stream())
                .filter(claimed)
                .forEach(plan -> plan.setPriority(BuildOrder.EMERGENCY_DEFENSE_PRIORITY));
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
     * <p>Against Protoss the Lair waits for the whole reaction. Against Zerg it waits while
     * Metabolic Boost is planned and not yet started, so a Lair claim cannot take the minerals the
     * upgrade is waiting on, and while enemy ground combat units are last known to be at our bases,
     * so a Lair claim cannot take the minerals the rush defence is waiting on. The Zerg hold is
     * released once research has started and no attacker is known at our bases, or when the
     * reaction stands down. No other race delays it.
     *
     * @param opponentRace the opponent's race as currently resolved
     * @param speedPlanned whether a Metabolic Boost plan is outstanding
     * @param speedStarted whether Metabolic Boost research has started or finished
     * @param knownEnemyGroundUnitsAtOurBases living enemy ground combat units last known to be at our bases
     * @return true while the Lair is held back
     */
    static boolean shouldDelayLair(Race opponentRace, boolean speedPlanned, boolean speedStarted,
                                   int knownEnemyGroundUnitsAtOurBases) {
        if (opponentRace == Race.Protoss) {
            return true;
        }
        if (opponentRace != Race.Zerg) {
            return false;
        }
        return speedPlanned && !speedStarted || knownEnemyGroundUnitsAtOurBases > 0;
    }

    private boolean isSpeedStarted() {
        return isSpeedStarted(gameState.getTechProgression().isMetabolicBoost(), gameState.getPlansBuilding());
    }

    /**
     * Whether Metabolic Boost research has started or finished. A speed plan joins the building set
     * on the frame research begins; a scheduled one still only holds its claim, so it does not count.
     *
     * @param metabolicBoostResearched whether the upgrade has finished
     * @param plansBuilding plans whose research or construction has begun
     * @return true once research has begun
     */
    static boolean isSpeedStarted(boolean metabolicBoostResearched, Set<Plan> plansBuilding) {
        return metabolicBoostResearched || plansBuilding.stream().anyMatch(IS_SPEED_UPGRADE);
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
     *
     * <p>A Hatchery morphing at an expansion does not count either, so a grant made here holds the
     * main open for the whole morph. Only reactions whose threat reaches the main regardless of the
     * natural call this; the rest go through {@link #allowSunkenAtMainIfNoExpansionUnderway}.
     */
    static void allowSunkenAtMainIfSingleBase(BaseData baseData) {
        if (baseData.currentBaseCount() < 2) {
            baseData.setAllowSunkenAtMain(true);
        }
    }

    /**
     * Relaxes the main-base sunken restriction only while the main is our sole base and no
     * expansion Hatchery has started morphing.
     *
     * <p>Once the natural is morphing the main is no longer where the next base's defense belongs,
     * and {@link #shouldClearMainSunken} closes a main this grant opened earlier. A queued expansion
     * does not block the grant, for the reason given on {@link #allowSunkenAtMainIfSingleBase}.
     *
     * @param baseData our bases and the main sunken gate
     * @param expansionsUnderConstruction expansion Hatcheries that have started morphing and not finished
     */
    static void allowSunkenAtMainIfNoExpansionUnderway(BaseData baseData, int expansionsUnderConstruction) {
        if (expansionsUnderConstruction == 0) {
            allowSunkenAtMainIfSingleBase(baseData);
        }
    }

    /**
     * Opens a single-base main and marks it held for this frame, so a morphing expansion does not
     * close it. Used by the reactions whose threat is at the main itself.
     */
    private void holdMainIfSingleBase(BaseData baseData) {
        allowSunkenAtMainIfSingleBase(baseData);
        mainHeldThroughExpansion = true;
    }

    private void planSpeedUpgrade(ProductionQueue productionQueue) {
        planSpeedUpgrade(productionQueue,
                gameState.getTechProgression(),
                gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Extractor) > 0,
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
        allowSunkenAtMainIfNoExpansionUnderway(baseData, expansionsUnderConstruction);
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
        boolean enemyAhead = enemyUpAHatchery || enemyUpZerglings;

        if (shouldOpenMainForZvZPressure(enemyAhead, gameState.knownEnemyMobileGroundCombatUnitsAtOurBases())) {
            holdMainIfSingleBase(baseData);
        }
    }

    /**
     * Whether a ZvZ hatchery or zergling deficit should open the main to static defense.
     *
     * <p>The deficit alone is a count comparison that says nothing about where the enemy army is, and
     * the main counts as our sole base for the whole span between the natural starting its morph and
     * completing. A colony at the main is only worth its drone and minerals once an enemy ground unit
     * is known to be at one of our bases.
     *
     * <p>Last known positions are read rather than visible ones, because an army crossing the fog is
     * the case the colony has to be standing for.
     *
     * @param enemyAhead whether the enemy leads on hatcheries or zerglings
     * @param knownEnemyGroundUnitsAtOurBases living enemy ground combat units last known to be at our bases
     * @return true when the ZvZ reaction should open the main to static defense
     */
    static boolean shouldOpenMainForZvZPressure(boolean enemyAhead, int knownEnemyGroundUnitsAtOurBases) {
        return enemyAhead && knownEnemyGroundUnitsAtOurBases > 0;
    }

    private void ffeReaction() {
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        if (!strategyTracker.isDetectedStrategy("FFE")) {
            return;
        }

        if (gameState.getGameTime().greaterThan(FFE_DEADLINE)) {
            return;
        }

        if (shouldFireFfeBoost(gameState.getTechProgression())) {
            boostDronesForFfe(gameState.getProductionQueue());
        }
    }

    /**
     * Whether the FFE drone boost should be applied this frame. It fires once, on the first frame
     * the reaction runs, and never once army tech is committed: the reaction is "drone hard until
     * tech", not "drone over the army".
     *
     * @param techProgression the bot's tech state
     * @return true on the one frame the boost applies
     */
    boolean shouldFireFfeBoost(TechProgression techProgression) {
        return !isArmyTechCommitted(techProgression) && ffeBoost.fire();
    }

    /**
     * Whether a Hydralisk Den, Lair or Spire stands, so the army it unlocks outranks the drones.
     *
     * @param techProgression the bot's tech state
     * @return true once army tech is committed
     */
    static boolean isArmyTechCommitted(TechProgression techProgression) {
        return techProgression.isHydraliskDen() || techProgression.isLair() || techProgression.isHive()
                || techProgression.isSpire();
    }

    /**
     * Lifts the Drone and Hatchery plans queued now to the head of the rest of the queue.
     *
     * <p>The target is read off plans that are neither Drones, Hatcheries nor Overlords, so it
     * cannot chase a Drone it already lifted or an Overlord inserted just ahead of one. Plans
     * already ahead of the target keep their priority, and the target never falls below
     * {@link #FFE_BOOST_FLOOR}, so the boost cannot reach the priorities held for emergencies.
     * With nothing else queued there is nothing to jump and the queue is left unchanged.
     *
     * @param productionQueue the queue holding plans not yet scheduled
     */
    static void boostDronesForFfe(ProductionQueue productionQueue) {
        Predicate<Plan> boosted = IS_DRONE.or(IS_HATCHERY);
        int target = productionQueue.minPriorityWhere(boosted.or(IS_OVERLORD).negate());
        if (target == Integer.MAX_VALUE) {
            return;
        }
        int priority = Math.max(target, FFE_BOOST_FLOOR);
        productionQueue.setPriorityWhere(boosted.and(p -> p.getPriority() > priority), priority);
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
            allowSunkenAtMainIfNoExpansionUnderway(baseData, expansionsUnderConstruction);
        }
    }

    private boolean isUnderBarracksPressure() {
        return SunkenTargets.isBarracksPressure(gameState.enemyUnitCount(UnitType.Terran_Barracks));
    }

    private boolean isUnderOneBaseFloor() {
        return SunkenTargets.oneBaseSunkens(gameState.getStrategyTracker().isDetectedStrategy(SunkenTargets.ONE_BASE_STRATEGY),
                gameState.getBaseData().getEnemyBases().size(),
                SunkenTargets.hasGroundLead(gameState.getOpponentRace(),
                        gameState.ourLivingUnitCount(UnitType.Zerg_Zergling),
                        gameState.enemyUnitCount(UnitType.Zerg_Zergling)),
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
     * <p>The gate also closes as soon as an expansion Hatchery starts morphing, unless a reaction
     * whose threat is at the main held it open this frame. Waiting for the natural to complete
     * leaves the main open for the natural's whole build time. Colonies already scheduled or
     * building at the main are left alone; only queued ones are dropped.
     *
     * @param baseData our bases and the current main sunken gate
     * @param underBarracksPressure whether the enemy's observed Barracks read as a bio push
     * @param expansionsUnderConstruction expansion Hatcheries that have started morphing and not finished
     * @param mainHeldThroughExpansion whether the SCV rush or ZvZ pressure reaction held the main open this frame
     * @return true when the gate should close and the main's queued colonies be dropped
     */
    static boolean shouldClearMainSunken(BaseData baseData, boolean underBarracksPressure,
                                         int expansionsUnderConstruction, boolean mainHeldThroughExpansion) {
        if (!baseData.isAllowSunkenAtMain() || underBarracksPressure) {
            return false;
        }
        if (baseData.currentBaseCount() >= 2) {
            return true;
        }
        return expansionsUnderConstruction > 0 && !mainHeldThroughExpansion;
    }

    private void clearMainSunkenOnExpansion() {
        BaseData baseData = gameState.getBaseData();
        if (!shouldClearMainSunken(baseData, isUnderBarracksPressure(), expansionsUnderConstruction, mainHeldThroughExpansion)) {
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
