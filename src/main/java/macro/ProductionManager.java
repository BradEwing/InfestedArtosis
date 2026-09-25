package macro;

import bwapi.Game;
import bwapi.Player;
import bwapi.Position;
import bwapi.TechType;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.UpgradeType;
import bwem.Base;
import info.GameState;
import info.Readiness;
import info.ResourceCount;
import info.TechProgression;
import info.UnitTypeCount;
import info.map.BuildingPlanner;
import macro.plan.ColonyClaims;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.PlanCancelSource;
import macro.plan.PlanComparator;
import macro.plan.PlanState;
import macro.plan.PlanType;
import macro.plan.UnitPlan;
import strategy.buildorder.BuildOrder;
import telemetry.PlanEvents;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;
import util.TravelTime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * Manages the production of units, buildings, upgrades and research.
 * <p>
 * The Bot's BuildOrder is responsible for deciding what units should be queued; the exact unit
 * is currently determined probabilistically by UnitWeights.
 */
public class ProductionManager {

    private static final int OVERLORD_SUPPLY = UnitType.Zerg_Overlord.supplyProvided();

    /** Raw supply headroom the overlord planner keeps ahead of the queue it is walking. */
    static final int SUPPLY_BUFFER = 4;

    private static final int MAX_SUPPLY = 400;

    private static final int HATCHERY_MINERAL_PRICE = UnitType.Zerg_Hatchery.mineralPrice();

    private Game game;

    private GameState gameState;

    // isPlanning contingent on -> hitting min supply set by build order OR queue exhaust
    private boolean isPlanning = false;

    private final BuildAheadSlot buildAheadSlot = new BuildAheadSlot();

    private final BuildAheadSlot unitAheadSlot = new BuildAheadSlot();

    /** The tech wave production is pre-positioning for this frame, or null. */
    private TechWave techWave;

    private int currentFrame = 5;

    /** Plans the current schedule pass pulled out of the queue; they still hold their colony claims. */
    private List<Plan> schedulingBatch = new ArrayList<>();


    private BuildOrder activeBuildOrder;

    private Reactions reactions;

    public ProductionManager(Game game, GameState gameState, BuildOrder opener) {
        this.game = game;
        this.gameState = gameState;
        this.reactions = new Reactions(gameState);

        this.activeBuildOrder = opener;
    }

    private void debugProductionQueue() { }

    // TODO: Ensure print out of production queue is displaying how much time is remaining
    private void debugInProgressQueue() { }

    private void debugScheduledPlannedItems() { }

    // debug console messaging goes here
    private void debug() {
        debugProductionQueue();
        debugInProgressQueue();
        debugScheduledPlannedItems();
    }

    public void onFrame() {
        debug();

        currentFrame = game.getFrameCount();

        transition();
        reactions.onFrame();
        updateTechWave();
        plan();
        cancelImpossiblePlans();
        cancelDelayedLairPlans();
        cancelExcessHatcheryPlans();
        cancelExcessOverlordPlans();
        enforceBuildAheadSlot();
        enforceUnitAheadSlot();
        schedulePlannedItems();
        buildUpgrades();
        researchTech();
    }

    private void transition() {
        if (gameState.isTransitionBuildOrder()) {
            this.activeBuildOrder = gameState.getActiveBuildOrder();
        }
    }

    /** Re-derives the pending tech wave from the structures under construction this frame. */
    private void updateTechWave() {
        techWave = null;
        Unit prerequisite = null;
        for (Unit unit : gameState.getSelf().getUnits()) {
            if (!unit.isCompleted() && TechWave.waveUnit(unit.getType()) != null) {
                prerequisite = unit;
                break;
            }
        }
        if (prerequisite == null) {
            return;
        }

        UnitType prerequisiteType = prerequisite.getType();
        boolean rushReactionActive = gameState.isEarlyRushed()
                || gameState.isCannonRushed()
                || gameState.isScvRushed()
                || gameState.isLarvaDeadlocked();
        boolean groundSafe = TechWave.isGroundSafe(
                gameState.ourLivingUnitCount(UnitType.Zerg_Zergling),
                gameState.enemyUnitCount(UnitType.Zerg_Zergling),
                gameState.visibleEnemyMobileGroundCombatUnitsAtOurBases());
        techWave = TechWave.pending(
                prerequisiteType,
                prerequisite.getRemainingBuildTime(),
                gameState.structureCount(Readiness.USABLE, prerequisiteType) > 0,
                rushReactionActive,
                groundSafe);
    }

    private PlanBlocker techWaveBlocker(Plan plan) {
        if (techWave == null) {
            return PlanBlocker.NONE;
        }
        ResourceCount resourceCount = gameState.getResourceCount();
        return techWave.blocker(
                plan,
                resourceCount.freeLarva(gameState.numLarva(), gameState.larvaAssignedToPlans()),
                resourceCount.availableMinerals(),
                resourceCount.availableGas());
    }

    /** Queues the Overlord a pending tech wave needs so it hatches before the wave is issued. */
    private void planTechWaveSupply(Player self) {
        if (techWave == null) {
            return;
        }
        int supplyInFlight = gameState.getUnitTypeCount().plannedCount(UnitType.Zerg_Overlord) * OVERLORD_SUPPLY;
        if (!techWave.needsOverlord(self.supplyTotal() - self.supplyUsed(), supplyInFlight)) {
            return;
        }
        addUnitToQueue(UnitType.Zerg_Overlord, UnitPlan.ADVANCED_UNIT_PRIORITY - 1);
        ResourceCount resourceCount = gameState.getResourceCount();
        resourceCount.setPlannedSupply(resourceCount.getPlannedSupply() + OVERLORD_SUPPLY);
    }

    private void cancelImpossiblePlans() {
        gameState.getProductionQueue().removeWhere(
                plan -> !canSchedulePlan(plan),
                PlanCancelSource.PRODUCTION_IMPOSSIBLE_SWEEP,
                this::cancelSweptPlan);

        cancelImpossibleScheduledLurkerPlans();
        removePlansWithLaterPrerequisites();
    }

    /** Records the predicate the sweep failed on before the plan is retired. */
    private void cancelSweptPlan(Plan plan) {
        if (plan.getType() == PlanType.UNIT) {
            PlanBlocker blocker = unitScheduleBlocker(plan.getPlannedUnit());
            plan.setCancelSource(PlanCancelSource.PRODUCTION_IMPOSSIBLE_SWEEP, blocker.cancelReason());
        }
        gameState.setImpossiblePlan(plan);
    }

    private void cancelExcessHatcheryPlans() {
        boolean excess = gameState.hasExcessHatchery();
        if (!excess) {
            return;
        }
        boolean excessForExpansion = gameState.hasExcessExpansionHatchery();

        gameState.getProductionQueue().removeWhere(
                p -> isExcessHatcheryPlan(p, excess, excessForExpansion),
                PlanCancelSource.PRODUCTION_EXCESS_HATCHERY_QUEUED,
                gameState::setImpossiblePlan);

        Set<Plan> scheduledPlansToCancel = gameState.getPlansScheduled()
                .stream()
                .filter(plan -> isExcessHatcheryPlan(plan, excess, excessForExpansion))
                .collect(Collectors.toSet());

        for (Plan plan : scheduledPlansToCancel) {
            buildAheadSlot.release(plan);
            gameState.getPlansScheduled().remove(plan);
            gameState.cancelPlan(null, plan, PlanCancelSource.PRODUCTION_EXCESS_HATCHERY_SCHEDULED);
        }
    }

    /**
     * Whether the excess sweep cancels this plan: a hatchery plan the excess rule for its kind
     * reports as excess.
     *
     * @param plan a plan in the queue or the scheduled set
     * @param excess the excess rule with every hatchery counted
     * @param excessForExpansion the excess rule with completed macro hatcheries left out
     */
    static boolean isExcessHatcheryPlan(Plan plan, boolean excess, boolean excessForExpansion) {
        return plan.getType() == PlanType.BUILDING
                && plan.getPlannedUnit() == UnitType.Zerg_Hatchery
                && HatcheryCapacity.isExcessPlan(plan.isMacroHatchery(), excess, excessForExpansion);
    }

    /** Drops scheduled Lair plans while an early rush delays the Lair; the reaction removes only queued ones. */
    private void cancelDelayedLairPlans() {
        for (Plan plan : delayedLairPlans(gameState.isEarlyRushDelayLair(), gameState.getPlansScheduled())) {
            buildAheadSlot.release(plan);
            gameState.getPlansScheduled().remove(plan);
            gameState.cancelPlan(null, plan, PlanCancelSource.PRODUCTION_DELAYED_LAIR_SCHEDULED);
        }
    }

    /**
     * The scheduled Lair plans an early rush delay cancels, collected apart from the scheduled set
     * so the caller can remove them from it.
     *
     * @param delayLair whether the early rush reaction holds the Lair back
     * @param plansScheduled plans holding a schedule claim
     * @return the Lair plans to cancel, empty while the Lair is not delayed
     */
    static Set<Plan> delayedLairPlans(boolean delayLair, Set<Plan> plansScheduled) {
        if (!delayLair) {
            return new HashSet<>();
        }

        return plansScheduled.stream()
                .filter(plan -> plan.getType() == PlanType.BUILDING && plan.getPlannedUnit() == UnitType.Zerg_Lair)
                .collect(Collectors.toSet());
    }

    private boolean hasExcessSupply(Player self) {
        return SupplyCapacity.isExcess(self.supplyTotal(), self.supplyUsed());
    }

    private void cancelExcessOverlordPlans() {
        if (!hasExcessSupply(game.self())) {
            return;
        }

        gameState.getProductionQueue().removeWhere(
                p -> p.getType() == PlanType.UNIT && p.getPlannedUnit() == UnitType.Zerg_Overlord,
                PlanCancelSource.PRODUCTION_EXCESS_OVERLORD,
                gameState::setImpossiblePlan);
    }

    /** Bounds how long a building plan holds the build-ahead slot. */
    private void enforceBuildAheadSlot() {
        buildAheadSlot.reconcile(activeBuildingPlans());

        cancelUnexecutableBuildingClaims();
        refreshBuildAheadPredictions();

        for (Plan plan : buildAheadSlot.stalled(currentFrame)) {
            PlanState state = plan.getState();
            if (state != PlanState.SCHEDULE && state != PlanState.BUILDING) {
                buildAheadSlot.release(plan);
                continue;
            }
            PlanEvents.buildAheadEvicted(plan, buildAheadSlot.heldFrames(plan, currentFrame), starvedQueuedPlans());
            requeueStalledPlan(plan, buildAheadSlot);
        }

        for (Plan plan : buildAheadSlot.holdReportsDue(currentFrame)) {
            PlanEvents.buildAheadHold(plan, buildAheadSlot.heldFrames(plan, currentFrame), starvedQueuedPlans());
        }
    }

    private void enforceUnitAheadSlot() {
        unitAheadSlot.reconcile(activeUnitPlans());

        for (Plan plan : unitAheadSlot.stalled(currentFrame)) {
            PlanState state = plan.getState();
            if (state != PlanState.SCHEDULE && state != PlanState.BUILDING) {
                unitAheadSlot.release(plan);
                continue;
            }
            PlanEvents.buildAheadEvicted(plan, unitAheadSlot.heldFrames(plan, currentFrame), starvedQueuedPlans());
            requeueStalledPlan(plan, unitAheadSlot);
        }

        for (Plan plan : unitAheadSlot.holdReportsDue(currentFrame)) {
            PlanEvents.buildAheadHold(plan, unitAheadSlot.heldFrames(plan, currentFrame), starvedQueuedPlans());
        }
    }

    private Set<Plan> activeBuildingPlans() {
        Set<Plan> active = activeUnitPlans();
        active.addAll(gameState.getPlansMorphing());
        return active;
    }

    private Set<Plan> activeUnitPlans() {
        Set<Plan> active = new HashSet<>(gameState.getPlansScheduled());
        active.addAll(gameState.getPlansBuilding());
        return active;
    }

    private int starvedQueuedPlans() {
        ResourceCount resourceCount = gameState.getResourceCount();
        int starved = 0;
        for (Plan plan : gameState.getProductionQueue()) {
            if (resourceCount.availableMinerals() < plan.mineralPrice()
                    || resourceCount.availableGas() < plan.gasPrice()) {
                starved += 1;
            }
        }
        return starved;
    }

    private void refreshBuildAheadPredictions() {
        refreshBuildAheadPredictions(
                buildAheadSlot,
                gameState.frameCanAffordReserved(currentFrame),
                this::builderTravelFrames,
                gameState.getResourceCount()::bankCovers);
    }

    /**
     * Re-times every hold whose plan still waits on its builder, parked or launched, against
     * current income.
     *
     * <p>The prediction a claim was taken on decays when gatherers die or are reassigned, and a
     * launched builder is still walking, or clearing its path, when the claim-time deadline lands.
     * Left stale, the plan rides to the claim-time cap and is evicted with its builder mid-walk.
     * Only a parked plan's predictedReadyFrame is rewritten, since PlanManager releases the builder
     * on it; a launched builder reads it to decide when to clear a blocking mineral.
     *
     * <p>A plan the bank already covers is carried no further than
     * {@code claimFrame + BuildAheadSlot.MAX_HOLD_FRAMES}, so a stalled builder is evicted instead
     * of riding the sliding ledger prediction to the total hold.
     */
    static void refreshBuildAheadPredictions(
            BuildAheadSlot slot,
            int predictedReadyFrame,
            ToIntFunction<Plan> travelFrames,
            Predicate<Plan> bankCovers) {
        for (Plan plan : slot.claimedPlans()) {
            PlanState state = plan.getState();
            if (state == PlanState.SCHEDULE) {
                plan.setPredictedReadyFrame(predictedReadyFrame);
            } else if (state != PlanState.BUILDING) {
                continue;
            }
            slot.extend(plan, predictedReadyFrame, travelFrames.applyAsInt(plan), bankCovers.test(plan));
        }
    }

    /**
     * Requeues an evicted plan. It keeps its build position, so its tiles are not reserved twice,
     * and its priority, so an eviction cannot reorder it behind plans queued after it. The per-plan
     * backoff bars it from a fresh hold: a plan that can pay resumes the hold it was evicted from,
     * and one that cannot waits the backoff out.
     */
    private void requeueStalledPlan(Plan plan, BuildAheadSlot slot) {
        slot.releaseWithBackoff(plan, currentFrame);
        requeueHolder(plan);
    }

    /**
     * Hands a holder's slot to emergency defence or to a colony morph whose Creep Colony is
     * complete. The holder did not stall, so it is requeued without a backoff and claims a fresh
     * hold once the slot is free again.
     *
     * @param holder the plan giving up the slot
     * @param taker the plan taking it
     */
    private void yieldBuildAheadHold(Plan holder, Plan taker) {
        PlanEvents.buildAheadYielded(holder, buildAheadSlot.heldFrames(holder, currentFrame), taker);
        buildAheadSlot.release(holder);
        PlanState state = holder.getState();
        if (state != PlanState.SCHEDULE && state != PlanState.BUILDING) {
            return;
        }
        requeueHolder(holder);
    }

    private void requeueHolder(Plan plan) {
        removeFromActivePlans(plan);
        releaseExecutor(plan);
        gameState.getResourceCount().unreserveUnit(plan.getPlannedUnit());
        plan.setState(PlanState.PLANNED);
        gameState.getProductionQueue().add(plan);
    }

    private void cancelUnexecutableBuildingClaims() {
        Map<Plan, PlanCancelSource> unexecutable = new HashMap<>();
        for (Plan plan : buildAheadSlot.claimedPlans()) {
            PlanCancelSource source = buildAheadCancellationSource(
                    plan,
                    canSchedulePlan(plan),
                    executorOf(plan) != null);
            if (source != null) {
                unexecutable.put(plan, source);
            }
        }

        for (Map.Entry<Plan, PlanCancelSource> entry : unexecutable.entrySet()) {
            Plan plan = entry.getKey();
            buildAheadSlot.releaseWithBackoff(plan, currentFrame);
            removeFromActivePlans(plan);
            gameState.cancelPlan(executorOf(plan), plan, entry.getValue());
        }
    }

    static PlanCancelSource buildAheadCancellationSource(
            Plan plan,
            boolean prerequisitesAvailable,
            boolean executorAssigned) {
        if (!prerequisitesAvailable) {
            return PlanCancelSource.PRODUCTION_SCHEDULED_PREREQUISITE_LOST;
        }
        if (plan.getState() == PlanState.BUILDING && !executorAssigned) {
            return PlanCancelSource.PRODUCTION_EXECUTOR_LOST;
        }
        return null;
    }

    private void removeFromActivePlans(Plan plan) {
        gameState.getPlansScheduled().remove(plan);
        gameState.getPlansBuilding().remove(plan);
        gameState.getPlansMorphing().remove(plan);
    }

    private Unit executorOf(Plan plan) {
        return gameState.executorOf(plan);
    }

    private void releaseExecutor(Plan plan) {
        Unit executor = executorOf(plan);
        if (executor == null) {
            return;
        }

        gameState.getAssignedPlannedItems().remove(executor);
        ManagedUnit managedUnit = gameState.getManagedUnitLookup().get(executor);
        if (managedUnit != null) {
            managedUnit.setPlan(null);
            managedUnit.setRole(UnitRole.IDLE);
        }
    }

    private void removePlansWithLaterPrerequisites() {
        List<Plan> plansToRemove = new ArrayList<>();
        List<Plan> queueList = gameState.getProductionQueue().toSortedList();
        
        for (int i = 0; i < queueList.size(); i++) {
            Plan currentPlan = queueList.get(i);
            UnitType prerequisite = null;
            
            switch (currentPlan.getType()) {
                case UNIT:
                    prerequisite = getPrerequisiteForUnit(currentPlan.getPlannedUnit());
                    break;
                case UPGRADE:
                    prerequisite = getPrerequisiteForUpgrade(currentPlan.getPlannedUpgrade());
                    break;
                case TECH:
                    prerequisite = getPrerequisiteForTech(currentPlan.getPlannedTechType());
                    break;
                default:
                    continue;
            }
            
            if (prerequisite == null) {
                continue;
            }
            
            for (int j = i + 1; j < queueList.size(); j++) {
                Plan laterPlan = queueList.get(j);
                if (laterPlan.getType() == PlanType.BUILDING && 
                    laterPlan.getPlannedUnit() == prerequisite) {
                    plansToRemove.add(currentPlan);
                    break;
                }
            }
        }
        
        for (Plan plan : plansToRemove) {
            gameState.getProductionQueue().remove(plan);
            plan.setCancelSource(PlanCancelSource.PRODUCTION_LATER_PREREQUISITE);
            gameState.setImpossiblePlan(plan);
        }
    }

    private UnitType getPrerequisiteForUnit(UnitType unitType) {
        switch (unitType) {
            case Zerg_Zergling:
            case Zerg_Lair:
                return UnitType.Zerg_Spawning_Pool;
            case Zerg_Hydralisk:
            case Zerg_Lurker:
                return UnitType.Zerg_Hydralisk_Den;
            case Zerg_Mutalisk:
            case Zerg_Scourge:
                return UnitType.Zerg_Spire;
            case Zerg_Queen:
            case Zerg_Hive:
                return UnitType.Zerg_Queens_Nest;
            case Zerg_Ultralisk:
                return UnitType.Zerg_Ultralisk_Cavern;
            case Zerg_Defiler:
                return UnitType.Zerg_Defiler_Mound;
            default:
                return null;
        }
    }

    private UnitType getPrerequisiteForUpgrade(UpgradeType upgradeType) {
        switch (upgradeType) {
            case Metabolic_Boost:
                return UnitType.Zerg_Spawning_Pool;
            case Muscular_Augments:
            case Grooved_Spines:
                return UnitType.Zerg_Hydralisk_Den;
            case Zerg_Carapace:
            case Zerg_Missile_Attacks:
            case Zerg_Melee_Attacks:
                return UnitType.Zerg_Evolution_Chamber;
            case Zerg_Flyer_Attacks:
            case Zerg_Flyer_Carapace:
                return UnitType.Zerg_Spire;
            case Pneumatized_Carapace:
                return UnitType.Zerg_Lair;
            case Chitinous_Plating:
            case Anabolic_Synthesis:
                return UnitType.Zerg_Ultralisk_Cavern;
            case Adrenal_Glands:
                return UnitType.Zerg_Spawning_Pool;
            default:
                return null;
        }
    }

    private UnitType getPrerequisiteForTech(TechType techType) {
        switch (techType) {
            case Lurker_Aspect:
                return UnitType.Zerg_Hydralisk_Den;
            case Consume:
            case Plague:
                return UnitType.Zerg_Defiler_Mound;
            default:
                return null;
        }
    }

    /**
     * Plan overlords by inserting them at appropriate queue priority.
     * Walks the queue in priority order and inserts overlords when supply would run out.
     */
    private void planSupply(Player self) {
        if (self.supplyUsed() >= MAX_SUPPLY) {
            return;
        }

        if (hasExcessSupply(self)) {
            return;
        }

        if (activeBuildOrder.holdsOverlords(gameState)) {
            return;
        }

        planTechWaveSupply(self);

        final int overlordCount = gameState.ourLivingUnitCount(UnitType.Zerg_Overlord);
        final int plannedSupply = gameState.getResourceCount().getPlannedSupply();
        final boolean isNinePool = "9PoolSpeed".equals(activeBuildOrder.getName());
        if (overlordCount < 2 && !isNinePool) {
            if (self.supplyUsed() >= 18 && overlordCount < 2 && plannedSupply == 0) {
                addUnitToQueue(UnitType.Zerg_Overlord, 1);
                gameState.getResourceCount().setPlannedSupply(OVERLORD_SUPPLY);
                return;
            }
            return;
        }
    
        List<Plan> sortedQueue = gameState.getProductionQueue().toSortedList();

        List<Plan> scheduledPlans = new ArrayList<>(gameState.getPlansScheduled());
        scheduledPlans.sort(new PlanComparator());

        List<Integer> insertPriorities = overlordInsertPriorities(
                scheduledPlans,
                sortedQueue,
                self.supplyTotal() - self.supplyUsed(),
                plannedSupply,
                self.supplyUsed());

        final int supplyAfterInserts = plannedSupply + OVERLORD_SUPPLY * insertPriorities.size();
        for (int priority : insertPriorities) {
            addUnitToQueue(UnitType.Zerg_Overlord, priority);
        }
        gameState.getResourceCount().setPlannedSupply(supplyAfterInserts);

        // Emergency fallback: nothing waiting can fit in the remaining supply, with high minerals
        int cheapestWaitingUnit = Math.min(
                SupplyCapacity.cheapestUnitSupply(sortedQueue),
                SupplyCapacity.cheapestUnitSupply(scheduledPlans));
        boolean supplyBlocked = SupplyCapacity.isBlocked(
                self.supplyTotal(), self.supplyUsed(), cheapestWaitingUnit);
        if (supplyBlocked && self.minerals() > 700 && supplyAfterInserts < 80) {
            addUnitToQueue(UnitType.Zerg_Overlord, 1);
            gameState.getResourceCount().setPlannedSupply(supplyAfterInserts + OVERLORD_SUPPLY);
        }
    }

    /**
     * Walks the scheduled plans and then the production queue, returning the priority at which an
     * overlord has to be inserted every time the running supply headroom falls under the buffer.
     * Each unit plan is charged {@link SupplyCapacity#morphSupplyCost}, the same cost
     * {@link #isSupplyBlocked} gates the morph on.
     *
     * <p>A scheduled overlord earns headroom because it holds a larva and will morph. A queued
     * overlord earns none: the production queue holds PLANNED plans only, every one of them is
     * already counted in plannedSupply, and a plan blocked on larva may never be schedulable at
     * all. Crediting those was what let four deadlocked overlords report supply as solved.
     */
    static List<Integer> overlordInsertPriorities(
            List<Plan> scheduledPlans,
            List<Plan> queuedPlans,
            int freeSupply,
            int plannedSupply,
            int supplyUsed) {
        int availableSupply = freeSupply + plannedSupply;

        for (Plan plan : scheduledPlans) {
            if (plan.getType() != PlanType.UNIT) {
                continue;
            }
            UnitType unitType = plan.getPlannedUnit();
            if (unitType == UnitType.Zerg_Overlord) {
                availableSupply += OVERLORD_SUPPLY;
            } else {
                availableSupply -= SupplyCapacity.morphSupplyCost(unitType);
            }
        }

        List<Integer> insertPriorities = new ArrayList<>();
        for (Plan plan : queuedPlans) {
            if (plan.getType() != PlanType.UNIT) {
                continue;
            }

            UnitType unitType = plan.getPlannedUnit();
            if (unitType == UnitType.Zerg_Overlord) {
                continue;
            }

            availableSupply -= SupplyCapacity.morphSupplyCost(unitType);

            while (availableSupply < SUPPLY_BUFFER
                    && supplyUsed + plannedSupply + OVERLORD_SUPPLY * insertPriorities.size() < MAX_SUPPLY) {
                insertPriorities.add(Math.max(1, plan.getPriority() - 1));
                availableSupply += OVERLORD_SUPPLY;
            }
        }
        return insertPriorities;
    }

    // This is only used for planSupply()
    // TODO: Move to BuildOrder?
    private void addUnitToQueue(UnitType unitType, int priority) {
        UnitTypeCount unitTypeCount = this.gameState.getUnitTypeCount();
        gameState.getProductionQueue().add(new UnitPlan(unitType, priority));
        unitTypeCount.planUnit(unitType);
    }

    private void plan() {
        gameState.getProductionQueue().addAll(activeBuildOrder.planDefense(gameState));

        if (!isPlanning && !gameState.getProductionQueue().isEmpty()) {
            return;
        }

        // Once opener items are exhausted, plan items
        isPlanning = true;

        planSupply(gameState.getSelf());

        List<Plan> plans = activeBuildOrder.plan(gameState);
        gameState.getProductionQueue().addAll(plans);
        promoteOpenDroneRound();
        demoteClosedDroneRound();
    }

    /**
     * Moves the oldest queued Drones to {@link UnitPlan#DRONE_ROUND_PRIORITY} while a drone round is
     * open, so the Drones the build already queued go ahead of the advanced unit band and the
     * frame-numbered plans instead of waiting behind them.
     */
    private void promoteOpenDroneRound() {
        DroneRound round = gameState.getDroneRound();
        if (!round.isActive()) {
            return;
        }
        int roundDronesInFlight = (int) gameState.getPlansScheduled().stream()
                .filter(plan -> DroneRound.isRoundDrone(plan)
                        && (plan.getState() == PlanState.SCHEDULE || plan.getState() == PlanState.BUILDING))
                .count();
        promoteOldestDrones(gameState.getProductionQueue(), round, roundDronesInFlight);
    }

    /**
     * Promotes the oldest queued Drones the open round still has room for to
     * {@link UnitPlan#DRONE_ROUND_PRIORITY}. Drones already hatched, in an egg, held at that priority
     * in the queue or scheduled from it count against the round's target, so a promotion never
     * passes it. Only a PLANNED Drone behind that priority is promoted: one already ahead of it keeps
     * its place.
     *
     * @param queue the production queue
     * @param round the drone round
     * @param roundDronesInFlight round Drones scheduled from the queue that are not yet in an egg
     * @return the promoted plans, oldest first
     */
    static List<Plan> promoteOldestDrones(ProductionQueue queue, DroneRound round, int roundDronesInFlight) {
        int queuedRoundDrones = 0;
        List<Plan> candidates = new ArrayList<>();
        for (Plan plan : queue) {
            if (DroneRound.isRoundDrone(plan)) {
                queuedRoundDrones++;
            } else if (isPromotableDrone(plan)) {
                candidates.add(plan);
            }
        }
        int slots = round.openDroneSlots(queuedRoundDrones + roundDronesInFlight);
        if (slots == 0 || candidates.isEmpty()) {
            return new ArrayList<>();
        }
        candidates.sort(Comparator.comparingInt(Plan::getPlanId));
        List<Plan> promoted = new ArrayList<>(candidates.subList(0, Math.min(slots, candidates.size())));
        Set<Plan> promotedSet = new HashSet<>(promoted);
        queue.setPriorityWhere(promotedSet::contains, UnitPlan.DRONE_ROUND_PRIORITY);
        for (Plan plan : promoted) {
            PlanEvents.promoted(plan);
        }
        return promoted;
    }

    private static boolean isPromotableDrone(Plan plan) {
        return plan.getType() == PlanType.UNIT
                && plan.getPlannedUnit() == UnitType.Zerg_Drone
                && plan.getState() == PlanState.PLANNED
                && plan.getPriority() > UnitPlan.DRONE_ROUND_PRIORITY;
    }

    /**
     * Returns the Drones a closed drone round queued or promoted to the current frame, behind the
     * advanced unit band, so a threat that closes the round puts the army back ahead of them.
     */
    private void demoteClosedDroneRound() {
        demoteRoundDrones(gameState.getProductionQueue(), gameState.getDroneRound(), currentFrame);
    }

    /**
     * Returns every queued Drone at {@link UnitPlan#DRONE_ROUND_PRIORITY}, whether the round queued
     * or promoted it, to the current frame once the round is closed.
     *
     * @param queue the production queue
     * @param round the drone round
     * @param frame the current frame, which becomes the demoted Drones' priority
     */
    static void demoteRoundDrones(ProductionQueue queue, DroneRound round, int frame) {
        if (round.isActive()) {
            return;
        }
        queue.setPriorityWhere(DroneRound::isRoundDrone, frame);
    }


    /**
     * Plans that are impossible to schedule can block the queue.
     * @return boolean indicating if the plan can be scheduled
     */
    private boolean canSchedulePlan(Plan plan) {
        switch (plan.getType()) {
            case UNIT:
                return canScheduleUnit(plan.getPlannedUnit());
            case BUILDING:
                return canScheduleBuilding(plan.getPlannedUnit());
            case UPGRADE:
                return canScheduleUpgrade(plan.getPlannedUpgrade());
            case TECH:
                return canScheduleTech(plan.getPlannedTechType());
            default:
                return false;
        }
    }

    private boolean canScheduleUnit(UnitType unitType) {
        return unitScheduleBlocker(unitType) == PlanBlocker.NONE;
    }

    /** The first gate a unit plan fails, or NONE; tech units share the gate with the build order. */
    private PlanBlocker unitScheduleBlocker(UnitType unitType) {
        TechProgression techProgression = gameState.getTechProgression();
        final int numHatcheries = gameState.getBaseData().numHatcheries();

        switch (unitType) {
            case Zerg_Overlord:
            case Zerg_Drone:
                return numHatcheries > 0 ? PlanBlocker.NONE : PlanBlocker.NO_PRODUCER;
            case Zerg_Zergling:
                if (techProgression.isPlannedSpawningPool() || techProgression.isSpawningPool()) {
                    return PlanBlocker.NONE;
                }
                return PlanBlocker.TECH_MISSING;
            case Zerg_Lurker:
                PlanBlocker lurkerBlocker = advancedUnitBlocker(unitType);
                if (lurkerBlocker != PlanBlocker.NONE) {
                    return lurkerBlocker;
                }
                return morphProducerBlocker(unitType);
            case Zerg_Hydralisk:
            case Zerg_Mutalisk:
            case Zerg_Scourge:
            case Zerg_Ultralisk:
            case Zerg_Defiler:
                return advancedUnitBlocker(unitType);
            default:
                return PlanBlocker.NO_PRODUCER;
        }
    }

    /**
     * Blocks a morph from an existing unit when every producer of its type is already spoken for.
     *
     * Only living producers count: a planned producer cannot be assigned to the morph, and a
     * producer already claimed by a scheduled or assigned plan of this type is spoken for.
     *
     * @param unitType the planned morph, for example a Lurker
     * @return NONE while a free producer remains, otherwise NO_PRODUCER
     */
    private PlanBlocker morphProducerBlocker(UnitType unitType) {
        UnitType producer = unitType.whatBuilds().getKey();
        int claimedProducers = 0;
        for (Map.Entry<Unit, Plan> entry : gameState.getAssignedPlannedItems().entrySet()) {
            if (entry.getKey().getType() == producer && entry.getValue().getPlannedUnit() == unitType) {
                claimedProducers++;
            }
        }
        for (Plan plan : gameState.getPlansScheduled()) {
            if (plan.getType() == PlanType.UNIT
                    && plan.getPlannedUnit() == unitType
                    && !gameState.getAssignedPlannedItems().containsValue(plan)) {
                claimedProducers++;
            }
        }
        return gameState.ourLivingUnitCount(producer) > claimedProducers ? PlanBlocker.NONE : PlanBlocker.NO_PRODUCER;
    }

    private PlanBlocker advancedUnitBlocker(UnitType unitType) {
        return AdvancedUnitEligibility.blocker(unitType, gameState.getTechProgression(), gameState.numGatherers());
    }

    private boolean canScheduleBuilding(UnitType unitType) {
        TechProgression techProgression = gameState.getTechProgression();
        final int numHatcheries = gameState.getBaseData().numHatcheries();
        switch (unitType) {
            case Zerg_Hatchery:
            case Zerg_Extractor:
            case Zerg_Creep_Colony:
                return true;
            case Zerg_Spawning_Pool:
                return numHatcheries > 0;
            case Zerg_Hydralisk_Den:
            case Zerg_Sunken_Colony:
            case Zerg_Evolution_Chamber:
                return techProgression.isSpawningPool();
            case Zerg_Spore_Colony:
                return techProgression.getEvolutionChambers() > 0;
            case Zerg_Lair:
                return numHatcheries > 0 && techProgression.isSpawningPool();
            case Zerg_Spire:
            case Zerg_Queens_Nest:
                return techProgression.isLair();
            case Zerg_Hive:
                return techProgression.isLair() && techProgression.isQueensNest();
            case Zerg_Ultralisk_Cavern:
            case Zerg_Defiler_Mound:
                return techProgression.isHive();
            default:
                return false;
        }
    }

    private boolean canScheduleTech(TechType techType) {
        TechProgression techProgression = gameState.getTechProgression();
        switch (techType) {
            case Lurker_Aspect:
                return techProgression.isHydraliskDen();
            case Consume:
            case Plague:
                return techProgression.isDefilerMound();
            default:
                return false;
        }
    }

    private boolean canScheduleUpgrade(UpgradeType upgradeType) {
        TechProgression techProgression = gameState.getTechProgression();
        switch (upgradeType) {
            case Metabolic_Boost:
                return techProgression.isSpawningPool();
            case Muscular_Augments:
            case Grooved_Spines:
                return techProgression.isHydraliskDen();
            case Zerg_Carapace:
            case Zerg_Missile_Attacks:
            case Zerg_Melee_Attacks:
                return techProgression.getEvolutionChambers() > 0;
            case Zerg_Flyer_Attacks:
            case Zerg_Flyer_Carapace:
                return techProgression.isSpire();
            case Pneumatized_Carapace:
                return techProgression.isLair() || techProgression.isHive();
            case Chitinous_Plating:
            case Anabolic_Synthesis:
                return techProgression.isUltraliskCavern();
            case Adrenal_Glands:
                return techProgression.isSpawningPool() && techProgression.isHive();
            default:
                return false;
        }
    }

    private void schedulePlannedItems() {
        if (gameState.getProductionQueue().isEmpty()) {
            return;
        }

        reprioritizeHatcheriesForLarvaConstraint();

        List<Plan> schedulable = new ArrayList<>();
        int queueSize = gameState.getProductionQueue().size();
        for (int i = 0; i < queueSize; i++) {
            final Plan plan = gameState.getProductionQueue().poll();
            if (plan == null) {
                continue;
            }
            plan.markPlannedSince(currentFrame);
            if (ProductionQueue.isStale(plan, currentFrame)) {
                PlanEvents.stale(plan);
            }
            if (!canSchedulePlan(plan)) {
                plan.setCancelSource(PlanCancelSource.PRODUCTION_SCHEDULE_GATE);
                gameState.setImpossiblePlan(plan);
                continue;
            }
            schedulable.add(plan);
        }

        schedulingBatch = schedulable;
        ScanOutcome outcome = scanPlans(schedulable, gameState.getDroneRound().isActive(), this::schedulePlan);
        schedulingBatch = new ArrayList<>();
        gameState.getPlansScheduled().addAll(outcome.scheduled);
        gameState.getProductionQueue().addAll(outcome.requeued);
    }

    private PlanBlocker schedulePlan(Plan plan, boolean bankClaimedAhead, boolean larvaClaimedAhead,
                                     boolean researchClaimedAhead) {
        switch (plan.getType()) {
            case BUILDING:
                return scheduleBuildingItem(plan, bankClaimedAhead, researchClaimedAhead);
            case UNIT:
                return scheduleUnitItem(plan, bankClaimedAhead, larvaClaimedAhead, researchClaimedAhead);
            case UPGRADE:
                return scheduleUpgradeItem(game.self(), plan, researchClaimedAhead);
            case TECH:
                return scheduleResearch(plan, researchClaimedAhead);
            default:
                return PlanBlocker.UNSUPPORTED_PLAN_TYPE;
        }
    }

    @FunctionalInterface
    interface PlanScheduler {
        PlanBlocker schedule(Plan plan, boolean bankClaimedAhead, boolean larvaClaimedAhead,
                             boolean researchClaimedAhead);
    }

    static final class ScanOutcome {

        final List<Plan> scheduled = new ArrayList<>();

        final List<Plan> requeued = new ArrayList<>();
    }

    /** Scans every plan in priority order without stopping at blockers, with no drone round open. */
    static ScanOutcome scanPlans(List<Plan> plansInPriorityOrder, PlanScheduler scheduler) {
        return scanPlans(plansInPriorityOrder, false, scheduler);
    }

    /**
     * Scans every plan in priority order without stopping at blockers.
     *
     * @param plansInPriorityOrder the plans, highest priority first
     * @param droneRoundActive whether a {@link DroneRound} is open, which lifts the advanced unit larva claim
     * @param scheduler schedules one plan and reports what blocked it
     * @return the plans scheduled and the plans to requeue
     */
    static ScanOutcome scanPlans(List<Plan> plansInPriorityOrder, boolean droneRoundActive, PlanScheduler scheduler) {
        ScanOutcome outcome = new ScanOutcome();
        boolean bankClaimedAhead = false;
        boolean larvaClaimedAhead = false;
        boolean researchClaimedAhead = false;
        for (Plan plan : plansInPriorityOrder) {
            PlanBlocker blocker = scheduler.schedule(plan, bankClaimedAhead, larvaClaimedAhead, researchClaimedAhead);
            if (blocker == PlanBlocker.NONE) {
                outcome.scheduled.add(plan);
                continue;
            }
            PlanEvents.blocked(plan, blocker);
            outcome.requeued.add(plan);
            bankClaimedAhead = bankClaimedAhead || claimsBank(blocker);
            larvaClaimedAhead = larvaClaimedAhead || claimsLarva(plan, blocker, droneRoundActive);
            researchClaimedAhead = researchClaimedAhead || blocker == PlanBlocker.RESEARCH_MINERALS;
        }
        return outcome;
    }

    /**
     * The shortfall an upgrade or a research plan reports, and whether it holds the bank.
     *
     * <p>A plan short only of minerals, with a producer free to start it and income that covers the
     * shortfall within {@link BuildAheadSlot#MAX_HOLD_FRAMES}, holds the bank against every cheaper
     * plan behind it. Research reserves nothing while it is short, so without the hold each mineral
     * is spent by a Drone or Zergling that can already pay, and the research is funded only once
     * larva runs out. A plan also waiting on gas or on a busy producer would hold the bank for
     * nothing, and one whose shortfall outlasts the longest build-ahead hold would stall the drones
     * that gather it; those keep the plain shortfall claim.
     *
     * @param frame the current frame
     * @param predictedReadyFrame the frame the plan can pay its own cost
     * @param shortOnlyOfMinerals whether the unreserved bank covers the gas but not the minerals
     * @param producerFree whether a producer is idle and carries no plan
     * @return RESEARCH_MINERALS when the plan holds the bank, otherwise the plain shortfall blocker
     */
    static PlanBlocker researchShortfallBlocker(int frame, int predictedReadyFrame,
                                                boolean shortOnlyOfMinerals, boolean producerFree) {
        PlanBlocker shortfall = shortfallBlocker(predictedReadyFrame);
        if (shortfall != PlanBlocker.RESOURCES || !shortOnlyOfMinerals || !producerFree) {
            return shortfall;
        }
        if (predictedReadyFrame - frame > BuildAheadSlot.MAX_HOLD_FRAMES) {
            return PlanBlocker.RESOURCES;
        }
        return PlanBlocker.RESEARCH_MINERALS;
    }

    /**
     * True when a research or upgrade holding the bank ahead in the scan bars this plan.
     *
     * <p>Drones and Zerglings are held like every other plan: they are the larva spend that starves
     * research, and the hold lasts no longer than the income the research is waiting on. Overlords
     * are exempt so the hold can never become a supply block. Emergency defence is exempt, as it is
     * from a building holding the build-ahead slot, and so is a Sunken or Spore morph whose own
     * Creep Colony is complete, which has nothing left to wait for but its cost.
     *
     * @param plan the plan behind the claim
     * @param researchClaimedAhead whether a plan ahead in the scan reported RESEARCH_MINERALS
     * @param colonyReady whether the plan is a colony morph whose Creep Colony is complete
     * @return true when the plan must wait for the research to be funded
     */
    static boolean isHeldByResearchClaim(Plan plan, boolean researchClaimedAhead, boolean colonyReady) {
        if (!researchClaimedAhead || colonyReady || BuildAheadSlot.isEmergencyDefence(plan)) {
            return false;
        }
        return plan.getPlannedUnit() != UnitType.Zerg_Overlord;
    }

    /**
     * Separates a shortfall that income will cover from one that no worker is gathering for.
     *
     * <p>An upgrade or a research plan reports its own affordability, unlike a building or a unit,
     * whose build-ahead gates already convert an unreachable projection to NO_INCOME. Reporting
     * RESOURCES for a cost nobody is mining hands the plan the bank through claimsBank, and it
     * holds it against the Extractor that would have started the income.
     *
     * @param predictedReadyFrame the frame the plan can pay its own cost
     * @return RESOURCES while the shortfall can still be gathered, otherwise NO_INCOME
     */
    static PlanBlocker shortfallBlocker(int predictedReadyFrame) {
        return BuildAheadSlot.isUnreachable(predictedReadyFrame) ? PlanBlocker.NO_INCOME : PlanBlocker.RESOURCES;
    }

    static boolean claimsBank(PlanBlocker blocker) {
        return blocker == PlanBlocker.RESOURCES || blocker == PlanBlocker.RESEARCH_MINERALS;
    }

    /**
     * True when a blocked larva morph claims the next free larva against every plan behind it.
     *
     * <p>A plan waiting on larva, on supply, or on a bank another plan is holding will take a larva
     * as soon as that one wait clears. Letting a later plan spend the larva meanwhile hands the
     * higher-priority plan a fresh larva wait on top of the one it had. A plan short of its own cost
     * by more than a build cycle, with no income for it, or in eviction backoff is not about to use
     * a larva, and holding one for it would only idle larva production.
     *
     * <p>While a {@link DroneRound} is open an advanced unit plan claims nothing, so the Drones
     * queued behind it take the larva it is waiting on. Scourge keeps its claim, as the round never
     * withholds it either.
     *
     * @param plan the blocked plan
     * @param blocker the gate it failed this scan
     * @param droneRoundActive whether a drone round is open
     * @return true when later larva morphs must leave the larva to this plan
     */
    static boolean claimsLarva(Plan plan, PlanBlocker blocker, boolean droneRoundActive) {
        if (plan.getType() != PlanType.UNIT || !isLarvaMorph(plan.getPlannedUnit())) {
            return false;
        }
        if (droneRoundActive && plan.getPriority() == UnitPlan.ADVANCED_UNIT_PRIORITY
                && plan.getPlannedUnit() != UnitType.Zerg_Scourge) {
            return false;
        }
        return blocker == PlanBlocker.NO_LARVA
                || blocker == PlanBlocker.SUPPLY
                || blocker == PlanBlocker.BUILD_AHEAD_SLOT_TAKEN;
    }

    // TODO: Refactor this into WorkerManager or a Buildingmanager (TechManager)?
    // These PlannedItems will not work through state machine in same way as Unit and Buildings
    // This is a bit of a HACK until properly maintained
    private void buildUpgrades() {
        HashSet<Plan> scheduledPlans = gameState.getPlansScheduled();
        if (scheduledPlans.isEmpty()) {
            return;
        }

        HashSet<Unit> unitsExecutingPlan = new HashSet<>();
        List<Map.Entry<Unit, Plan>> scheduledUpgradeAssignments = gameState.getAssignedPlannedItems().entrySet()
                .stream()
                .filter(assignment -> assignment.getValue().getType() == PlanType.UPGRADE)
                .collect(Collectors.toList());

        // TODO: Move to BuildingManager or PlanManager
        for (Map.Entry<Unit, Plan> entry: scheduledUpgradeAssignments) {
            final Unit unit = entry.getKey();
            final Plan plan = entry.getValue();
            if (buildUpgrade(unit, plan)) {
                unitsExecutingPlan.add(unit);
                scheduledPlans.remove(plan);
                plan.setState(PlanState.BUILDING);
                gameState.getPlansBuilding().add(plan);
            }
        }

        // Remove executing plans from gameState.getAssignedPlannedItems()
        for (Unit u : unitsExecutingPlan) {
            gameState.getAssignedPlannedItems().remove(u);
        }
    }

    private void researchTech() {
        HashSet<Plan> scheduledPlans = gameState.getPlansScheduled();
        if (scheduledPlans.isEmpty()) {
            return;
        }

        HashSet<Unit> unitsExecutingPlan = new HashSet<>();
        List<Map.Entry<Unit, Plan>> scheduledTechResearch = gameState.getAssignedPlannedItems().entrySet()
                .stream()
                .filter(assignment -> assignment.getValue().getType() == PlanType.TECH)
                .collect(Collectors.toList());

        for (Map.Entry<Unit, Plan> entry: scheduledTechResearch) {
            final Unit unit = entry.getKey();
            final Plan plan = entry.getValue();
            // TODO: Move to BuildingManager or PlanManager
            if (researchTech(unit, plan)) {
                unitsExecutingPlan.add(unit);
                scheduledPlans.remove(plan);
                plan.setState(PlanState.BUILDING);
                gameState.getPlansBuilding().add(plan);
            }
        }

        // Remove executing plans from gameState.getAssignedPlannedItems()
        for (Unit u : unitsExecutingPlan) {
            gameState.getAssignedPlannedItems().remove(u);
        }
    }

    // Track planned items that are morphing
    // BUILD -> MORPH
    // Buildings and units
    // TODO: Move to info package
    private void plannedItemToMorphing(Plan plan) {
        final UnitType unitType = plan.getPlannedUnit();
        ResourceCount resourceCount = gameState.getResourceCount();
        resourceCount.unreserveUnit(unitType);

        if (unitType == UnitType.Zerg_Drone) {
            gameState.removePlannedWorker(1);
        }

        if (unitType.isBuilding()) {
            buildAheadSlot.release(plan);
        }

        gameState.getPlansBuilding().remove(plan);
        plan.setState(PlanState.MORPHING);
        gameState.getPlansMorphing().add(plan);
    }

    // TODO: Handle in BaseManager (ManagedUnits that are buildings. ManagedBuilding?)
    private boolean buildUpgrade(Unit unit, Plan plan) {
        final UpgradeType upgradeType = plan.getPlannedUpgrade();
        if (game.canUpgrade(upgradeType, unit)) {
            unit.upgrade(upgradeType);
        }

        if (unit.isUpgrading()) {
            gameState.getResourceCount().unreserveUpgrade(plan);
            return true;
        }
        return false;
    }

    private boolean researchTech(Unit unit, Plan plan) {
        final TechType techType = plan.getPlannedTechType();
        if (game.canResearch(techType, unit)) {
            unit.research(techType);
        }

        if (unit.isResearching()) {
            gameState.getResourceCount().unreserveTechResearch(techType);
            return true;
        }
        return false;
    }

    // PLANNED -> SCHEDULED
    // Allow one building to be scheduled if resources aren't available, unless in an opener
    private PlanBlocker scheduleBuildingItem(Plan plan, boolean hasHigherPriorityPending,
                                             boolean researchClaimedAhead) {
        UnitType building = plan.getPlannedUnit();

        boolean colonyReady = false;
        if (ColonyClaims.isColonyMorph(building)) {
            PlanBlocker colonyBlocker = resolveColonyMorph(plan);
            if (colonyBlocker != PlanBlocker.NONE) {
                return colonyBlocker;
            }
            colonyReady = true;
        }

        PlanBlocker producerBlocker = buildingMorphBlocker(building, hasFreeMorphProducer(building));
        if (producerBlocker != PlanBlocker.NONE) {
            return producerBlocker;
        }

        PlanBlocker waveBlocker = techWaveBlocker(plan);
        if (waveBlocker != PlanBlocker.NONE) {
            return waveBlocker;
        }

        if (isHeldByResearchClaim(plan, researchClaimedAhead, colonyReady)) {
            return PlanBlocker.RESEARCH_CLAIM;
        }

        ResourceCount resourceCount = gameState.getResourceCount();
        boolean cannotAfford = resourceCount.cannotAffordUnit(building);
        int predictedReadyFrame = gameState.frameCanAffordUnit(building, currentFrame);
        PlanBlocker buildAheadBlocker = buildAheadBlocker(
                buildAheadSlot,
                plan,
                currentFrame,
                cannotAfford,
                hasHigherPriorityPending,
                predictedReadyFrame,
                colonyReady);
        if (buildAheadBlocker != PlanBlocker.NONE) {
            return buildAheadBlocker;
        }

        if (!resolveBuildPosition(plan, building)) {
            return PlanBlocker.NO_BUILD_POSITION;
        }

        int travelFrames = builderTravelFrames(plan);
        if (BuildAheadSlot.dispatchOutlastsHold(currentFrame, predictedReadyFrame, travelFrames)) {
            return PlanBlocker.BUILD_AHEAD_TOO_FAR;
        }

        List<Plan> yieldingHolders = cannotAfford
                ? buildAheadSlot.holdersYieldingTo(plan, colonyReady)
                : new ArrayList<>();
        if (!yieldingHolders.isEmpty()) {
            yieldingHolders.forEach(holder -> yieldBuildAheadHold(holder, plan));
            predictedReadyFrame = gameState.frameCanAffordUnit(building, currentFrame);
        }

        buildAheadSlot.claim(plan, currentFrame, predictedReadyFrame, travelFrames);
        resourceCount.reserveUnit(building);
        plan.setPredictedReadyFrame(predictedReadyFrame);
        plan.setState(PlanState.SCHEDULE);
        return PlanBlocker.NONE;
    }

    /**
     * Blocks a building morph while BuildingManager has no producer free to take it.
     *
     * <p>A morph from another building has no drone to walk, so nothing downstream notices the
     * missing producer. The plan takes the build-ahead slot, holds its reservation for the whole
     * minimum hold, is evicted without ever having been assigned, and claims again on the next
     * scan. Refusing it before the claim leaves it in the queue at no cost until a producer frees
     * up.
     *
     * @param building the planned morph, for example a Lair
     * @param freeProducerAvailable whether a completed producer of its type carries no plan yet
     * @return NONE while the morph can be handed to a producer, otherwise NO_PRODUCER
     */
    static PlanBlocker buildingMorphBlocker(UnitType building, boolean freeProducerAvailable) {
        if (building.whatBuilds().getFirst() == UnitType.Zerg_Drone) {
            return PlanBlocker.NONE;
        }
        return freeProducerAvailable ? PlanBlocker.NONE : PlanBlocker.NO_PRODUCER;
    }

    /** Matches what BuildingManager looks for: a completed producer that carries no plan yet. */
    private boolean hasFreeMorphProducer(UnitType building) {
        UnitType producer = building.whatBuilds().getFirst();
        if (producer == UnitType.Zerg_Drone) {
            return true;
        }
        for (Unit unit : gameState.getSelf().getUnits()) {
            if (unit.getType() == producer
                    && unit.isCompleted()
                    && !gameState.getAssignedPlannedItems().containsKey(unit)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The walk the eviction deadline has to cover.
     *
     * <p>Measured on the assigned builder once PlanManager has handed the plan one, which is the
     * same unit and the same formula the dispatch gate reads, and on the worker closest to the tile
     * before then. A building that morphs from another building has no walk, and earns no extra
     * hold.
     */
    private int builderTravelFrames(Plan plan) {
        UnitType building = plan.getPlannedUnit();
        TilePosition buildPosition = plan.getBuildPosition();
        if (buildPosition == null || building.whatBuilds().getFirst() != UnitType.Zerg_Drone) {
            return 0;
        }

        Position target = buildPosition.toPosition();
        Unit executor = executorOf(plan);
        if (executor != null) {
            return TravelTime.framesToReach(executor, target);
        }

        int travelFrames = 0;
        double closest = Double.MAX_VALUE;
        for (ManagedUnit worker : gameState.getAssignedManagedWorkers()) {
            Unit unit = worker.getUnit();
            double distance = target.getDistance(unit.getPosition());
            if (distance < closest) {
                closest = distance;
                travelFrames = TravelTime.framesToReach(unit, target);
            }
        }
        return travelFrames;
    }

    /**
     * Why a building plan cannot take the build-ahead slot this frame.
     *
     * <p>Affordability is answered first: an eviction bars a plan from a fresh hold, never from
     * being scheduled with minerals it can already pay for. A plan that can pay resumes the hold it
     * was evicted from, so it waits out its backoff only once that hold has run out.
     *
     * <p>An occupied slot does not bar emergency defence from holders queued below it, nor a
     * colony morph whose Creep Colony is complete from holders still in SCHEDULE; the caller takes
     * the slot from them with {@link BuildAheadSlot#holdersYieldingTo}.
     */
    static PlanBlocker buildAheadBlocker(
            BuildAheadSlot slot,
            Plan plan,
            int frame,
            boolean cannotAfford,
            boolean hasHigherPriorityPending,
            int predictedReadyFrame) {
        return buildAheadBlocker(slot, plan, frame, cannotAfford, hasHigherPriorityPending, predictedReadyFrame, false);
    }

    static PlanBlocker buildAheadBlocker(
            BuildAheadSlot slot,
            Plan plan,
            int frame,
            boolean cannotAfford,
            boolean hasHigherPriorityPending,
            int predictedReadyFrame,
            boolean colonyReady) {
        if (!cannotAfford) {
            return slot.isHoldSpent(plan, frame) ? PlanBlocker.BUILD_AHEAD_BACKOFF : PlanBlocker.NONE;
        }
        if (hasHigherPriorityPending) {
            return PlanBlocker.BUILD_AHEAD_SLOT_TAKEN;
        }
        if (slot.isOccupied() && slot.holdersYieldingTo(plan, colonyReady).isEmpty()) {
            return PlanBlocker.BUILD_AHEAD_SLOT_TAKEN;
        }
        if (slot.isInBackoff(plan, frame)) {
            return PlanBlocker.BUILD_AHEAD_BACKOFF;
        }
        if (BuildAheadSlot.isUnreachable(predictedReadyFrame)) {
            return PlanBlocker.NO_INCOME;
        }
        return PlanBlocker.NONE;
    }

    /** A plan with nowhere to build must not reserve its cost. */
    private boolean resolveBuildPosition(Plan plan, UnitType building) {
        if (plan.getBuildPosition() != null) {
            return true;
        }

        Base mainBase = gameState.getBaseData().getMainBase();
        if (mainBase == null) {
            return false;
        }

        BuildingPlanner buildingPlanner = gameState.getBuildingPlanner();
        TilePosition buildPosition = buildingPlanner.getLocationForBuilding(mainBase, building);
        if (buildPosition == null) {
            return false;
        }

        if (!gameState.getGameMap().isValidTile(buildPosition)) {
            buildingPlanner.unreservePlannedBuildingTiles(buildPosition, building);
            return false;
        }

        plan.setBuildPosition(buildPosition);
        return true;
    }

    /**
     * Points a Sunken or Spore plan at the creep colony its pair is building. The plan morphs that
     * colony and nothing else for as long as the pair can deliver one, waiting while the colony is
     * queued, under construction or not yet complete. Only a broken pair adopts another colony, and
     * then only one no other morph plan is claiming.
     */
    private PlanBlocker resolveColonyMorph(Plan plan) {
        TilePosition claim = plan.claimedColonyTile();
        if (claim != null) {
            plan.setBuildPosition(claim);
        }

        Unit pairedColony = gameState.creepColonyAt(claim);
        if (!ColonyClaims.mayAdoptAnotherColony(plan, pairedColony != null)) {
            if (pairedColony != null && pairedColony.isCompleted()) {
                return PlanBlocker.NONE;
            }
            return PlanBlocker.NO_CREEP_COLONY;
        }

        Unit adoptedColony = findAdoptableCreepColony(plan);
        if (adoptedColony == null) {
            return PlanBlocker.NO_CREEP_COLONY;
        }

        plan.setPairedColonyPlan(null);
        plan.setBuildPosition(adoptedColony.getTilePosition());
        return PlanBlocker.NONE;
    }

    /** A completed creep colony no plan is executing on and no other morph plan is waiting for. */
    private Unit findAdoptableCreepColony(Plan plan) {
        Map<TilePosition, Plan> claims = ColonyClaims.collect(
                schedulingBatch,
                gameState.getProductionQueue(),
                gameState.getPlansScheduled(),
                gameState.getPlansBuilding());

        for (Unit unit : gameState.getSelf().getUnits()) {
            if (unit.getType() != UnitType.Zerg_Creep_Colony || !unit.isCompleted()) {
                continue;
            }
            if (gameState.getAssignedPlannedItems().containsKey(unit)) {
                continue;
            }
            if (ColonyClaims.isClaimedByOther(claims, unit.getTilePosition(), plan)) {
                continue;
            }
            return unit;
        }
        return null;
    }

    private PlanBlocker scheduleUnitItem(Plan plan, boolean bankClaimedAhead, boolean larvaClaimedAhead,
                                         boolean researchClaimedAhead) {
        UnitType unit = plan.getPlannedUnit();
        ResourceCount resourceCount = gameState.getResourceCount();
        boolean larvaAvailable = resourceCount.canScheduleLarva(gameState.numLarva(), gameState.larvaAssignedToPlans());
        if (isLarvaBlocked(unit, larvaAvailable, larvaClaimedAhead)) {
            return PlanBlocker.NO_LARVA;
        }

        PlanBlocker waveBlocker = techWaveBlocker(plan);
        if (waveBlocker != PlanBlocker.NONE) {
            return waveBlocker;
        }

        Player self = gameState.getSelf();
        if (isSupplyBlocked(unit, self.supplyTotal() - self.supplyUsed())) {
            return PlanBlocker.SUPPLY;
        }

        if (isHeldByResearchClaim(plan, researchClaimedAhead, false)) {
            return PlanBlocker.RESEARCH_CLAIM;
        }

        boolean cannotAfford = resourceCount.cannotAffordUnit(unit);
        int predictedReadyFrame = gameState.frameCanAffordUnit(unit, currentFrame);
        PlanBlocker unitAheadBlocker = unitAheadBlocker(
                unitAheadSlot,
                plan,
                currentFrame,
                cannotAfford,
                bankClaimedAhead,
                cannotAfford && isBarredByBuildingReservation(plan, self),
                predictedReadyFrame);
        if (unitAheadBlocker != PlanBlocker.NONE) {
            return unitAheadBlocker;
        }

        if (cannotAfford) {
            unitAheadSlot.claim(plan, currentFrame, predictedReadyFrame);
        }
        resourceCount.reserveUnit(unit);
        plan.setState(PlanState.SCHEDULE);
        return PlanBlocker.NONE;
    }

    /**
     * True when a larva morph cannot start for lack of free larva, or because a blocked plan ahead
     * of it in the scan has claimed the larva.
     *
     * Units that morph from an existing unit consume no larva, so only larva morphs are gated.
     * Overlords are exempt from the claim, so a plan waiting on supply cannot hold back the Overlord
     * that gives it supply.
     *
     * @param unit the planned unit
     * @param larvaAvailable whether unreserved larva is on hand
     * @param larvaClaimedAhead whether a higher-priority larva morph blocked this scan claims the larva
     * @return true when the morph cannot be issued yet
     */
    static boolean isLarvaBlocked(UnitType unit, boolean larvaAvailable, boolean larvaClaimedAhead) {
        if (!isLarvaMorph(unit)) {
            return false;
        }
        if (!larvaAvailable) {
            return true;
        }
        return larvaClaimedAhead && unit != UnitType.Zerg_Overlord;
    }

    private static boolean isLarvaMorph(UnitType unit) {
        return unit.whatBuilds().getKey() == UnitType.Zerg_Larva;
    }

    /**
     * True when a larva morph cannot start for lack of free supply.
     *
     * Overlords are exempt; blocking them is what turns a supply block into a deadlock. Units that
     * morph from an existing unit carry their own supply across the morph, so only larva morphs
     * are gated. Zerglings and scourge hatch in pairs and cost supply for both.
     *
     * @param unit the planned unit
     * @param freeSupply raw supply total less raw supply used
     * @return true when the morph cannot be issued yet
     */
    static boolean isSupplyBlocked(UnitType unit, int freeSupply) {
        if (unit == UnitType.Zerg_Overlord) {
            return false;
        }
        if (unit.whatBuilds().getKey() != UnitType.Zerg_Larva) {
            return false;
        }
        return freeSupply < SupplyCapacity.morphSupplyCost(unit);
    }

    /**
     * True when a building already holding the bank bars this unit from spending against it.
     *
     * <p>An Overlord is exempt only while a supply block is imminent: supply headroom, counting
     * Overlords already in an egg, is under {@link #SUPPLY_BUFFER}. Holding supply down then stops
     * the drones that gather the income funding the building. With more headroom the Overlord
     * waits like any other unit.
     *
     * <p>An emergency Creep or Sunken Colony is always exempt. An emergency Zergling is exempt only
     * while fewer than {@link Reactions#EARLY_RUSH_SAFE_ZERGLINGS} zerglings are alive; past that
     * floor a rush that never ends would otherwise spend every mineral the building reserved.
     *
     * @param plan the planned unit
     * @param buildingHoldsBank whether a building plan holds the build-ahead slot
     * @param livingZerglings zerglings that have hatched
     * @param supplyHeadroom raw free supply plus the supply Overlords in an egg will add
     * @return true when the unit must wait for the building to be funded
     */
    static boolean isBarredByBuildingReservation(Plan plan, boolean buildingHoldsBank, int livingZerglings,
                                                 int supplyHeadroom) {
        if (!buildingHoldsBank) {
            return false;
        }
        UnitType unit = plan.getPlannedUnit();
        if (unit == UnitType.Zerg_Overlord) {
            return supplyHeadroom >= SUPPLY_BUFFER;
        }
        if (!BuildAheadSlot.isEmergencyDefence(plan)) {
            return true;
        }
        return unit == UnitType.Zerg_Zergling && livingZerglings >= Reactions.EARLY_RUSH_SAFE_ZERGLINGS;
    }

    /**
     * Raw free supply plus the supply every Overlord still in an egg will add when it hatches.
     */
    private static int supplyHeadroom(Player self) {
        int hatching = 0;
        for (Unit unit : self.getUnits()) {
            if (unit.getType() == UnitType.Zerg_Egg && unit.getBuildType() == UnitType.Zerg_Overlord) {
                hatching += OVERLORD_SUPPLY;
            }
        }
        return self.supplyTotal() - self.supplyUsed() + hatching;
    }

    private boolean isBarredByBuildingReservation(Plan plan, Player self) {
        if (!buildAheadSlot.isOccupied()) {
            return false;
        }
        int headroom = plan.getPlannedUnit() == UnitType.Zerg_Overlord ? supplyHeadroom(self) : SUPPLY_BUFFER;
        return isBarredByBuildingReservation(
                plan, true, gameState.ourLivingUnitCount(UnitType.Zerg_Zergling), headroom);
    }

    /**
     * Why a unit plan cannot be scheduled against a bank it cannot yet cover.
     *
     * <p>A building holding the build-ahead slot has reserved its cost out of the same bank, so
     * its hold bars unit plans exactly as a resource-blocked plan ahead of them in the scan does,
     * unless {@link #isBarredByBuildingReservation(Plan, boolean, int, int)} exempts the plan.
     */
    static PlanBlocker unitAheadBlocker(
            BuildAheadSlot slot,
            Plan plan,
            int frame,
            boolean cannotAfford,
            boolean bankClaimedAhead,
            boolean barredByBuildingReservation,
            int predictedReadyFrame) {
        if (!cannotAfford) {
            return PlanBlocker.NONE;
        }
        if (bankClaimedAhead || slot.isOccupied()) {
            return PlanBlocker.BUILD_AHEAD_SLOT_TAKEN;
        }
        if (barredByBuildingReservation) {
            return PlanBlocker.BUILD_AHEAD_SLOT_TAKEN;
        }
        if (slot.isInBackoff(plan, frame)) {
            return PlanBlocker.BUILD_AHEAD_BACKOFF;
        }
        if (BuildAheadSlot.isUnreachable(predictedReadyFrame)) {
            return PlanBlocker.NO_INCOME;
        }
        if (predictedReadyFrame - frame > plan.getPlannedUnit().buildTime()) {
            return PlanBlocker.RESOURCES;
        }
        return PlanBlocker.NONE;
    }

    private PlanBlocker scheduleUpgradeItem(Player self, Plan plan, boolean researchClaimedAhead) {
        final UpgradeType upgrade = plan.getPlannedUpgrade();
        ResourceCount resourceCount = gameState.getResourceCount();

        PlanBlocker waveBlocker = techWaveBlocker(plan);
        if (waveBlocker != PlanBlocker.NONE) {
            return waveBlocker;
        }

        if (isHeldByResearchClaim(plan, researchClaimedAhead, false)) {
            return PlanBlocker.RESEARCH_CLAIM;
        }

        if (resourceCount.cannotAffordUpgrade(plan)) {
            return researchShortfall(plan, hasFreeResearcher(upgrade.whatUpgrades()));
        }

        Unit nextAvailable = null;
        for (Unit unit : self.getUnits()) {
            UnitType unitType = unit.getType();

            if (unitType != upgrade.whatUpgrades() && !isUpgradedForm(unitType, upgrade.whatUpgrades())) {
                continue;
            }

            if (nextAvailable == null) {
                nextAvailable = unit;
            }

            // TODO: Evo chamber already upgrading passes this check
            // Needs to be unavailable until upgrade completes
            if (!unit.isUpgrading() && !gameState.getAssignedPlannedItems().containsKey(unit)) {
                gameState.getAssignedPlannedItems().put(unit, plan);
                plan.setState(PlanState.SCHEDULE);
                resourceCount.reserveUpgrade(plan);
                return PlanBlocker.NONE;
            }

            // If no assignment, see if this unit will be available before other buildings
            if (unit.getRemainingUpgradeTime() > nextAvailable.getRemainingUpgradeTime()) {
                nextAvailable = unit;
            }
        }

        if (nextAvailable != null) {
            int priority = plan.getPriority();
            plan.setPriority(priority + nextAvailable.getRemainingUpgradeTime());
        }

        return PlanBlocker.NO_PRODUCER;
    }

    private boolean isUpgradedForm(UnitType actual, UnitType required) {
        return required == UnitType.Zerg_Lair && actual == UnitType.Zerg_Hive
                || required == UnitType.Zerg_Spire && actual == UnitType.Zerg_Greater_Spire;
    }

    private PlanBlocker researchShortfall(Plan plan, boolean producerFree) {
        return researchShortfallBlocker(
                currentFrame,
                gameState.frameCanAffordPlan(plan, currentFrame),
                gameState.getResourceCount().isShortOnlyOfMinerals(plan),
                producerFree);
    }

    /** A completed producer, or its upgraded form, that is not researching and carries no plan. */
    private boolean hasFreeResearcher(UnitType producer) {
        for (Unit unit : gameState.getSelf().getUnits()) {
            UnitType unitType = unit.getType();
            if (unitType != producer && !isUpgradedForm(unitType, producer)) {
                continue;
            }
            if (unit.isCompleted()
                    && !unit.isUpgrading()
                    && !unit.isResearching()
                    && !gameState.getAssignedPlannedItems().containsKey(unit)) {
                return true;
            }
        }
        return false;
    }

    private PlanBlocker scheduleResearch(Plan plan, boolean researchClaimedAhead) {
        final TechType techType = plan.getPlannedTechType();
        ResourceCount resourceCount = gameState.getResourceCount();

        PlanBlocker waveBlocker = techWaveBlocker(plan);
        if (waveBlocker != PlanBlocker.NONE) {
            return waveBlocker;
        }

        if (isHeldByResearchClaim(plan, researchClaimedAhead, false)) {
            return PlanBlocker.RESEARCH_CLAIM;
        }

        if (resourceCount.cannotAffordResearch(techType)) {
            return researchShortfall(plan, hasFreeResearcher(techType.whatResearches()));
        }

        Unit nextAvailable = null;
        for (Unit unit : game.self().getUnits()) {
            UnitType unitType = unit.getType();

            if (unitType != techType.whatResearches()) {
                continue;
            }

            if (nextAvailable == null) {
                nextAvailable = unit;
            }

            // Needs to be unavailable until upgrade completes
            if (!unit.isUpgrading() && !gameState.getAssignedPlannedItems().containsKey(unit)) {
                gameState.getAssignedPlannedItems().put(unit, plan);
                plan.setState(PlanState.SCHEDULE);
                resourceCount.reserveTechResearch(techType);
                return PlanBlocker.NONE;
            }

            // If no assignment, see if this unit will be available before other buildings
            if (unit.getRemainingUpgradeTime() > nextAvailable.getRemainingUpgradeTime()) {
                nextAvailable = unit;
            }
        }

        if (nextAvailable != null) {
            int priority = plan.getPriority();
            plan.setPriority(priority + nextAvailable.getRemainingUpgradeTime());
        }

        return PlanBlocker.NO_PRODUCER;
    }

    /**
     * Identifies and handles the larva/hatchery constraint scenario:
     * - Larva count is zero
     * - Hatchery is in the production queue
     * - There are enough minerals to build a hatchery
     *
     * If this scenario is detected, finds the highest priority hatchery in the queue
     * and sets its priority to put it at the top of the queue.
     *
     * <p>Reads the mined bank rather than the available one. A hatchery already holding its own
     * 300-mineral reservation would otherwise suppress the rule that exists to rescue it.
     */
    private void reprioritizeHatcheriesForLarvaConstraint() {
        Plan target = larvaConstraintHatchery(gameState.numLarva(),
                gameState.getResourceCount().minedMinerals(), gameState.getProductionQueue());
        if (target != null) {
            gameState.getProductionQueue().setPriorityWhere(plan -> plan == target, 0);
        }
    }

    /**
     * The queued hatchery the larva constraint rule promotes to priority 0.
     *
     * <p>The rule only re-prioritises. It never creates a hatchery plan and never changes the
     * chosen plan's tile or macro hatchery flag, so an expansion stays an expansion. The main
     * macro hatchery a larva-bound build needs is requested by the build order itself.
     *
     * @param larva larva not yet handed to a plan
     * @param minedMinerals minerals mined and unspent, before any reservation
     * @param queue the production queue
     * @return the highest-priority queued hatchery, or null when larva is free, the bank cannot
     *     buy a hatchery, none is queued, or the best one already sits at priority 0
     */
    static Plan larvaConstraintHatchery(int larva, int minedMinerals, Iterable<Plan> queue) {
        if (larva > 0 || minedMinerals < HATCHERY_MINERAL_PRICE) {
            return null;
        }

        Plan priorityHatcheryPlan = null;
        int highestPriority = Integer.MAX_VALUE;
        for (Plan plan : queue) {
            if (plan.getType() == PlanType.BUILDING
                    && plan.getPlannedUnit() == UnitType.Zerg_Hatchery
                    && plan.getPriority() < highestPriority) {
                highestPriority = plan.getPriority();
                priorityHatcheryPlan = plan;
            }
        }

        return highestPriority > 0 ? priorityHatcheryPlan : null;
    }

    // Need to handle cancel case (building about to die, extractor trick, etc.)
    public void onUnitMorph(Unit unit) {
        HashMap<Unit, Plan> assignedPlannedItems = gameState.getAssignedPlannedItems();
        if (assignedPlannedItems.containsKey(unit)) {
            Plan plan = gameState.getAssignedPlannedItems().get(unit);
            plannedItemToMorphing(plan);
        }

        clearAssignments(unit, false);
    }

    public void onUnitRenegade(Unit unit) {
        Player self = game.self();
        if (unit.getPlayer() != self) {
            return;
        }

        final UnitType unitType = unit.getType();

        if (unitType == UnitType.Zerg_Extractor) {
            ResourceCount resourceCount = gameState.getResourceCount();
            resourceCount.unreserveUnit(unitType);
            buildAheadSlot.releaseFirst(unitType);
        }
    }

    public void onUnitDestroy(Unit unit) {
        Player self = game.self();
        if (unit.getPlayer() != self) {
            return;
        }
        clearAssignments(unit, true);
    }

    /**
     * Remove a unit from all data stores
     *
     * @param unit unit to remove
     */
    private void clearAssignments(Unit unit, boolean isDestroyed) {
        // Requeue PlannedItems
        // Put item back onto the queue with greater importance
        if (gameState.getAssignedPlannedItems().containsKey(unit)) {
            Plan plan = gameState.getAssignedPlannedItems().get(unit);
            switch (plan.getState()) {
                case BUILDING:
                case MORPHING:
                    if (isDestroyed) {
                        if (plan.getPlannedUnit() == UnitType.Zerg_Extractor
                                && plan.getBuildPosition() != null
                                && gameState.getBaseData().isExtractorAtPosition(plan.getBuildPosition())) {
                            gameState.completePlan(unit, plan);
                        } else {
                            if (plan.getState() == PlanState.BUILDING
                                    && plan.getType() == PlanType.BUILDING) {
                                buildAheadSlot.releaseWithBackoff(plan, currentFrame);
                            }
                            gameState.cancelPlan(unit, plan, PlanCancelSource.PRODUCTION_EXECUTOR_LOST);
                        }
                    } else {
                        gameState.completePlan(unit, plan);
                    }
                    break;
                case SCHEDULE:
                    if (plan.getType() == PlanType.BUILDING) {
                        buildAheadSlot.releaseWithBackoff(plan, currentFrame);
                    }
                    gameState.cancelPlan(unit, plan, PlanCancelSource.PRODUCTION_EXECUTOR_LOST);
                    break;
                default:
                    gameState.completePlan(unit, plan);
                    break;
            }
        }
    }

    /**
     * How many scheduled Lurker plans have no producer left to morph them.
     *
     * @param scheduledPlans scheduled Lurker plans
     * @param hydralisks living hydralisks, the producer type
     * @param lurkerEggs hydralisks already morphing, which count as producers already spoken for
     * @return plans to cancel, never negative
     */
    static int excessLurkerPlans(int scheduledPlans, int hydralisks, int lurkerEggs) {
        return Math.max(0, scheduledPlans - hydralisks - lurkerEggs);
    }

    /**
     * Retires the scheduled Lurker plans that have no hydralisk left to morph, and only those.
     * Hydralisks already morphing count as producers: the morph turns the unit into a Lurker Egg,
     * which is neither a hydralisk nor a Lurker.
     */
    private void cancelImpossibleScheduledLurkerPlans() {
        List<Plan> lurkerPlans = gameState.getPlansScheduled().stream()
                .filter(plan -> plan.getType() == PlanType.UNIT && plan.getPlannedUnit() == UnitType.Zerg_Lurker)
                .sorted(Comparator.comparingInt(Plan::getPriority).reversed())
                .collect(Collectors.toList());
        int excess = excessLurkerPlans(
                lurkerPlans.size(),
                gameState.getUnitTypeCount().livingCount(UnitType.Zerg_Hydralisk),
                gameState.getUnitTypeCount().livingCount(UnitType.Zerg_Lurker_Egg));

        for (Plan plan : lurkerPlans) {
            if (excess <= 0) {
                break;
            }
            gameState.getPlansScheduled().remove(plan);
            gameState.cancelPlan(null, plan, PlanCancelSource.PRODUCTION_SCHEDULED_LURKER);
            excess -= 1;
        }
    }
}
