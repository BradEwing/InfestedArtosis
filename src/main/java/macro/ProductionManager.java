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
import state.GameState;
import planner.PlanState;
import planner.PlanType;
import planner.PlannedItem;
import planner.PlannedItemComparator;

import java.util.ArrayList;
import java.util.Collections;
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
    private boolean hasPool = false;
    private boolean hasPlannedPool = false;
    private boolean hasMetabolicBoost = false;
    private boolean hasPlannedMetabolicBoost = false;
    private boolean hasDen = false;
    private boolean hasPlannedDen = false;
    private boolean hasPlannedDenUpgrades = false;
    private boolean hasLurkers = false;
    private boolean hasPlannedLurkers = false;
    private boolean hasLair = false;
    private boolean hasPlannedLair = false;
    private boolean hasPlannedEvoChamberUpgrades1 = false;

    // TODO: Track in info manager / GameState with some sort of base planner class
    private int numSunkens = 0;
    // TODO: VERY HACKY! this is because current 9pool hardcodes an extractor, this must be aware of build order
    private int numExtractors = 1;
    private int numEvoChambers = 0;

    // These worker trackers do not track death or morph into building
    private int mineralWorkers = 0;
    private int gasWorkers = 0;

    private int reservedMinerals = 0;
    private int reservedGas = 0;
    private int currentPriority = 5;
    private int plannedSupply = 0;
    private int plannedHatcheries = 1; // Start with 1 because we decrement with initial hatch
    private int plannedWorkers = 0;

    private HashSet<Unit> bases = new HashSet<>();
    private HashSet<Base> baseLocations = new HashSet<>();
    private HashSet<Unit> macroHatcheries = new HashSet<>();

    // TODO: Queue populated with an information manager / strategy planner
    private PriorityQueue<PlannedItem> productionQueue = new PriorityQueue<>(new PlannedItemComparator());

    public ProductionManager(Game game, BWEM bwem, GameState gameState) {
        this.game = game;
        this.bwem = bwem;
        this.gameState = gameState;

        List<PlannedItem> items = ninePool();
        for (PlannedItem plannedItem: items) {
            productionQueue.add(plannedItem);
        }
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
        mainBase = closestBaseToUnit(initialHatch, allBases);
        baseLocations.add(mainBase);
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

    private void planBase() {
        Base base = findNewBase();
        // all possible bases are taken!
        if (base == null) {
            return;
        }

        productionQueue.add(new PlannedItem(UnitType.Zerg_Hatchery, currentPriority-1, true, base.getLocation()));
        baseLocations.add(base);
    }

    private List<PlannedItem> ninePool() {
        List<PlannedItem> list = new ArrayList<>();
        list.add(new PlannedItem(UnitType.Zerg_Drone, 0, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 0, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 0, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 0, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 0, false));
        list.add(new PlannedItem(UnitType.Zerg_Spawning_Pool, 1, true));
        list.add(new PlannedItem(UnitType.Zerg_Overlord, 2, false));
        list.add(new PlannedItem(UnitType.Zerg_Zergling, 3, false));
        list.add(new PlannedItem(UnitType.Zerg_Zergling, 3, false));
        list.add(new PlannedItem(UnitType.Zerg_Zergling, 3, false));
        return list;
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
    }

    // TODO: Determine why some workers go and stay idle
    public void onFrame() {

        debug();

        planItems();
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
        return (gameState.getMineralAssignments().size() * 2) + (gameState.getGeyserAssignments().size() * 3);
    }

    private int numWorkers() {
        return gameState.getMineralWorkers() + gameState.getGeyserWorkers();
    }

    private void planItems() {
        Player self = game.self();
        // Macro builder kicks in at 10 supply
        if (!isPlanning && self.supplyUsed() < PLAN_AT_SUPPY && productionQueue.size() > 0) {
            return;
        }

        // Allow buildings to arbitrary queue size
        // Always allow hatch to enter queue even if we're at max size (means we are throttled on bandwith)

        // 2 Types of Hatch:
        // Base Hatch - Setup resources to assign workers, add to base data
        // Macro Hatch - Take a macro hatch every other time
        // Limit to 3 plannedHatch to prevent queue deadlock
        if ((canAffordHatch(self) || isNearMaxExpectedWorkers()) && plannedHatcheries < 3) {
            plannedHatcheries += 1;
            if (((baseLocations.size() + macroHatcheries.size()) % 2) != 0) {
                planBase();
            } else {
                productionQueue.add(new PlannedItem(UnitType.Zerg_Hatchery, currentPriority, true));
            }
        }

        if (!hasPlannedLair && !hasLair && self.supplyUsed() > 45) {
            productionQueue.add(new PlannedItem(UnitType.Zerg_Lair, currentPriority, true));
            hasPlannedLair = true;
        }

        // One extractor per base
        // TODO: account for bases with no gas or 2 gas
        if (numExtractors < bases.size()) {
            numExtractors += 1;
            productionQueue.add(new PlannedItem(UnitType.Zerg_Extractor, currentPriority, true));
        }

        // Build at 10 workers if not part of initial build order
        if (!hasPlannedPool && !hasPool && self.supplyUsed() > 20) {
            productionQueue.add(new PlannedItem(UnitType.Zerg_Spawning_Pool, currentPriority, true));
            hasPlannedPool = true;
        }
        // Plan hydra den, why not?
        if (!hasPlannedDen && !hasDen && self.supplyUsed() > 40) {
            productionQueue.add(new PlannedItem(UnitType.Zerg_Hydralisk_Den, currentPriority, true));
            hasPlannedDen = true;
        }

        // Evolution Chamber
        // Plan 2 and first round of upgrades
        if (numEvoChambers < 2 && self.supplyUsed() > 50) {
            productionQueue.add(new PlannedItem(UnitType.Zerg_Evolution_Chamber, currentPriority, true));
            productionQueue.add(new PlannedItem(UnitType.Zerg_Evolution_Chamber, currentPriority, true));
            numEvoChambers += 2;
        }

        // TODO: Spire

        // TODO: Queen's Nest

        // TODO: Sunken Colonies
        // For now, plan 2 sunkens per base we take
        // Give them high priority, macro tends to be strong
        /*
        if (numSunkens < (bases.size())) {
            productionQueue.add(new PlannedItem(UnitType.Zerg_Creep_Colony, currentPriority-2, true));
            productionQueue.add(new PlannedItem(UnitType.Zerg_Sunken_Colony, currentPriority-1, true));
            // TODO: Track Planned + Actually built rather than here
            numSunkens += 1;
        }
         */

        // TODO: Spore Colonies

        // NOTE: Always let upgrades to enter the queue, we take them greedily
        // Plan tech / upgrades
        // The former should at least be driven by a higher level (strategy) manager
        // For now, greedily plan upgrades

        // TODO: Figure out why metabolic boost is not upgrading, why only 1 den upgrade is triggering
        /** Ling Upgrades **/
        if (hasPool && !hasPlannedMetabolicBoost && !hasMetabolicBoost) {
            productionQueue.add(new PlannedItem(UpgradeType.Metabolic_Boost, currentPriority));
            hasPlannedMetabolicBoost = true;
        }

        /** Hydra Upgrades */
        if (hasDen && !hasPlannedDenUpgrades) {
            productionQueue.add(new PlannedItem(UpgradeType.Muscular_Augments, currentPriority));
            productionQueue.add(new PlannedItem(UpgradeType.Grooved_Spines, currentPriority));
            hasPlannedDenUpgrades = true;
        }

        // Just take first level of upgrades for now
        if (numEvoChambers > 0 && hasPlannedEvoChamberUpgrades1) {
            productionQueue.add(new PlannedItem(UpgradeType.Zerg_Melee_Attacks, currentPriority));
            productionQueue.add(new PlannedItem(UpgradeType.Zerg_Missile_Attacks, currentPriority));
            productionQueue.add(new PlannedItem(UpgradeType.Zerg_Carapace, currentPriority));
            hasPlannedEvoChamberUpgrades1 = true;
        }

        /** For now, only subject unit production to queue size */
        // Base queue size is 3, increases per hatch
        if (productionQueue.size() >= 3 + (baseLocations.size() * 3) + (macroHatcheries.size() * 3)) {
            return;
        }

        // Once we come here, we never stop
        isPlanning = true;

        // Plan supply
        // If negative, trigger higher priority for overlord
        final int supplyRemaining = self.supplyTotal() - self.supplyUsed();
        if (supplyRemaining + plannedSupply < 5 && self.supplyUsed() < 400) {
            plannedSupply += 8;
            productionQueue.add(new PlannedItem(UnitType.Zerg_Overlord, currentPriority-1, false));
        } else if (supplyRemaining + plannedSupply < 0 && self.supplyUsed() < 400) {
            plannedSupply += 8;
            productionQueue.add(new PlannedItem(UnitType.Zerg_Overlord, currentPriority-2, false));
        }

        // Plan workers
        // This should be related to num bases + aval min patches and geysers, limited by army and potentially higher level strat info
        // For now, set them to be 1/3 of total supply
        // Limit the number of drones in queue, or they will crowd out production!
        if (plannedWorkers < 3 && numWorkers() < 80 && numWorkers() < expectedWorkers() && self.supplyUsed() < 400) {
            plannedWorkers += 1;
            productionQueue.add(new PlannedItem(UnitType.Zerg_Drone, currentPriority, false));
        }

        // Plan army
        if (hasPool && self.supplyUsed() < 400) {
            productionQueue.add(new PlannedItem(UnitType.Zerg_Zergling, currentPriority, false));
        }

        if (hasDen && self.supplyUsed() < 400) {
            productionQueue.add(new PlannedItem(UnitType.Zerg_Hydralisk, currentPriority, false));
        }


        // TODO: Tech upgrades
        /*
        if (hasDen && hasLair && !hasPlannedLurkers) {
            productionQueue.add(new PlannedItem(, currentPriority));
            hasPlannedLurkers = true;
        }

         */
    }

    private boolean canAffordHatch(Player self) {
        return self.minerals() - reservedMinerals > ((1 + plannedHatcheries) * 300);
    }

    private boolean isNearMaxExpectedWorkers() {
        return ((expectedWorkers() * (1 + plannedHatcheries)) - numWorkers() < 6);
    }

    // TODO: Planned item priority on frame?
    //  - If failed to execute after X frames, increase priority value
    //  - Track number of retries per PlannedItem, continual failure can warrant dropping from queue entirely and/or exponential backoff
    public void schedulePlannedItems() {
        Player self = game.self();
        int currentUnitAssignAttempts = 0; // accept 3 failures in planning
        int curPriority = productionQueue.peek().getPriority();

        // Loop through items until we exhaust queue or we break because we can't consume top item
        // Call method to attempt to build that type, if we can't build return false and break the loop
        // TODO: What to do when current planned item can never be executed

        HashSet<PlannedItem> scheduledPlans = gameState.getPlansScheduled();
        if (scheduledPlans.size() > 1) {
            List<PlannedItem> sortedScheduledPlans = scheduledPlans.stream().collect(Collectors.toList());
            Collections.sort(sortedScheduledPlans, new PlannedItemComparator());
            int schedulePriority = sortedScheduledPlans.get(0).getPriority();
            curPriority = Math.min(curPriority, schedulePriority);
        }


        List<PlannedItem> requeuePlannedItems = new ArrayList<>();
        // TODO: Importance logic?
        for (int i = 0; i < productionQueue.size(); i++) {
            boolean canSchedule = false;
            // If we can't plan, we'll put it back on the queue
            // TODO: Queue backoff, prioritization
            final PlannedItem plannedItem = productionQueue.poll();
            if (plannedItem == null) {
                continue;
            }

            // Only let current priority through the queue
            if (curPriority < plannedItem.getPriority()) {
                requeuePlannedItems.add(plannedItem);
                break;
            }

            switch (plannedItem.getType()) {
                case BUILDING:
                    canSchedule = scheduleBuildingItem(self, plannedItem);
                    break;
                case UNIT:
                    if (currentUnitAssignAttempts < 3) {
                        canSchedule = scheduleUnitItem(self, plannedItem);
                    }
                    break;
                case UPGRADE:
                    canSchedule = scheduleUpgradeItem(self, plannedItem);
                    break;
            }

            if (!canSchedule) {
                if (plannedItem.getType() == PlanType.UNIT) {
                    currentUnitAssignAttempts += 1;
                }
                requeuePlannedItems.add(plannedItem);
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
        plannedSupply = Math.max(0, plannedSupply - unitType.supplyProvided());

        if (unitType == UnitType.Zerg_Drone) {
            plannedWorkers -= 1;
        }

        // TODO: Execute this in own method w/ switch case
        if (unitType == UnitType.Zerg_Hydralisk_Den) {
            hasDen = true;
            //hasPlannedDen = false; // only plan 1
        } else if (unitType == UnitType.Zerg_Spawning_Pool) {
            hasPool = true;
            //hasPlannedPool = false; // TODO: set this when unit completes
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
        final UnitType plannedUnit = plannedItem.getPlannedUnit();
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

            //System.out.printf("assignUpgradeItem(), required: [%s], unitType: [%s] \n", upgrade.whatUpgrades(), unitType);
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

        if (unitType == UnitType.Zerg_Hatchery) {
            // Account for macro hatch
            if (closestBaseToUnit(unit, bwem.getMap().getBases()).getLocation().getDistance(unit.getTilePosition()) < 1) {
                buildBase(unit);
            } else {
                macroHatcheries.add(unit);
            }

            plannedHatcheries -= 1; // TODO: change this when building starts?
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

        clearAssignments(unit);
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
