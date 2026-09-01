package macro;

import bwapi.Game;
import bwapi.Player;
import bwapi.TechType;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.UpgradeType;
import bwem.Base;
import info.GameState;
import info.ResourceCount;
import info.TechProgression;
import info.UnitTypeCount;
import info.map.BuildingPlanner;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages the production of units, buildings, upgrades and research.
 * <p>
 * The Bot's BuildOrder is responsible for deciding what units should be queued; the exact unit
 * is currently determined probabilistically by UnitWeights.
 */
public class ProductionManager {

    private Game game;

    private GameState gameState;

    // isPlanning contingent on -> hitting min supply set by build order OR queue exhaust
    private boolean isPlanning = false;

    private final BuildAheadSlot buildAheadSlot = new BuildAheadSlot();

    private final BuildAheadSlot unitAheadSlot = new BuildAheadSlot();

    private int currentFrame = 5;


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

    private void cancelImpossiblePlans() {
        gameState.getProductionQueue().removeWhere(
                plan -> !canSchedulePlan(plan),
                PlanCancelSource.PRODUCTION_IMPOSSIBLE_SWEEP,
                gameState::setImpossiblePlan);

        cancelImpossibleScheduledLurkerPlans();
        removePlansWithLaterPrerequisites();
    }

    private void cancelExcessHatcheryPlans() {
        if (!gameState.hasExcessHatchery()) {
            return;
        }

        gameState.getProductionQueue().removeWhere(
                p -> p.getType() == PlanType.BUILDING && p.getPlannedUnit() == UnitType.Zerg_Hatchery,
                PlanCancelSource.PRODUCTION_EXCESS_HATCHERY_QUEUED,
                gameState::setImpossiblePlan);

        Set<Plan> scheduledPlansToCancel = gameState.getPlansScheduled()
                .stream()
                .filter(plan -> plan.getType() == PlanType.BUILDING && plan.getPlannedUnit() == UnitType.Zerg_Hatchery)
                .collect(Collectors.toSet());

        for (Plan plan : scheduledPlansToCancel) {
            buildAheadSlot.release(plan);
            gameState.getPlansScheduled().remove(plan);
            gameState.cancelPlan(null, plan, PlanCancelSource.PRODUCTION_EXCESS_HATCHERY_SCHEDULED);
        }
    }

    /** Drops scheduled Lair plans while an early rush delays the Lair; the reaction removes only queued ones. */
    private void cancelDelayedLairPlans() {
        if (!gameState.isEarlyRushDelayLair()) {
            return;
        }

        Set<Plan> scheduledLairs = gameState.getPlansScheduled()
                .stream()
                .filter(plan -> plan.getType() == PlanType.BUILDING && plan.getPlannedUnit() == UnitType.Zerg_Lair)
                .collect(Collectors.toSet());

        for (Plan plan : scheduledLairs) {
            buildAheadSlot.release(plan);
            gameState.getPlansScheduled().remove(plan);
            gameState.cancelPlan(null, plan, PlanCancelSource.PRODUCTION_DELAYED_LAIR_SCHEDULED);
        }
    }

    private boolean hasExcessSupply(Player self) {
        return SupplyCapacity.isExcess(self.supplyTotal(), self.supplyUsed());
    }

    private void cancelExcessOverlordPlans() {
        if (!hasExcessSupply(game.self())) {
            return;
        }

        ResourceCount resourceCount = gameState.getResourceCount();
        gameState.getProductionQueue().removeWhere(
                p -> p.getType() == PlanType.UNIT && p.getPlannedUnit() == UnitType.Zerg_Overlord,
                PlanCancelSource.PRODUCTION_EXCESS_OVERLORD,
                plan -> {
                    gameState.setImpossiblePlan(plan);
                    int plannedSupply = resourceCount.getPlannedSupply();
                    resourceCount.setPlannedSupply(Math.max(0, plannedSupply - 16));
                });
    }

    /** Bounds how long a building plan holds the build-ahead slot. */
    private void enforceBuildAheadSlot() {
        buildAheadSlot.reconcile(activeBuildingPlans());

        cancelUnexecutableBuildingClaims();

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

    /** Requeues an evicted plan; it keeps its build position, so its tiles are not reserved twice. */
    private void requeueStalledPlan(Plan plan, BuildAheadSlot slot) {
        slot.releaseWithBackoff(plan, currentFrame);
        removeFromActivePlans(plan);
        releaseExecutor(plan);
        gameState.getResourceCount().unreserveUnit(plan.getPlannedUnit());
        plan.setPriority(BuildAheadSlot.requeuePriority(plan.getPriority()));
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
        for (Map.Entry<Unit, Plan> entry : gameState.getAssignedPlannedItems().entrySet()) {
            if (plan.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
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
        if (self.supplyUsed() >= 400) {
            return;
        }

        if (hasExcessSupply(self)) {
            return;
        }

        final int overlordCount = gameState.ourLivingUnitCount(UnitType.Zerg_Overlord);
        final int plannedSupply = gameState.getResourceCount().getPlannedSupply();
        final boolean isNinePool = "9PoolSpeed".equals(activeBuildOrder.getName());
        if (overlordCount < 2 && !isNinePool) {
            if (self.supplyUsed() >= 18 && overlordCount < 2 && plannedSupply == 0) {
                addUnitToQueue(UnitType.Zerg_Overlord, 1);
                gameState.getResourceCount().setPlannedSupply(16);
                return;
            }
            return;
        }
    
        List<Plan> sortedQueue = gameState.getProductionQueue().toSortedList();

        List<Plan> scheduledPlans = new ArrayList<>(gameState.getPlansScheduled());
        scheduledPlans.sort(new PlanComparator());

        int availableSupply = self.supplyTotal() - self.supplyUsed();
        availableSupply += gameState.getResourceCount().getPlannedSupply();

        for (Plan plan : scheduledPlans) {
            if (plan.getType() == PlanType.UNIT) {
                UnitType unitType = plan.getPlannedUnit();
                if (unitType == UnitType.Zerg_Overlord) {
                    availableSupply += 16;
                } else {
                    availableSupply -= unitType.supplyRequired();
                }
            }
        }

        final int SUPPLY_BUFFER = 4;

        List<Integer> overlordInsertPriorities = new ArrayList<>();

        for (Plan plan : sortedQueue) {
            if (plan.getType() != PlanType.UNIT) {
                continue;
            }

            UnitType unitType = plan.getPlannedUnit();
            if (unitType == UnitType.Zerg_Overlord) {
                availableSupply += 16;
                continue;
            }

            int supplyCost = unitType.supplyRequired();
            availableSupply -= supplyCost;

            while (availableSupply < SUPPLY_BUFFER && (self.supplyUsed() + gameState.getResourceCount().getPlannedSupply()) < 400) {
                int insertPriority = Math.max(1, plan.getPriority() - 1);
                overlordInsertPriorities.add(insertPriority);
                availableSupply += 16;
            }
        }

        for (int priority : overlordInsertPriorities) {
            addUnitToQueue(UnitType.Zerg_Overlord, priority);
            gameState.getResourceCount().setPlannedSupply(plannedSupply + 16);
        }

        // Emergency fallback: supply blocked with high minerals
        final int supplyRemaining = self.supplyTotal() - self.supplyUsed();
        if (supplyRemaining == 0 && self.minerals() > 700 && plannedSupply < 80) {
            addUnitToQueue(UnitType.Zerg_Overlord, 1);
            gameState.getResourceCount().setPlannedSupply(plannedSupply + 16);
        }
    }

    // This is only used for planSupply()
    // TODO: Move to BuildOrder?
    private void addUnitToQueue(UnitType unitType, int priority) {
        UnitTypeCount unitTypeCount = this.gameState.getUnitTypeCount();
        gameState.getProductionQueue().add(new UnitPlan(unitType, priority));
        unitTypeCount.planUnit(unitType);
    }

    private void plan() {
        gameState.getProductionQueue().addAll(activeBuildOrder.planEmergencyDefense(gameState));

        if (!isPlanning && !gameState.getProductionQueue().isEmpty()) {
            return;
        }

        // Once opener items are exhausted, plan items
        isPlanning = true;

        planSupply(gameState.getSelf());

        List<Plan> plans = activeBuildOrder.plan(gameState);
        gameState.getProductionQueue().addAll(plans);
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
        TechProgression techProgression = gameState.getTechProgression();

        final boolean hasFourOrMoreDrones = gameState.numGatherers() > 3;
        final int numHatcheries = gameState.getBaseData().numHatcheries();

        switch (unitType) {
            case Zerg_Overlord:
            case Zerg_Drone:
                return numHatcheries > 0;
            case Zerg_Zergling:
                return techProgression.isPlannedSpawningPool() || techProgression.isSpawningPool();
            case Zerg_Hydralisk:
                return hasFourOrMoreDrones && (techProgression.isPlannedDen() || techProgression.isHydraliskDen());
            case Zerg_Lurker:
                final boolean canScheduleLurker = techProgression.isPlannedLurker() || techProgression.isLurker();
                if (!hasFourOrMoreDrones || !canScheduleLurker) {
                    return false;
                }
                int hydraliskCount = gameState.ourUnitCount(UnitType.Zerg_Hydralisk);
                int assignedHydralisks = 0;
                // TODO: Generalize for other unit morphs
                for (Map.Entry<Unit, Plan> entry : gameState.getAssignedPlannedItems().entrySet()) {
                    if (entry.getKey().getType() == UnitType.Zerg_Hydralisk && 
                        entry.getValue().getPlannedUnit() == UnitType.Zerg_Lurker) {
                        assignedHydralisks++;
                    }
                }
                return hydraliskCount > assignedHydralisks;
            case Zerg_Mutalisk:
            case Zerg_Scourge:
                return hasFourOrMoreDrones && (techProgression.isPlannedSpire() || techProgression.isSpire());
            case Zerg_Ultralisk:
                return hasFourOrMoreDrones && (techProgression.isPlannedUltraliskCavern() || techProgression.isUltraliskCavern());
            case Zerg_Defiler:
                return hasFourOrMoreDrones && (techProgression.isPlannedDefilerMound() || techProgression.isDefilerMound());
            default:
                return false;
        }
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
            if (!canSchedulePlan(plan)) {
                plan.setCancelSource(PlanCancelSource.PRODUCTION_SCHEDULE_GATE);
                gameState.setImpossiblePlan(plan);
                continue;
            }
            schedulable.add(plan);
        }

        ScanOutcome outcome = scanPlans(schedulable, this::schedulePlan);
        gameState.getPlansScheduled().addAll(outcome.scheduled);
        gameState.getProductionQueue().addAll(outcome.requeued);
    }

    private PlanBlocker schedulePlan(Plan plan, boolean bankClaimedAhead) {
        switch (plan.getType()) {
            case BUILDING:
                return scheduleBuildingItem(plan, bankClaimedAhead);
            case UNIT:
                return scheduleUnitItem(plan, bankClaimedAhead);
            case UPGRADE:
                return scheduleUpgradeItem(game.self(), plan);
            case TECH:
                return scheduleResearch(plan);
            default:
                return PlanBlocker.UNSUPPORTED_PLAN_TYPE;
        }
    }

    @FunctionalInterface
    interface PlanScheduler {
        PlanBlocker schedule(Plan plan, boolean bankClaimedAhead);
    }

    static final class ScanOutcome {

        final List<Plan> scheduled = new ArrayList<>();

        final List<Plan> requeued = new ArrayList<>();
    }

    /** Scans every plan in priority order without stopping at blockers. */
    static ScanOutcome scanPlans(List<Plan> plansInPriorityOrder, PlanScheduler scheduler) {
        ScanOutcome outcome = new ScanOutcome();
        boolean bankClaimedAhead = false;
        for (Plan plan : plansInPriorityOrder) {
            PlanBlocker blocker = scheduler.schedule(plan, bankClaimedAhead);
            if (blocker == PlanBlocker.NONE) {
                outcome.scheduled.add(plan);
                continue;
            }
            PlanEvents.blocked(plan, blocker);
            outcome.requeued.add(plan);
            bankClaimedAhead = bankClaimedAhead || claimsBank(blocker);
        }
        return outcome;
    }

    static boolean claimsBank(PlanBlocker blocker) {
        return blocker == PlanBlocker.RESOURCES;
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
    private PlanBlocker scheduleBuildingItem(Plan plan, boolean hasHigherPriorityPending) {
        UnitType building = plan.getPlannedUnit();

        if (isColonyMorph(building) && !hasCreepColonyAtPosition(plan.getBuildPosition())) {
            Unit unassignedColony = findUnassignedCreepColony();
            if (unassignedColony == null) {
                return PlanBlocker.NO_CREEP_COLONY;
            }
            plan.setBuildPosition(unassignedColony.getTilePosition());
        }

        ResourceCount resourceCount = gameState.getResourceCount();
        int predictedReadyFrame = gameState.frameCanAffordUnit(building, currentFrame);
        PlanBlocker buildAheadBlocker = buildAheadBlocker(
                buildAheadSlot,
                building,
                currentFrame,
                resourceCount.cannotAffordUnit(building),
                hasHigherPriorityPending,
                predictedReadyFrame);
        if (buildAheadBlocker != PlanBlocker.NONE) {
            return buildAheadBlocker;
        }

        if (!resolveBuildPosition(plan, building)) {
            return PlanBlocker.NO_BUILD_POSITION;
        }

        buildAheadSlot.claim(plan, currentFrame, predictedReadyFrame);
        resourceCount.reserveUnit(building);
        plan.setPredictedReadyFrame(predictedReadyFrame);
        plan.setState(PlanState.SCHEDULE);
        return PlanBlocker.NONE;
    }

    static PlanBlocker buildAheadBlocker(
            BuildAheadSlot slot,
            UnitType building,
            int frame,
            boolean cannotAfford,
            boolean hasHigherPriorityPending,
            int predictedReadyFrame) {
        if (slot.isInBackoff(building, frame)) {
            return PlanBlocker.BUILD_AHEAD_BACKOFF;
        }
        if (!cannotAfford) {
            return PlanBlocker.NONE;
        }
        if (hasHigherPriorityPending || slot.isOccupied()) {
            return PlanBlocker.BUILD_AHEAD_SLOT_TAKEN;
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

    private boolean isColonyMorph(UnitType type) {
        return type == UnitType.Zerg_Sunken_Colony || type == UnitType.Zerg_Spore_Colony;
    }

    private boolean hasCreepColonyAtPosition(TilePosition tp) {
        if (tp == null) {
            return false;
        }
        for (Unit unit : gameState.getSelf().getUnits()) {
            if (unit.getType() == UnitType.Zerg_Creep_Colony
                    && unit.isCompleted()
                    && unit.getTilePosition().equals(tp)) {
                return true;
            }
        }
        return false;
    }

    private Unit findUnassignedCreepColony() {
        for (Unit unit : gameState.getSelf().getUnits()) {
            if (unit.getType() == UnitType.Zerg_Creep_Colony
                    && unit.isCompleted()
                    && !gameState.getAssignedPlannedItems().containsKey(unit)) {
                return unit;
            }
        }
        return null;
    }

    private PlanBlocker scheduleUnitItem(Plan plan, boolean bankClaimedAhead) {
        UnitType unit = plan.getPlannedUnit();
        ResourceCount resourceCount = gameState.getResourceCount();
        if (!resourceCount.canScheduleLarva(gameState.numLarva())) {
            return PlanBlocker.NO_LARVA;
        }

        boolean cannotAfford = resourceCount.cannotAffordUnit(unit);
        int predictedReadyFrame = gameState.frameCanAffordUnit(unit, currentFrame);
        PlanBlocker unitAheadBlocker = unitAheadBlocker(
                unitAheadSlot,
                unit,
                currentFrame,
                cannotAfford,
                bankClaimedAhead,
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

    static PlanBlocker unitAheadBlocker(
            BuildAheadSlot slot,
            UnitType unit,
            int frame,
            boolean cannotAfford,
            boolean bankClaimedAhead,
            int predictedReadyFrame) {
        if (!cannotAfford) {
            return PlanBlocker.NONE;
        }
        if (bankClaimedAhead || slot.isOccupied()) {
            return PlanBlocker.BUILD_AHEAD_SLOT_TAKEN;
        }
        if (slot.isInBackoff(unit, frame)) {
            return PlanBlocker.BUILD_AHEAD_BACKOFF;
        }
        if (BuildAheadSlot.isUnreachable(predictedReadyFrame)) {
            return PlanBlocker.NO_INCOME;
        }
        if (predictedReadyFrame - frame > unit.buildTime()) {
            return PlanBlocker.RESOURCES;
        }
        return PlanBlocker.NONE;
    }

    private PlanBlocker scheduleUpgradeItem(Player self, Plan plan) {
        final UpgradeType upgrade = plan.getPlannedUpgrade();
        ResourceCount resourceCount = gameState.getResourceCount();

        if (resourceCount.cannotAffordUpgrade(plan)) {
            return PlanBlocker.RESOURCES;
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

    private PlanBlocker scheduleResearch(Plan plan) {
        final TechType techType = plan.getPlannedTechType();
        ResourceCount resourceCount = gameState.getResourceCount();

        if (resourceCount.cannotAffordResearch(techType)) {
            return PlanBlocker.RESOURCES;
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
     */
    private void reprioritizeHatcheriesForLarvaConstraint() {
        if (gameState.numLarva() > 0) {
            return;
        }

        if (gameState.getResourceCount().availableMinerals() < 300) {
            return;
        }

        Plan priorityHatcheryPlan = null;
        int highestPriority = Integer.MAX_VALUE;

        for (Plan plan : gameState.getProductionQueue()) {
            if (plan.getType() == PlanType.BUILDING && 
                plan.getPlannedUnit() == UnitType.Zerg_Hatchery) {
                
                if (plan.getPriority() < highestPriority) {
                    highestPriority = plan.getPriority();
                    priorityHatcheryPlan = plan;
                }
            }
        }

        if (priorityHatcheryPlan != null && highestPriority > 0) {
            priorityHatcheryPlan.setPriority(0);
        }
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

    private void cancelImpossibleScheduledLurkerPlans() {
        Set<Plan> lurkerPlans = gameState.getPlansScheduled().stream()
                .filter(plan -> plan.getType() == PlanType.UNIT && plan.getPlannedUnit() == UnitType.Zerg_Lurker)
                .collect(Collectors.toSet());
        int hydraliskCount = gameState.getUnitTypeCount().livingCount(UnitType.Zerg_Hydralisk);
        int lurkerPlanCount = lurkerPlans.size();

        
        if (hydraliskCount == 0 || lurkerPlanCount > hydraliskCount) {
            for (Plan plan : lurkerPlans) {
                gameState.getPlansScheduled().remove(plan);
                gameState.cancelPlan(null, plan, PlanCancelSource.PRODUCTION_SCHEDULED_LURKER);
            }
        }
    }
}
