package macro;

import bwapi.Color;
import bwapi.Game;
import bwapi.Player;
import bwapi.Race;
import bwapi.Text;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.UpgradeType;

import bwem.BWEM;
import bwem.Base;
import bwem.Mineral;
import info.GameState;
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
    private static int PLAN_AT_SUPPY = 20;

    private Game game;
    private BWEM bwem; // TODO: This seems like a code smell?
    private Base mainBase; // TODO: Populate/track from info manager

    private GameState gameState;

    // Track in GameState
    // isPlanning contingent on -> hitting min supply set by build order OR queue exhaust OR (MAYBE) enemy unit in base
    private boolean isPlanning = false;

    // TODO: Track this stuff in GameState
    private boolean hasPlannedPool = false;
    private boolean hasMetabolicBoost = false;
    // TODO: set off of strategy
    private boolean hasPlannedMetabolicBoost = false;
    private boolean hasPlannedDen = false;
    private boolean hasPlannedDenUpgrades = false;
    private boolean hasLurkers = false;
    private boolean hasPlannedLurkers = false;
    private boolean hasLair = false;
    private boolean hasPlannedLair = false;
    private boolean hasPlannedEvoChamberUpgrades1 = false;

    // TODO: Track in info manager / GameState with some sort of base planner class
    private int numSunkens = 0;

    private int numExtractors = 0;
    private int numEvoChambers = 0;

    // TODO: Determine from desired unit composition / active strategy
    // Because bot is only on hatch tech, only take 1 for now
    private int targetExtractors = 1;

    private int reservedMinerals = 0;
    private int reservedGas = 0;
    private int currentPriority = 5;
    private int plannedHatcheries = 1; // Start with 1 because we decrement with initial hatch
    private int plannedWorkers = 0;

    private HashSet<Unit> bases = new HashSet<>();
    private HashSet<Base> baseLocations = new HashSet<>();
    private HashSet<Unit> macroHatcheries = new HashSet<>();

    // TODO: Queue populated with an information manager / strategy planner
    private PriorityQueue<PlannedItem> productionQueue = new PriorityQueue<>(new PlannedItemComparator());

    public ProductionManager(Game game, BWEM bwem, GameState gameState, List<PlannedItem> initialBuildOrder) {
        this.game = game;
        this.bwem = bwem;
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
                    hasPlannedMetabolicBoost = true;
                }
            }


            this.productionQueue.add(plannedItem);
        }
    }

    private void debugAssignedItemsQueue() {

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

    // TODO: refactor
    // Method to add hatchery and surrounding mineral patches to internal data structures
    private void buildBase(Unit hatchery) {
        bases.add(hatchery);
        List<Base> bases = bwem.getMap().getBases();
        Base newBase = closestBaseToUnit(hatchery, bases);
        //baseLocations.add(newBase);

        for (Mineral mineral: newBase.getMinerals()) {
            gameState.getMineralAssignments().put(mineral.getUnit(), new HashSet<>());
        }
    }

    // TODO: Refactor
    private Base findNewBase() {
        Base closestUnoccupiedBase = null;
        double closestDistance = Double.MAX_VALUE;
        for (Base b : bwem.getMap().getBases()) {
            if (baseLocations.contains(b)) {
                continue;
            }

            double distance = mainBase.getLocation().getDistance(b.getLocation());

            if (distance < closestDistance) {
                closestUnoccupiedBase = b;
                closestDistance = distance;
            }
        }

        return closestUnoccupiedBase;
    }

    private void addBaseToGameState(Base base) {
        HashMap<Base, HashSet<ManagedUnit>> gatherToBaseAssignments = this.gameState.getGatherersAssignedToBase();
        gatherToBaseAssignments.put(base, new HashSet<>());
        baseLocations.add(base);
    }

    private void planBase() {
        Base base = findNewBase();
        // all possible bases are taken!
        if (base == null) {
            return;
        }

        productionQueue.add(new PlannedItem(UnitType.Zerg_Hatchery, currentPriority, true, true, base.getLocation()));
        addBaseToGameState(base);
    }

    // debug console messaging goes here
    private void debug() {
        // Log every 100 frames
        if (game.getFrameCount() % 100 == 0) {
            //System.out.printf("Frame: %s, Reserved Minerals: %s, Planned Hatcheries: %s, Macro Hatcheries: %s, CurrentBases: %s" +
            //                " isPlanning: [%s]\n",
            //        game.getFrameCount(), reservedMinerals, plannedHatcheries, macroHatcheries.size(), baseLocations.size(), isPlanning);
        }
        debugProductionQueue();
        debugInProgressQueue();
        debugScheduledPlannedItems();
    }

    // TODO: Determine why some workers go and stay idle
    public void onFrame() {

        debug();

        currentPriority = game.getFrameCount();

        plan();
        schedulePlannedItems();
        //buildItems();
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
        return (5 + (bases.size() * 5)) + (gameState.getGeyserAssignments().size() * 3);
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
        // Base Hatch - Setup resources to assign workers, add to base data
        // Macro Hatch - Take a macro hatch every other time
        // Limit to 3 plannedHatch to prevent queue deadlock
        if (!isAllIn && (canAffordHatch(self) || (isNearMaxExpectedWorkers() && canAffordHatchSaturation())) && plannedHatcheries < 3) {
            plannedHatcheries += 1;
            if ((numHatcheries() % 2) != 0) {
                planBase();
            } else {
                productionQueue.add(new PlannedItem(UnitType.Zerg_Hatchery, currentPriority / 3, true, true));
            }
        }

        /*
        if (!hasPlannedLair && !hasLair && self.supplyUsed() > 45) {
            productionQueue.add(new PlannedItem(UnitType.Zerg_Lair, currentPriority, true));
            hasPlannedLair = true;
        }
         */

        // One extractor per base
        // TODO: account for bases with no gas or 2 gas
        if (!isAllIn && numExtractors < bases.size() && numExtractors < targetExtractors) {
            numExtractors += 1;
            productionQueue.add(new PlannedItem(UnitType.Zerg_Extractor, currentPriority, true, false));
        }

        TechProgression techProgression = this.gameState.getTechProgression();

        // Build at 10 workers if not part of initial build order
        if (!hasPlannedPool && !techProgression.isSpawningPool() && self.supplyUsed() > 20) {
            productionQueue.add(new PlannedItem(UnitType.Zerg_Spawning_Pool, currentPriority / 4, true, true));
            hasPlannedPool = true;
        }
        // Plan hydra den, why not?
        // HACK
        // TODO: Move this out of here

        int hydraSupplyThreshold = game.enemy().getRace() == Race.Protoss ? 20 : 40;
        if (!isAllIn && techProgression.isSpawningPool() && !hasPlannedDen && !techProgression.isHydraliskDen() && self.supplyUsed() > hydraSupplyThreshold) {
            productionQueue.add(new PlannedItem(UnitType.Zerg_Hydralisk_Den, currentPriority, true, false));
            hasPlannedDen = true;
        }
    }

    private void planUpgrades(Player self, Boolean isAllIn) {
        TechProgression techProgression = this.gameState.getTechProgression();
        // TODO: Figure out why metabolic boost is not upgrading, why only 1 den upgrade is triggering
        /** Ling Upgrades **/
        if (!isAllIn && techProgression.isSpawningPool() && !hasPlannedMetabolicBoost && !hasMetabolicBoost) {
            productionQueue.add(new PlannedItem(UpgradeType.Metabolic_Boost, currentPriority, false));
            hasPlannedMetabolicBoost = true;
        }

        /** Hydra Upgrades */
        if (!isAllIn && techProgression.isHydraliskDen() && !hasPlannedDenUpgrades) {
            productionQueue.add(new PlannedItem(UpgradeType.Muscular_Augments, currentPriority, false));
            productionQueue.add(new PlannedItem(UpgradeType.Grooved_Spines, currentPriority, false));
            hasPlannedDenUpgrades = true;
        }

        // Just take first level of upgrades for now
        if (!isAllIn && numEvoChambers > 0 && hasPlannedEvoChamberUpgrades1) {
            productionQueue.add(new PlannedItem(UpgradeType.Zerg_Melee_Attacks, currentPriority, false));
            productionQueue.add(new PlannedItem(UpgradeType.Zerg_Missile_Attacks, currentPriority, false));
            productionQueue.add(new PlannedItem(UpgradeType.Zerg_Carapace, currentPriority, false));
            hasPlannedEvoChamberUpgrades1 = true;
        }
    }


    // planSupply checks if near supply cap or supply blocked
    private void planSupply(Player self) {
        final int supplyRemaining = self.supplyTotal() - self.supplyUsed();
        int plannedSupply = gameState.getPlannedSupply();
        if (supplyRemaining + plannedSupply < 5 && self.supplyUsed() < 400) {
            gameState.setPlannedSupply(plannedSupply+16);
            productionQueue.add(new PlannedItem(UnitType.Zerg_Overlord, currentPriority / 3, false, false));
        } else if (supplyRemaining + plannedSupply < 0 && self.supplyUsed() < 400) {
            gameState.setPlannedSupply(plannedSupply+16);
            productionQueue.add(new PlannedItem(UnitType.Zerg_Overlord, currentPriority / 2, false, true));
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

        TechProgression techProgression = this.gameState.getTechProgression();
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
        productionQueue.add(new PlannedItem(unitType, currentPriority, false, false));
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
        planUpgrades(self, isAllIn);

        // Plan supply
        planSupply(self);

        /** For now, only subject unit production to queue size */
        // Base queue size is 3, increases per hatch
        if (productionQueue.size() >= 3 + (numHatcheries() * 3)) {
            return;
        }

        planUnits(self, isAllIn);
    }

    private boolean canAffordHatchSaturation() {
        return ((numHatcheries() + plannedHatcheries) * 7) <= gameState.getMineralWorkers();
    }

    private boolean canAffordHatch(Player self) {
        return self.minerals() - reservedMinerals > ((1 + plannedHatcheries) * 300);
    }

    private boolean isNearMaxExpectedWorkers() {
        return ((expectedWorkers() * (1 + plannedHatcheries)) - numWorkers() < 0);
    }

    // TODO: Planned item priority on frame?
    //  - If failed to execute after X frames, increase priority value
    //  - Track number of retries per PlannedItem, continual failure can warrant dropping from queue entirely and/or exponential backoff
    public void schedulePlannedItems() {
        if (productionQueue.size() == 0) {
            return;
        }

        Player self = game.self();

        // Loop through items until we exhaust queue or we break because we can't consume top item
        // Call method to attempt to build that type, if we can't build return false and break the loop
        // TODO: What to do when current planned item can never be executed
        // This is done for supply deadlock, need to build logic for when tech prereq is destroyed

        HashSet<PlannedItem> scheduledPlans = gameState.getPlansScheduled();

        List<PlannedItem> requeuePlannedItems = new ArrayList<>();
        // Try to schedule one each of unit, building or upgrade per frame.
        boolean skipScheduleUnit = false;
        boolean skipScheduleBuilding = false;
        boolean skipScheduleUpgrade = false;
        for (int i = 0; i < productionQueue.size(); i++) {
            if (skipScheduleUnit && skipScheduleBuilding && skipScheduleUpgrade) {
                break;
            }

            // Don't over schedule units
            if (!skipScheduleUnit && scheduledPlans.size() >= 1) {
                skipScheduleUnit = true;
            }

            boolean canSchedule = false;
            // If we can't plan, we'll put it back on the queue
            // TODO: Queue backoff, prioritization
            final PlannedItem plannedItem = productionQueue.poll();
            if (plannedItem == null) {
                continue;
            }

            PlanType planType = plannedItem.getType();
            Boolean skipSchedule = (planType == PlanType.BUILDING && skipScheduleBuilding) ||
                    (planType == PlanType.UNIT && skipScheduleUnit) ||
                    (planType == PlanType.UPGRADE && skipScheduleUpgrade);

            if (skipSchedule) {
                requeuePlannedItems.add(plannedItem);
                continue;
            }

            switch (planType) {
                case BUILDING:
                    canSchedule = scheduleBuildingItem(self, plannedItem);
                    if (!canSchedule) {
                        skipScheduleBuilding = true;
                    }
                    break;
                case UNIT:
                    canSchedule = scheduleUnitItem(self, plannedItem);
                    if (!canSchedule) {
                        skipScheduleUnit = true;
                    }
                    break;
                case UPGRADE:
                    canSchedule = scheduleUpgradeItem(self, plannedItem);
                    if (!canSchedule) {
                        skipScheduleUpgrade = true;
                    }
                    break;
            }

            if (!canSchedule) {
                requeuePlannedItems.add(plannedItem);
                if (plannedItem.isBlockOtherPlans()) {
                    break;
                }
            } else {
                scheduledPlans.add(plannedItem);
            }
        }

        // Requeue
        // TODO: Decrement importance in plannedItem?
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
        reservedMinerals -= unitType.mineralPrice();
        reservedGas -= unitType.gasPrice();

        if (unitType == UnitType.Zerg_Drone) {
            plannedWorkers -= 1;
        }

        TechProgression techProgression = this.gameState.getTechProgression();

        // TODO: Execute this in own method w/ switch case
        if (unitType == UnitType.Zerg_Hydralisk_Den) {
            techProgression.setHydraliskDen(true);
            hasPlannedDen = false; // only plan 1
        } else if (unitType == UnitType.Zerg_Spawning_Pool) {
            techProgression.setSpawningPool(true);
            hasPlannedPool = false; // TODO: set this when unit completes
        } else if (unitType == UnitType.Zerg_Lair) {
            hasLair = true;
            //hasPlannedLair = false;
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

    // TODO: Handle in BuildingManager (ManagedUnits that are buildings. ManagedBuilding?)
    private boolean buildUpgrade(Unit unit, PlannedItem plannedItem) {
        final UpgradeType upgradeType = plannedItem.getPlannedUpgrade();
        if (game.canUpgrade(upgradeType, unit)) {
            unit.upgrade(upgradeType);
        }

        if (unit.isUpgrading()) {
            reservedMinerals -= upgradeType.mineralPrice();
            reservedGas -= upgradeType.gasPrice();
            return true;
        }
        return false;
    }

    // PLANNED -> SCHEDULED
    // This involves assigning
    private boolean scheduleBuildingItem(Player self, PlannedItem plannedItem) {
        // Can we afford this unit?
        final int mineralPrice = plannedItem.getPlannedUnit().mineralPrice();
        final int gasPrice = plannedItem.getPlannedUnit().gasPrice();

        if (self.minerals() - reservedMinerals < mineralPrice || gasPrice - reservedGas < gasPrice) {
            return false;
        }

        reservedMinerals += mineralPrice;
        reservedGas += gasPrice;
        plannedItem.setState(PlanState.SCHEDULE);
        return true;
    }

    private boolean scheduleUnitItem(Player self, PlannedItem plannedItem) {
        // Can we afford this unit?
        final int mineralPrice = plannedItem.getPlannedUnit().mineralPrice();
        final int gasPrice = plannedItem.getPlannedUnit().gasPrice();
        if (self.minerals() - reservedMinerals < mineralPrice || self.gas() - reservedGas < gasPrice) {
            return false;
        }

        reservedMinerals += mineralPrice;
        reservedGas += gasPrice;
        plannedItem.setState(PlanState.SCHEDULE);
        return true;
    }

    private boolean scheduleUpgradeItem(Player self, PlannedItem plannedItem) {
        final UpgradeType upgrade = plannedItem.getPlannedUpgrade();
        final int mineralPrice = upgrade.mineralPrice();
        final int gasPrice = upgrade.gasPrice();

        if (self.minerals() - reservedMinerals < mineralPrice || self.gas() - reservedGas < gasPrice) {
            return false;
        }

        for (Unit unit : self.getUnits()) {
            UnitType unitType = unit.getType();

            if (unitType == upgrade.whatUpgrades() && !gameState.getAssignedPlannedItems().containsKey(unit)) {
                gameState.getAssignedPlannedItems().put(unit, plannedItem);
                plannedItem.setState(PlanState.SCHEDULE);
                reservedMinerals += mineralPrice;
                reservedGas += gasPrice;
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

    private Base closestBaseToUnit(Unit unit, List<Base> baseList) {
        Base closestBase = null;
        int closestDistance = Integer.MAX_VALUE;
        for (Base b : baseList) {
            int distance = unit.getDistance(b.getLocation().toPosition());
            if (distance < closestDistance) {
                closestBase = b;
                closestDistance = distance;
            }
        }

        return closestBase;
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

        if (unit.getType() == UnitType.Zerg_Extractor) {
            reservedMinerals -= UnitType.Zerg_Extractor.mineralPrice();
            reservedGas -= UnitType.Zerg_Extractor.gasPrice();
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
