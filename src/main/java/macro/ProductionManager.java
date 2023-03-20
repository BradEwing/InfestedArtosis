package macro;

import bwapi.Color;
import bwapi.Game;
import bwapi.Player;
import bwapi.Text;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.UpgradeType;

import bwem.BWEM;
import bwem.Base;
import bwem.Mineral;
import info.GameState;
import info.ResourceCount;
import info.TechProgression;
import info.UnitTypeCount;
import planner.PlanState;
import planner.PlanType;
import planner.PlannedItem;
import planner.PlannedItemComparator;
import strategy.strategies.UnitWeights;
import unit.managed.ManagedUnit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

// TODO: There is economy information here, build order and strategy. refactor
// Possible arch: GATHER GAME STATE -> PLAN -> EXECUTE
// STRATEGY -> BUILD ORDER (QUEUE) -> BUILD / ECONOMY MANAGEMENT (rebalance workers) (this file should eventually only be final step)
//
public class ProductionManager {

    private static int BASE_MINERAL_DISTANCE = 300;

    private Game game;

    private GameState gameState;

    // isPlanning contingent on -> hitting min supply set by build order OR queue exhaust
    private boolean isPlanning = false;

    // TODO: Track in info manager / GameState with some sort of base planner class
    private int numSunkens = 0;

    private int numExtractors = 0;

    private int scheduledBuildings = 0;

    // TODO: Determine from desired unit composition / active strategy
    // Because bot is only on hatch tech, only take 1 for now
    private int targetExtractors = 1;

    private int currentFrame = 5;
    private int plannedHatcheries = 1; // Start with 1 because we decrement with initial hatch
    private int plannedWorkers = 0;

    private PriorityQueue<PlannedItem> productionQueue = new PriorityQueue<>(new PlannedItemComparator());

    public ProductionManager(Game game, GameState gameState, List<PlannedItem> initialBuildOrder) {
        this.game = game;
        this.gameState = gameState;

        Unit initialHatch = null;

        // For now, let's iterate through twice. There are barely any units initialized
        for (Unit unit: game.getAllUnits()) {
            // Don't count opponent hatch in ZvZ
            if (game.self() != unit.getPlayer()) {
                continue;
            }

            if (unit.getType() == UnitType.Zerg_Hatchery) {
                buildBase(unit);
                initialHatch = unit;
            }
        }

        List<Base> allBases = bwem.getMap().getBases();
        this.mainBase = closestBaseToUnit(initialHatch, allBases);
        addBaseToGameState(mainBase);

        HashMap<Base, HashSet<ManagedUnit>> gatherToBaseAssignments = this.gameState.getGatherersAssignedToBase();
        gatherToBaseAssignments.put(mainBase, new HashSet<>());

        // TODO: extract to own function? assign all buildings in priority queue a location
        for (PlannedItem plannedItem: initialBuildOrder) {
            if (plannedItem.getPlannedUnit() != null && plannedItem.getPlannedUnit() == UnitType.Zerg_Extractor) {
                this.numExtractors += 1;
            }

            // TODO: be able to decide between base hatch and macro hatch
            if (plannedItem.getPlannedUnit() != null && plannedItem.getPlannedUnit() == UnitType.Zerg_Hatchery) {
                Base base = findNewBase();
                plannedItem.setBuildPosition(base.getLocation());
                addBaseToGameState(base);
            }

            if (plannedItem.getPlannedUnit() != null && plannedItem.getPlannedUnit() == UnitType.Zerg_Drone) {
                plannedWorkers += 1;
            }

            if (plannedItem.getType() == PlanType.UPGRADE) {
                if (plannedItem.getPlannedUpgrade() == UpgradeType.Metabolic_Boost) {
                    TechProgression techProgression = gameState.getTechProgression();
                    techProgression.setMetabolicBoost(true);
                }
            }


            this.productionQueue.add(plannedItem);
        }
    }

    private void debugProductionQueue() {
        int numDisplayed = 0;
        int x = 4;
        int y = 64;
        for (PlannedItem plannedItem : productionQueue) {
            game.drawTextScreen(x, y, plannedItem.getName() + " " + plannedItem.getPriority(), Text.Green);
            y += 8;
            numDisplayed += 1;
            if (numDisplayed == 10) {
                break;
            }
        }

        if (numDisplayed < productionQueue.size()) {
            game.drawTextScreen(x, y, String.format("... %s more planned items", productionQueue.size() - numDisplayed), Text.GreyGreen);
        }
    }

    private void debugBaseStats() {

    }

    // TODO: Ensure print out of production queue is displaying how much time is remaining
    private void debugInProgressQueue() {
        int numDisplayed = 0;
        int x = 100;
        int y = 64;
        // TODO: Debug production queue in GameState
        for (PlannedItem plannedItem: gameState.getAssignedPlannedItems().values()) {
            game.drawTextScreen(x, y, plannedItem.getName() + " " + plannedItem.getPriority(), Text.Green);
            y += 8;
            numDisplayed += 1;
            if (numDisplayed == 10) {
                break;
            }
        }
    }

    // TODO: Ensure print out of production queue is displaying how much time is remaining
    private void debugScheduledPlannedItems() {
        int numDisplayed = 0;
        int x = 196;
        int y = 64;
        // TODO: Debug production queue in GameState
        for (PlannedItem plannedItem: gameState.getPlansScheduled()) {
            game.drawTextScreen(x, y, plannedItem.getName() + " " + plannedItem.getPriority(), Text.Green);
            y += 8;
            numDisplayed += 1;
            if (numDisplayed == 10) {
                break;
            }
        }
    }

    private void planBase() {
        Base base = findNewBase();
        // all possible bases are taken!
        if (base == null) {
            return;
        }

        productionQueue.add(new PlannedItem(UnitType.Zerg_Hatchery, currentFrame, true, true, base.getLocation()));
        addBaseToGameState(base);
    }

    // debug console messaging goes here
    private void debug() {
        debugProductionQueue();
        debugInProgressQueue();
        debugScheduledPlannedItems();
    }

    // TODO: Determine why some workers go and stay idle
    public void onFrame() {

        debug();

        currentFrame = game.getFrameCount();

        plan();
        schedulePlannedItems();
        buildUpgrades();

        for (Unit u: game.getAllUnits()) {
            if (u.getType().isWorker() && u.isIdle()) {
                assignUnit(u);
            }
        }

        for (Unit u: bases) {
            game.drawCircleMap(u.getPosition(), BASE_MINERAL_DISTANCE, Color.Teal);
        }
    }

    private int expectedWorkers() {
        final int base = 5;
        final int expectedMineralWorkers = bases.size() * 7;
        final int expectedGasWorkers = gameState.getGeyserAssignments().size() * 3;
        return base + expectedMineralWorkers + expectedGasWorkers;
    }

    private int numWorkers() {
        return gameState.getMineralWorkers() + gameState.getGeyserWorkers();
    }

    // TODO: Handle elsewhere
    private int numHatcheries() { return baseLocations.size() + macroHatcheries.size(); }

    private void planBuildings(Player self, Boolean isAllIn) {
        // Allow buildings to arbitrary queue size
        // Always allow hatch to enter queue even if we're at max size (means we are throttled on bandwith)

        // 2 Types of Hatch:
        // BaseData Hatch - Setup resources to assign workers, add to base data
        // Macro Hatch - Take a macro hatch every other time
        // Limit to 3 plannedHatch to prevent queue deadlock
        if (!isAllIn && (canAffordHatch() || (isNearMaxExpectedWorkers() && canAffordHatchSaturation())) && plannedHatcheries < 3) {
            plannedHatcheries += 1;
            if ((numHatcheries() % 2) != 0) {
                planBase();
            } else {
                productionQueue.add(new PlannedItem(UnitType.Zerg_Hatchery, currentFrame / 3, true, true));
            }
        }

        // One extractor per base
        //  - Not always true, some bases are mineral only. Some maps have double gas.
        // TODO: account for bases with no gas or 2 gas
        if (!isAllIn && numExtractors < bases.size() && numExtractors < targetExtractors) {
            numExtractors += 1;
            productionQueue.add(new PlannedItem(UnitType.Zerg_Extractor, currentFrame, true, false));
        }

        TechProgression techProgression = this.gameState.getTechProgression();

        // Build at 10 workers if not part of initial build order
        if (techProgression.canPlanPool() && self.supplyUsed() > 20) {
            productionQueue.add(new PlannedItem(UnitType.Zerg_Spawning_Pool, currentFrame / 4, true, true));
            techProgression.setPlannedSpawningPool(true);
        }

        if (isAllIn) {
            return;
        }

        UnitWeights unitWeights = this.gameState.getUnitWeights();

        if (techProgression.canPlanHydraliskDen() && unitWeights.hasUnit(UnitType.Zerg_Hydralisk)) {
            productionQueue.add(new PlannedItem(UnitType.Zerg_Hydralisk_Den, currentFrame, true, true));
            techProgression.setPlannedDen(true);
        }

        final boolean needLairTech = unitWeights.hasUnit(UnitType.Zerg_Mutalisk) || unitWeights.hasUnit(UnitType.Zerg_Scourge);

        if (needLairTech && techProgression.canPlanLair()) {
            productionQueue.add(new PlannedItem(UnitType.Zerg_Lair, currentFrame, true, true));
            techProgression.setPlannedLair(true);
        }

        if (techProgression.canPlanSpire() && unitWeights.hasUnit(UnitType.Zerg_Mutalisk)) {
            productionQueue.add(new PlannedItem(UnitType.Zerg_Spire, currentFrame, true, true));
            techProgression.setPlannedSpire(true);
        }
    }

    /**
     * Plan to take an upgrade.
     *
     * Does not plan if there is no gas; all upgrades require gas.
     *
     * TODO: Track when an upgrade completes
     *
     * NOTE: Potential for reinforcement learning to search when to take an upgrade against an opponent.
     * @param isAllIn
     */
    private void planUpgrades(Boolean isAllIn) {

        if (numExtractors == 0 || isAllIn) {
            return;
        }

        TechProgression techProgression = this.gameState.getTechProgression();
        UnitTypeCount unitTypeCount = gameState.getUnitTypeCount();

        /** Ling Upgrades **/
        if (techProgression.canPlanMetabolicBoost() && unitTypeCount.get(UnitType.Zerg_Zergling) > 8) {
            productionQueue.add(new PlannedItem(UpgradeType.Metabolic_Boost, currentFrame, false));
            techProgression.setPlannedMetabolicBoost(true);
        }

        /** Hydra Upgrades */
        final int numHydralisks = unitTypeCount.get(UnitType.Zerg_Hydralisk);
        if (techProgression.canPlanMuscularAugments() && numHydralisks > 4) {
            productionQueue.add(new PlannedItem(UpgradeType.Muscular_Augments, currentFrame, false));
            techProgression.setPlannedMuscularAugments(true);
        }
        if (techProgression.canPlanGroovedSpines() && numHydralisks > 10) {
            productionQueue.add(new PlannedItem(UpgradeType.Grooved_Spines, currentFrame, false));
            techProgression.setPlannedGroovedSpines(true);
        }
    }


    // planSupply checks if near supply cap or supply blocked
    private void planSupply(Player self) {
        final int supplyRemaining = self.supplyTotal() - self.supplyUsed();
        int plannedSupply = gameState.getPlannedSupply();
        if (supplyRemaining + plannedSupply < 5 && self.supplyUsed() < 400) {
            gameState.setPlannedSupply(plannedSupply+16);
            productionQueue.add(new PlannedItem(UnitType.Zerg_Overlord, currentFrame / 3, false, false));
        } else if (supplyRemaining + plannedSupply < 0 && self.supplyUsed() < 400) {
            gameState.setPlannedSupply(plannedSupply+16);
            productionQueue.add(new PlannedItem(UnitType.Zerg_Overlord, currentFrame / 2, false, true));
        }
    }

    // TODO: Droning vs Combat Units
    private void planUnits(Player self, Boolean isAllIn) {
        if (self.supplyUsed() >= 400) {
            return;
        }
        // Plan workers
        // This should be related to num bases + aval min patches and geysers, limited by army and potentially higher level strat info
        // For now, set them to be 1/3 of total supply
        // Limit the number of drones in queue, or they will crowd out production!
        if (!isAllIn && plannedWorkers < 3 && numWorkers() < 80 && numWorkers() < expectedWorkers()) {
            plannedWorkers += 1;
            addUnitToQueue(UnitType.Zerg_Drone);
        }

        UnitWeights unitWeights = this.gameState.getUnitWeights();

        // Plan army
        UnitType unitToBuild = unitWeights.getRandom();
        if (unitToBuild == UnitType.Unknown) {
            return;
        }
        addUnitToQueue(unitToBuild);
    }

    private void addUnitToQueue(UnitType unitType) {
        UnitTypeCount unitTypeCount = this.gameState.getUnitTypeCount();
        productionQueue.add(new PlannedItem(unitType, currentFrame, false, false));
        unitTypeCount.planUnit(unitType);
    }

    // TODO: Make this smarter, following a strategy to define unit mix, when to take upgrades, etc.
    private void plan() {
        Player self = game.self();
        Boolean isAllIn = gameState.isAllIn();

        if (!isPlanning && productionQueue.size() > 0) {
            return;
        }

        // Once opener items are exhausted, plan items
        isPlanning = true;

        planBuildings(self, isAllIn);

        // NOTE: Always let upgrades to enter the queue, we take them greedily
        // Plan tech / upgrades
        // The former should at least be driven by a higher level (strategy) manager
        // For now, greedily plan upgrades
        planUpgrades(isAllIn);

        // Plan supply
        planSupply(self);

        /** For now, only subject unit production to queue size */
        // BaseData queue size is 3, increases per hatch
        if (productionQueue.size() >= 3) {
            return;
        }

        planUnits(self, isAllIn);
    }

    private boolean canAffordHatchSaturation() {
        return ((numHatcheries() + plannedHatcheries) * 7) <= gameState.getMineralWorkers();
    }

    private boolean canAffordHatch() {
        ResourceCount resourceCount = gameState.getResourceCount();
        return resourceCount.canAffordHatch(plannedHatcheries);
    }

    private boolean isNearMaxExpectedWorkers() {
        return ((expectedWorkers() * (1 + plannedHatcheries)) - numWorkers() < 0);
    }

    /**
     * Plans that are impossible to schedule can block the queue.
     * @return boolean indicating if the plan can be scheduled
     */
    private boolean canSchedulePlan(PlannedItem plannedItem) {
        switch (plannedItem.getType()) {
            case UNIT:
                return canScheduleUnit(plannedItem.getPlannedUnit());
            case BUILDING:
                return canScheduleBuilding(plannedItem.getPlannedUnit());
            case UPGRADE:
                return canScheduleUpgrade(plannedItem.getPlannedUpgrade());
            default:
                return false;
        }
    }

    // TODO: Consider units trying to schedule before their required tech
    private boolean canScheduleUnit(UnitType unitType) {
        TechProgression techProgression = gameState.getTechProgression();

        final boolean hasFourOrMoreDrones = gameState.numGatherers() > 3;
        switch(unitType) {
            case Zerg_Overlord:
            case Zerg_Drone:
                return numHatcheries() > 0;
            case Zerg_Zergling:
                return techProgression.isPlannedSpawningPool() || techProgression.isSpawningPool();
            case Zerg_Hydralisk:
                return hasFourOrMoreDrones && (techProgression.isPlannedDen() || techProgression.isHydraliskDen());
            case Zerg_Mutalisk:
            case Zerg_Scourge:
                return hasFourOrMoreDrones && (techProgression.isPlannedSpire() || techProgression.isSpire());
            default:
                return false;
        }
    }

    private boolean canScheduleBuilding(UnitType unitType) {
        TechProgression techProgression = gameState.getTechProgression();
        switch(unitType) {
            case Zerg_Hatchery:
            case Zerg_Extractor:
            case Zerg_Creep_Colony:
                return true;
            case Zerg_Spawning_Pool:
                return numHatcheries() > 0;
            case Zerg_Hydralisk_Den:
                return techProgression.isSpawningPool();
            case Zerg_Lair:
                return numHatcheries() > 0 && techProgression.isSpawningPool();
            case Zerg_Spire:
                return techProgression.isLair();
            default:
                return false;
        }
    }

    private boolean canScheduleUpgrade(UpgradeType upgradeType) {
        TechProgression techProgression = gameState.getTechProgression();
        switch(upgradeType) {
            case Metabolic_Boost:
                return techProgression.isSpawningPool();
            case Muscular_Augments:
            case Grooved_Spines:
                return techProgression.isHydraliskDen();
                default:
                return false;
        }
    }

    private void schedulePlannedItems() {
        if (productionQueue.size() == 0) {
            return;
        }

        Player self = game.self();

        // Loop through items until we exhaust queue or we break because we can't consume top item
        // Call method to attempt to build that type, if we can't build return false and break the loop

        HashSet<PlannedItem> scheduledPlans = gameState.getPlansScheduled();

        List<PlannedItem> requeuePlannedItems = new ArrayList<>();
        boolean skipSchedule = false;
        for (int i = 0; i < productionQueue.size(); i++) {
            if (skipSchedule) {
                break;
            }

            boolean canSchedule = false;
            // If we can't plan, we'll put it back on the queue
            final PlannedItem plannedItem = productionQueue.poll();
            if (plannedItem == null) {
                continue;
            }

            // Don't block the queue if the plan cannot be executed
            if (!canSchedulePlan(plannedItem)) {
                continue;
            }

            PlanType planType = plannedItem.getType();

            if (skipSchedule) {
                requeuePlannedItems.add(plannedItem);
                continue;
            }

            switch (planType) {
                case BUILDING:
                    canSchedule = scheduleBuildingItem(plannedItem);
                    if (!canSchedule) {
                        skipSchedule = true;
                    }
                    break;
                case UNIT:
                    canSchedule = scheduleUnitItem(plannedItem);
                    if (!canSchedule) {
                        skipSchedule = true;
                    }
                    break;
                case UPGRADE:
                    canSchedule = scheduleUpgradeItem(self, plannedItem);
                    if (!canSchedule) {
                        skipSchedule = true;
                    }
                    break;
            }

            if (canSchedule) {
                scheduledPlans.add(plannedItem);
            } else {
                requeuePlannedItems.add(plannedItem);
                if (plannedItem.isBlockOtherPlans()) {
                    break;
                }
            }
        }

        // Requeue
        for (PlannedItem plannedItem: requeuePlannedItems) {
            productionQueue.add(plannedItem);
        }
    }

    // TODO: Refactor this into WorkerManager or a Buildingmanager (TechManager)?
    // These PlannedItems will not work through state machine in same way as Unit and Buildings
    // This is a bit of a HACK until properly maintained
    private void buildUpgrades() {
        HashSet<PlannedItem> scheduledPlans = gameState.getPlansScheduled();
        if (scheduledPlans.size() == 0) {
            return;
        }

        HashSet<Unit> unitsExecutingPlan = new HashSet<>();
        List<Map.Entry<Unit, PlannedItem>> scheduledUpgradeAssignments = gameState.getAssignedPlannedItems().entrySet()
                .stream()
                .filter(assignment -> assignment.getValue().getType() == PlanType.UPGRADE)
                .collect(Collectors.toList());

        for (Map.Entry<Unit, PlannedItem> entry: scheduledUpgradeAssignments) {
            final Unit unit = entry.getKey();
            final PlannedItem plannedItem = entry.getValue();
            if (buildUpgrade(unit, plannedItem)) {
                unitsExecutingPlan.add(unit);
                scheduledPlans.remove(plannedItem);
                plannedItem.setState(PlanState.BUILDING); // TODO: This is awkward
                gameState.getPlansBuilding().add(plannedItem);
            }
        }

        // Remove executing plans from gameState.getAssignedPlannedItems()
        for (Iterator<Unit> it = unitsExecutingPlan.iterator(); it.hasNext(); ) {
            Unit u = it.next();
            gameState.getAssignedPlannedItems().remove(u);
        }
    }
    // Track planned items that are morphing
    // BUILD -> MORPH
    // Buildings and units
    private void plannedItemToMorphing(PlannedItem plannedItem) {
        final UnitType unitType = plannedItem.getPlannedUnit();
        ResourceCount resourceCount = gameState.getResourceCount();
        resourceCount.unreserveUnit(unitType);

        if (unitType == UnitType.Zerg_Drone) {
            plannedWorkers -= 1;
        }

        if (unitType.isBuilding()) {
            scheduledBuildings -= 1;
        }

        TechProgression techProgression = this.gameState.getTechProgression();

        switch(unitType) {
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
        }

        gameState.getPlansBuilding().remove(plannedItem);
        plannedItem.setState(PlanState.MORPHING);
        gameState.getPlansMorphing().add(plannedItem);
    }

    private void plannedItemToComplete(Unit unit, PlannedItem plannedItem) {
        gameState.getPlansBuilding().remove(plannedItem);
        gameState.getPlansMorphing().remove(plannedItem);
        plannedItem.setState(PlanState.COMPLETE);
        gameState.getPlansComplete().add(plannedItem);
        gameState.getAssignedPlannedItems().remove(unit);
    }

    // TODO: Handle in BaseManager (ManagedUnits that are buildings. ManagedBuilding?)
    private boolean buildUpgrade(Unit unit, PlannedItem plannedItem) {
        final UpgradeType upgradeType = plannedItem.getPlannedUpgrade();
        if (game.canUpgrade(upgradeType, unit)) {
            unit.upgrade(upgradeType);
        }

        ResourceCount resourceCount = gameState.getResourceCount();

        if (unit.isUpgrading()) {
            resourceCount.unreserveUpgrade(upgradeType);
            return true;
        }
        return false;
    }

    // PLANNED -> SCHEDULED
    // Allow one building to be scheduled if resources aren't available.
    private boolean scheduleBuildingItem(PlannedItem plannedItem) {
        // Can we afford this unit?
        UnitType building = plannedItem.getPlannedUnit();
        ResourceCount resourceCount = gameState.getResourceCount();
        int predictedReadyFrame = gameState.frameCanAffordUnit(building, currentFrame);
        if (scheduledBuildings > 0 && resourceCount.canAffordUnit(building)) {
            return false;
        }

        // TODO: Assign building location from building location planner
        if (plannedItem.getBuildPosition() == null) {
            plannedItem.setBuildPosition(game.getBuildLocation(building, mainBase.getLocation(), 128, true));
        }

        scheduledBuildings += 1;
        resourceCount.reserveUnit(building);
        plannedItem.setPredictedReadyFrame(predictedReadyFrame);
        plannedItem.setState(PlanState.SCHEDULE);
        return true;
    }

    private boolean scheduleUnitItem(PlannedItem plannedItem) {
        UnitType unit = plannedItem.getPlannedUnit();
        ResourceCount resourceCount = gameState.getResourceCount();
        if (resourceCount.canAffordUnit(unit)) {
            return false;
        }

        if (!resourceCount.canScheduleLarva(gameState.numLarva())) {
            return false;
        }

        resourceCount.reserveUnit(unit);
        plannedItem.setState(PlanState.SCHEDULE);
        return true;
    }

    private boolean scheduleUpgradeItem(Player self, PlannedItem plannedItem) {
        final UpgradeType upgrade = plannedItem.getPlannedUpgrade();
        ResourceCount resourceCount = gameState.getResourceCount();

        if (resourceCount.canAffordUpgrade(upgrade)) {
            return false;
        }

        for (Unit unit : self.getUnits()) {
            UnitType unitType = unit.getType();

            if (unitType == upgrade.whatUpgrades() && !gameState.getAssignedPlannedItems().containsKey(unit)) {
                gameState.getAssignedPlannedItems().put(unit, plannedItem);
                plannedItem.setState(PlanState.SCHEDULE);
                resourceCount.reserveUpgrade(upgrade);
                return true;
            }
        }

        return false;
    }

    public void onUnitComplete(Unit unit) {
        assignUnit(unit);
    }

    // TODO: Set curPriority PER type
    // Only allow items of the curPriority to attempt assignment
    private void assignUnit(Unit unit) {
        Player self = game.self();
        if (unit.getPlayer() != self) {
            return;
        }



        UnitType unitType = unit.getType();
        // TODO: Move to a building manager or base manager
        if (unitType == UnitType.Zerg_Extractor) {
            gameState.getGeyserAssignments().put(unit, new HashSet<>());
        }

        if (unitType == UnitType.Zerg_Overlord) {
            gameState.setPlannedSupply(Math.max(0, gameState.getPlannedSupply() - unitType.supplyProvided()));
        }

        if (unitType == UnitType.Zerg_Hatchery) {
            // Account for macro hatch
            if (closestBaseToUnit(unit, bwem.getMap().getBases()).getLocation().getDistance(unit.getTilePosition()) < 1) {
                buildBase(unit);
            } else {
                macroHatcheries.add(unit);
            }

            plannedHatcheries -= 1;
            // TODO: How are we getting here?
            if (plannedHatcheries < 0) {
                plannedHatcheries = 0;
            }
        }
    }

    // TODO: Should there be special logic here for handling the drones?
    // Need to handle cancel case (building about to die, extractor trick, etc.)
    public void onUnitMorph(Unit unit) {
        HashMap<Unit, PlannedItem> assignedPlannedItems = gameState.getAssignedPlannedItems();
        if (assignedPlannedItems.containsKey(unit)) {
            PlannedItem plannedItem = gameState.getAssignedPlannedItems().get(unit);
            plannedItemToMorphing(plannedItem);
        }

        clearAssignments(unit);
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
            clearAssignments(unit);
            // BUG: It seems that the unit passed here is the extractor, drone was destroyed
            //plannedItemToComplete(gameState.getAssignedPlannedItems().get(unit));
            //gameState.getAssignedPlannedItems().remove(unit);
        }
    }

    public void onUnitDestroy(Unit unit) {
        Player self = game.self();
        if (unit.getPlayer() != self) {
            return;
        }

        updateTechOnDestroy(unit);

        clearAssignments(unit);
    }

    private void updateTechOnDestroy(Unit unit) {
        TechProgression techProgression = this.gameState.getTechProgression();
        switch (unit.getType()) {
            case Zerg_Spawning_Pool:
                techProgression.setSpawningPool(false);
            case Zerg_Hydralisk_Den:
                techProgression.setHydraliskDen(false);
        }
    }

    /**
     * Remove a unit from all data stores
     *
     * @param unit unit to remove
     */
    // TODO: COMPLETE vs Requeue logic
    private void clearAssignments(Unit unit) {
        if (bases.contains(unit)) {
            bases.remove(unit);
        }


        // Requeue PlannedItems
        // Put item back onto the queue with greater importance
        if (gameState.getAssignedPlannedItems().containsKey(unit)) {
            PlannedItem plannedItem = gameState.getAssignedPlannedItems().get(unit);
            // TODO: Bit of a hack, need to handle cancel logic
            // This is because clearAssignments() requeues if plannedItem state is not complete
            plannedItemToComplete(unit, plannedItem);
        }

        /*
        if (gameState.getAssignedPlannedItems().containsKey(unit)) {
            PlannedItem plannedItem = gameState.getAssignedPlannedItems().get(unit);
            if (plannedItem.getState() != PlanState.COMPLETE) {
                plannedItem.setPriority(plannedItem.getPriority()-1);
                productionQueue.add(plannedItem);
            }
            gameState.getAssignedPlannedItems().remove(unit);
        }
         */
    }
}
