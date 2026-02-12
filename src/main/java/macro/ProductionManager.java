package macro;

import bwapi.Game;
import bwapi.Player;
import bwapi.TechType;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.GameState;
import info.ResourceCount;
import info.TechProgression;
import info.UnitTypeCount;
import macro.plan.Plan;
import macro.plan.PlanComparator;
import macro.plan.PlanState;
import macro.plan.PlanType;
import macro.plan.UnitPlan;
import strategy.buildorder.BuildOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages the production of units, buildings, upgrades and research.
 * <p>
 * The Bot's BuildOrder is responsible for deciding what units should be queued; the exact unit
 * is currently determined probabilistically by UnitWeights.
 */
public class ProductionManager {

    final int FRAME_ZVZ_HATCH_RESTRICT = 7200; // 5m

    private Game game;

    private GameState gameState;

    // isPlanning contingent on -> hitting min supply set by build order OR queue exhaust
    private boolean isPlanning = false;

    private int scheduledBuildings = 0;

    private int currentFrame = 5;


    // TODO: Determine if only 1 active strategy, or if multiple can be active at once.
    private BuildOrder activeBuildOrder;

    public ProductionManager(Game game, GameState gameState, BuildOrder opener) {
        this.game = game;
        this.gameState = gameState;

        this.activeBuildOrder = opener;
    }

    private void debugProductionQueue() { }

    // TODO: Ensure print out of production queue is displaying how much time is remaining
    private void debugInProgressQueue() { }

    // TODO: Ensure print out of production queue is displaying how much time is remaining
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
        plan();
        cancelImpossiblePlans();
        cancelExcessHatcheryPlans();
        cancelExcessOverlordPlans();
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
        List<Plan> plansToCancel = new ArrayList<>();
        
        for (Plan plan : gameState.getProductionQueue()) {
            if (!canSchedulePlan(plan)) {
                plansToCancel.add(plan);
            }
        }
        
        for (Plan plan : plansToCancel) {
            gameState.getProductionQueue().remove(plan);
            gameState.setImpossiblePlan(plan);
        }
        
        cancelImpossibleScheduledLurkerPlans();
        removePlansWithLaterPrerequisites();
    }

    /**
     * Cancels excess hatchery plans when floating larva.
     * Conditions: 3+ hatcheries AND 4+ larva.
     */
    private void cancelExcessHatcheryPlans() {
        final int MIN_HATCHERIES = 3;
        final int MIN_LARVA = 5;

        int numHatcheries = gameState.getBaseData().numHatcheries();
        int numLarva = gameState.numLarva();
        boolean isFloatingMinerals = gameState.isFloatingMinerals();

        if (isFloatingMinerals || numHatcheries < MIN_HATCHERIES || numLarva < MIN_LARVA) {
            return;
        }

        List<Plan> queuePlansToCancel = new ArrayList<>();
        PriorityQueue<Plan> pq = gameState.getProductionQueue();
        for (Plan plan : pq) {
            if (plan.getType() == PlanType.BUILDING &&
                plan.getPlannedUnit() == UnitType.Zerg_Hatchery) {
                queuePlansToCancel.add(plan);
            }
        }

        for (Plan plan : queuePlansToCancel) {
            pq.remove(plan);
            gameState.setImpossiblePlan(plan);
        }

        Set<Plan> scheduledPlansToCancel = gameState.getPlansScheduled()
                .stream()
                .filter(plan -> plan.getType() == PlanType.BUILDING && plan.getPlannedUnit() == UnitType.Zerg_Hatchery)
                .collect(Collectors.toSet());

        for (Plan plan : scheduledPlansToCancel) {
            gameState.getPlansScheduled().remove(plan);
            gameState.cancelPlan(null, plan);
        }
    }

    /**
     * Cancels excess overlord plans when free supply is sufficient.
     * Example: We queue up 10 hydras but half of them die by the time the overlord plan is ready to build.
     */
    private void cancelExcessOverlordPlans() {
        final int MAX_FREE_SUPPLY = 17;

        Player self = game.self();
        int freeSupply = self.supplyTotal() - self.supplyUsed();

        if (freeSupply <= MAX_FREE_SUPPLY) {
            return;
        }

        PriorityQueue<Plan> pq = gameState.getProductionQueue();
        List<Plan> queuePlansToCancel = new ArrayList<>();
        for (Plan plan : pq) {
            if (plan.getType() == PlanType.UNIT &&
                plan.getPlannedUnit() == UnitType.Zerg_Overlord) {
                queuePlansToCancel.add(plan);
            }
        }

        ResourceCount resourceCount = gameState.getResourceCount();
        for (Plan plan : queuePlansToCancel) {
            pq.remove(plan);
            gameState.setImpossiblePlan(plan);
            int plannedSupply = resourceCount.getPlannedSupply();
            resourceCount.setPlannedSupply(Math.max(0, plannedSupply - 16));
        }
    }

    private void removePlansWithLaterPrerequisites() {
        List<Plan> plansToRemove = new ArrayList<>();
        List<Plan> queueList = new ArrayList<>(gameState.getProductionQueue());
        
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
            default:
                return null;
        }
    }

    private UnitType getPrerequisiteForTech(TechType techType) {
        switch (techType) {
            case Lurker_Aspect:
                return UnitType.Zerg_Hydralisk_Den;
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

        List<Plan> sortedQueue = new ArrayList<>(gameState.getProductionQueue());
        Collections.sort(sortedQueue, new PlanComparator());

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
            addUnitToQueue(UnitType.Zerg_Overlord, priority, true);
            int plannedSupply = gameState.getResourceCount().getPlannedSupply();
            gameState.getResourceCount().setPlannedSupply(plannedSupply + 16);
        }

        // Emergency fallback: supply blocked with high minerals
        final int supplyRemaining = self.supplyTotal() - self.supplyUsed();
        int plannedSupply = gameState.getResourceCount().getPlannedSupply();
        if (supplyRemaining == 0 && self.minerals() > 700 && plannedSupply < 80) {
            addUnitToQueue(UnitType.Zerg_Overlord, 1, true);
            gameState.getResourceCount().setPlannedSupply(plannedSupply + 16);
        }
    }

    // This is only used for planSupply()
    // TODO: Move to BuildOrder?
    private void addUnitToQueue(UnitType unitType, int priority, boolean isBlocking) {
        UnitTypeCount unitTypeCount = this.gameState.getUnitTypeCount();
        gameState.getProductionQueue().add(new UnitPlan(unitType, priority, isBlocking));
        unitTypeCount.planUnit(unitType);
    }

    private void plan() {
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
            case Zerg_Lair:
                return numHatcheries > 0 && techProgression.isSpawningPool();
            case Zerg_Spire:
            case Zerg_Queens_Nest:
                return techProgression.isLair();
            case Zerg_Hive:
                return techProgression.isLair() && techProgression.isQueensNest();
            default:
                return false;
        }
    }

    private boolean canScheduleTech(TechType techType) {
        TechProgression techProgression = gameState.getTechProgression();
        switch (techType) {
            case Lurker_Aspect:
                return techProgression.isHydraliskDen();
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
                return techProgression.isLair();
            default:
                return false;
        }
    }

    private void schedulePlannedItems() {
        if (gameState.getProductionQueue().isEmpty()) {
            return;
        }

        reprioritizeHatcheriesForLarvaConstraint();

        Player self = game.self();

        // Loop through items until we exhaust queue, or we break because we can't consume top item
        // Call method to attempt to build that type, if we can't build return false and break the loop

        HashSet<Plan> scheduledPlans = gameState.getPlansScheduled();

        List<Plan> requeuePlans = new ArrayList<>();
        ResourceCount resourceCount = gameState.getResourceCount();
        int mineralBuffer = resourceCount.availableMinerals();
        int gasBuffer = resourceCount.availableGas();
        for (int i = 0; i < gameState.getProductionQueue().size(); i++) {

            boolean canSchedule = false;
            // If we can't plan, we'll put it back on the queue
            final Plan plan = gameState.getProductionQueue().poll();
            if (plan == null) {
                continue;
            }

            // Don't block the queue if the plan cannot be executed
            if (!canSchedulePlan(plan)) {
                gameState.setImpossiblePlan(plan);
                continue;
            }

            PlanType planType = plan.getType();

            switch (planType) {
                case BUILDING:
                    canSchedule = scheduleBuildingItem(plan);
                    break;
                case UNIT:
                    canSchedule = scheduleUnitItem(plan);
                    break;
                case UPGRADE:
                    canSchedule = scheduleUpgradeItem(self, plan);
                    break;
                case TECH:
                    canSchedule = scheduleResearch(plan);
                    break;
                default:
                    canSchedule = false;
            }

            if (canSchedule) {
                scheduledPlans.add(plan);
            } else {
                requeuePlans.add(plan);
                mineralBuffer -= plan.mineralPrice();
                gasBuffer -= plan.gasPrice();
                if (mineralBuffer <= 0 && gasBuffer <= 0) {
                    break;
                }
            }
        }

        // Requeue
        gameState.getProductionQueue().addAll(requeuePlans);
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
            scheduledBuildings -= 1;
        }

        TechProgression techProgression = this.gameState.getTechProgression();

        switch (unitType) {
            case Zerg_Hydralisk_Den:
                techProgression.setHydraliskDen(true);
                techProgression.setPlannedDen(false);
                break;
            case Zerg_Spawning_Pool:
                techProgression.setSpawningPool(true);
                techProgression.setPlannedSpawningPool(false);
                break;
            case Zerg_Lair:
                techProgression.setLair(true);
                techProgression.setPlannedLair(false);
                break;
            case Zerg_Spire:
                techProgression.setSpire(true);
                techProgression.setPlannedSpire(false);
                break;
            case Zerg_Queens_Nest:
                techProgression.setQueensNest(true);
                techProgression.setPlannedQueensNest(false);
                break;
            case Zerg_Hive:
                techProgression.setHive(true);
                techProgression.setPlannedHive(false);
                break;
            default:
                break;
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

        ResourceCount resourceCount = gameState.getResourceCount();
        TechProgression techProgression = gameState.getTechProgression();

        if (unit.isUpgrading()) {
            resourceCount.unreserveUpgrade(upgradeType);
            techProgression.upgradeTech(upgradeType);
            return true;
        }
        return false;
    }

    private boolean researchTech(Unit unit, Plan plan) {
        final TechType techType = plan.getPlannedTechType();
        if (game.canResearch(techType, unit)) {
            unit.research(techType);
        }

        ResourceCount resourceCount = gameState.getResourceCount();
        TechProgression techProgression = gameState.getTechProgression();

        if (unit.isResearching()) {
            resourceCount.unreserveTechResearch(techType);
            techProgression.upgradeTech(techType);
            return true;
        }
        return false;
    }

    // PLANNED -> SCHEDULED
    // Allow one building to be scheduled if resources aren't available.
    private boolean scheduleBuildingItem(Plan plan) {
        // Can we afford this unit?
        UnitType building = plan.getPlannedUnit();
        ResourceCount resourceCount = gameState.getResourceCount();
        int predictedReadyFrame = gameState.frameCanAffordUnit(building, currentFrame);
        if (scheduledBuildings > 0 && resourceCount.canAffordUnit(building)) {
            return false;
        }

        // TODO: Assign building location from building location planner
        if (plan.getBuildPosition() == null) {
            plan.setBuildPosition(game.getBuildLocation(building, gameState.getBaseData().mainBasePosition(), 128, true));
        }

        scheduledBuildings += 1;
        resourceCount.reserveUnit(building);
        plan.setPredictedReadyFrame(predictedReadyFrame);
        plan.setState(PlanState.SCHEDULE);
        return true;
    }

    private boolean scheduleUnitItem(Plan plan) {
        UnitType unit = plan.getPlannedUnit();
        ResourceCount resourceCount = gameState.getResourceCount();
        if (resourceCount.canAffordUnit(unit)) {
            return false;
        }

        if (!resourceCount.canScheduleLarva(gameState.numLarva())) {
            return false;
        }

        resourceCount.reserveUnit(unit);
        plan.setState(PlanState.SCHEDULE);
        return true;
    }

    private boolean scheduleUpgradeItem(Player self, Plan plan) {
        final UpgradeType upgrade = plan.getPlannedUpgrade();
        ResourceCount resourceCount = gameState.getResourceCount();

        if (resourceCount.canAffordUpgrade(upgrade)) {
            return false;
        }

        Unit nextAvailable = null;
        for (Unit unit : self.getUnits()) {
            UnitType unitType = unit.getType();

            if (unitType != upgrade.whatUpgrades()) {
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
                resourceCount.reserveUpgrade(upgrade);
                return true;
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

        return false;
    }

    private boolean scheduleResearch(Plan plan) {
        final TechType techType = plan.getPlannedTechType();
        ResourceCount resourceCount = gameState.getResourceCount();

        if (resourceCount.canAffordResearch(techType)) {
            return false;
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
                return true;
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

        return false;
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
            clearAssignments(unit, false);
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
                        gameState.cancelPlan(unit, plan);
                    } else {
                        gameState.completePlan(unit, plan);
                    }
                    break;
                case SCHEDULE:
                    gameState.cancelPlan(unit, plan);
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
                gameState.cancelPlan(null, plan);
            }
        }
    }
}
