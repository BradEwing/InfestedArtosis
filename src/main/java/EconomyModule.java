import bwapi.Color;
import bwapi.Game;
import bwapi.Player;
import bwapi.Text;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.UpgradeType;

import bwem.BWEM;
import bwem.Base;
import bwem.Mineral;
import planner.PlanType;
import planner.PlannedItem;
import planner.PlannedItemComparator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

// TODO: There is economy information here, build order and strategy. refactor
// Possible arch: GATHER GAME STATE -> PLAN -> EXECUTE
// STRATEGY -> BUILD ORDER (QUEUE) -> BUILD / ECONOMY MANAGEMENT (rebalance workers) (this file should eventually only be final step)
//
public class EconomyModule {

    private static int BASE_MINERAL_DISTANCE = 300;
    private static int PLAN_AT_SUPPY = 20;

    private Game game;
    private BWEM bwem; // TODO: This seems like a code smell?
    private Base mainBase; // TODO: Populate/track from info manager

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

    // These worker trackers do not track death or morph into building
    private int mineralWorkers = 0;
    private int gasWorkers = 0;

    private int reservedMinerals = 0;
    private int reservedGas = 0;
    private int currentPriority = 5;
    private int plannedSupply = 0;
    private int plannedHatcheries = 1; // Start with 1 because we decrement with initial hatch
    private int plannedWorkers = 0;

    private HashSet<Unit> assignedWorkers = new HashSet<>();

    // TODO: Use bwem.Mineral and bwem.Geyser?
    private HashMap<Unit, HashSet<Unit>> geyserAssignments = new HashMap<>();
    private HashMap<Unit, HashSet<Unit>> mineralAssignments = new HashMap<>();

    private HashSet<Unit> bases = new HashSet<>();
    private HashSet<Base> baseLocations = new HashSet<>();
    private HashMap<Base, HashSet<Unit>> baseMineralAssignments = new HashMap<>();
    private HashSet<Unit> macroHatcheries = new HashSet<>();

    // TODO: Queue populated with an information manager / strategy planner
    private PriorityQueue<PlannedItem> productionQueue = new PriorityQueue<>(new PlannedItemComparator());
    // TODO: Remove when unit dies
    private HashMap<Unit, PlannedItem> assignedPlannedItems = new HashMap<>();

    public EconomyModule(Game game, BWEM bwem) {
        this.game = game;
        this.bwem = bwem;

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

        for (Unit unit: game.getAllUnits()) {
            if (game.self() != unit.getPlayer()) {
                continue;
            }

            if (unit.getType() == UnitType.Zerg_Drone) {
                assignUnit(unit);
            }
        }
    }

    private void debugProductionQueue() {
        int numDisplayed = 0;
        int x = 4;
        int y = 64;
        for (PlannedItem plannedItem : productionQueue) {
            game.drawTextScreen(x, y, plannedItem.getName(), Text.Green);
            y += 8;
            numDisplayed += 1;
            if (numDisplayed == 10) {
                break;
            }
        }

        if (numDisplayed > productionQueue.size()) {
            game.drawTextScreen(x, y, String.format("... %s more planned items", productionQueue.size() - numDisplayed), Text.GreyGreen);
        }
    }

    private void debugBaseStats() {

    }

    // TODO: display this as a counter on the existing base
    // TODO: There's a bug where initial workers aren't assigned
    private void debugMineralPatches() {
        for (Unit mineral: mineralAssignments.keySet()) {
            TilePosition tilePosition = mineral.getTilePosition();
            game.drawTextMap(tilePosition.getX() * 32, tilePosition.getY() * 32, String.valueOf(mineralAssignments.get(mineral).size()), Text.Yellow);
        }
    }

    // TODO: Ensure print out of production queue is displaying how much time is remaining
    private void debugInProgressQueue() {}

    // TODO: refactor
    // Method to add hatchery and surrounding mineral patches to internal data structures
    private void buildBase(Unit hatchery) {
        bases.add(hatchery);
        Base newBase = closestBaseToUnit(hatchery, bwem.getMap().getBases());
        //baseLocations.add(newBase);

        for (Mineral mineral: newBase.getMinerals()) {
            mineralAssignments.put(mineral.getUnit(), new HashSet<>());
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
            System.out.printf("distance: [%s], closestDistance: [%s], base: [%s]\n", distance, closestDistance, b);
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
        list.add(new PlannedItem(UnitType.Zerg_Drone, 0, false));
        list.add(new PlannedItem(UnitType.Zerg_Spawning_Pool, 1, true));
        list.add(new PlannedItem(UnitType.Zerg_Overlord, 2, false));
        list.add(new PlannedItem(UnitType.Zerg_Extractor, 3, true));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 3, false));
        list.add(new PlannedItem(UnitType.Zerg_Zergling, 4, false));
        list.add(new PlannedItem(UnitType.Zerg_Zergling, 4, false));
        list.add(new PlannedItem(UnitType.Zerg_Zergling, 4, false));
        return list;
    }

    // debug console messaging goes here
    private void debug() {
        // Log every 100 frames
        if (game.getFrameCount() % 100 == 0) {
            System.out.printf("Frame: %s, Reserved Minerals: %s, Planned Hatcheries: %s, Macro Hatcheries: %s, CurrentBases: %s\n",
                    game.getFrameCount(), reservedMinerals, plannedHatcheries, macroHatcheries.size(), baseLocations.size());
        }
        debugMineralPatches();
        debugProductionQueue();
    }

    // TODO: Determine why some workers go and stay idle
    public void onFrame() {

        debug();

        planItems();
        initiatePlannedItems();
        buildItems();

        for (Unit u: game.getAllUnits()) {
            if (u.getType().isWorker() && u.isIdle()) {
                // Workers can sometimes get stuck and idle, but this code seems to be aggressively removing workers
                //if (assignedWorkers.contains(u)) {
                //    assignedWorkers.remove(u);
                //}

                assignUnit(u);
            }
        }

        for (Unit u: bases) {
            game.drawCircleMap(u.getPosition(), BASE_MINERAL_DISTANCE, Color.Teal);
        }
    }

    // TODO
    // TODO: BIG
    private void reassignPlan() {

    }

    private int expectedWorkers() {
        return (mineralAssignments.size() * 2) + (geyserAssignments.size() * 3);
    }

    private int numWorkers() {
        return mineralWorkers + gasWorkers;
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
        if (canAffordHatch(self) || isNearMaxExpectedWorkers()) {
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

    public void initiatePlannedItems() {
        Player self = game.self();
        int currentUnitAssignAttempts = 0; // accept 3 failures in planning

        // Loop through items until we exhaust queue or we break because we can't consume top item
        // Call method to attempt to build that type, if we can't build return false and break the loop
        // TODO: What to do when current planned item can never be executed


        List<PlannedItem> requeuePlannedItems = new ArrayList<>();
        // TODO: Importance logic?
        for (int i = 0; i < productionQueue.size(); i++) {
            boolean canAssign = false;
            // If we can't plan, we'll put it back on the queue
            // TODO: Queue backoff, prioritization
            final PlannedItem plannedItem = productionQueue.poll();
            if (plannedItem == null) {
                continue;
            }
            switch (plannedItem.type) {
                case BUILDING:
                    canAssign = assignBuildingItem(self, plannedItem);
                    break;
                case UNIT:
                    if (currentUnitAssignAttempts < 3) {
                        canAssign = assignUnitItem(self, plannedItem);
                    }
                    break;
                case UPGRADE:
                    canAssign = assignUpgradeItem(self, plannedItem);
                    break;
            }

            if (!canAssign) {
                if (plannedItem.getType() == PlanType.UNIT) {
                    currentUnitAssignAttempts += 1;
                }
                requeuePlannedItems.add(plannedItem);
            }
        }

        // Requeue
        // TODO: Decrement importance in plannedItem?
        for (PlannedItem plannedItem: requeuePlannedItems) {
            productionQueue.add(plannedItem);
        }
    }

    public void buildItems() {
        // If no items, exit
        if (assignedPlannedItems.size() == 0) {
            return;
        }

        HashSet<Unit> unitsExecutingPlan = new HashSet<>();

        for (Map.Entry<Unit, PlannedItem> entry : assignedPlannedItems.entrySet()) {
            boolean isPlanExecuted = false;
            final Unit unit = entry.getKey();
            final PlannedItem plannedItem = entry.getValue();

            switch (plannedItem.type) {
                case UNIT:
                    isPlanExecuted = buildUnit(unit, plannedItem);
                    break;
                case BUILDING:
                    isPlanExecuted = buildBuilding(unit, plannedItem);
                    break;
                case UPGRADE:
                    isPlanExecuted = buildUpgrade(unit, plannedItem);
                    break;
            }

            if (isPlanExecuted) {
                unitsExecutingPlan.add(unit);
            }
        }

        // Remove executing plans from assignedPlannedItems
        for (Iterator<Unit> it = unitsExecutingPlan.iterator(); it.hasNext(); ) {
            Unit u = it.next();
            assignedPlannedItems.remove(u);
        }

    }

    private boolean buildBuilding(Unit unit, PlannedItem plannedItem) {
        final UnitType unitType = plannedItem.getPlannedUnit();
        if (game.canMake(unitType, unit)) {
            TilePosition buildPosition = plannedItem.getBuildPosition() != null ? plannedItem.getBuildPosition() : game.getBuildLocation(unitType, unit.getTilePosition());
            unit.build(unitType, buildPosition);
        }

        if (unit.isMorphing() || unit.isBeingConstructed()) {
            reservedMinerals -= unitType.mineralPrice();
            reservedGas -= unitType.gasPrice();

            // TODO: Execute this in own method w/ switch case
            if (unitType == UnitType.Zerg_Hydralisk_Den) {
                hasDen = true;
                hasPlannedDen = false; // only plan 1
            } else if (unitType == UnitType.Zerg_Spawning_Pool) {
                hasPool = true;
            } else if (unitType == UnitType.Zerg_Lair) {
                hasLair = true;
            }

            return true;
        }

        return false;
    }

    private boolean buildUnit(Unit unit, PlannedItem plannedItem) {
        final UnitType unitType = plannedItem.getPlannedUnit();
        if (game.canMake(unitType, unit)) {
            unit.morph(unitType);
        }

        if (unit.isMorphing()) {
            reservedMinerals -= unitType.mineralPrice();
            reservedGas -= unitType.gasPrice();
            plannedSupply = Math.max(0, plannedSupply - unitType.supplyProvided());
            if (unitType == UnitType.Zerg_Drone) {
                plannedWorkers -= 1;
            }
            return true;
        }

        return false;
    }

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

    private boolean assignBuildingItem(Player self, PlannedItem plannedItem) {
        // Can we afford this unit?
        final UnitType plannedUnit = plannedItem.getPlannedUnit();
        final int mineralPrice = plannedItem.getPlannedUnit().mineralPrice();
        final int gasPrice = plannedItem.getPlannedUnit().gasPrice();
        if (self.minerals() - reservedMinerals < mineralPrice || gasPrice - reservedGas < plannedUnit.gasPrice()) {
            return false;
        }

        // Attempt to find a builder
        for (Unit unit : self.getUnits()) {
            UnitType unitType = unit.getType();
            // If drone and not assigned, assign
            if (unitType == UnitType.Zerg_Drone && !assignedPlannedItems.containsKey(unit)) {
                assignedPlannedItems.put(unit, plannedItem);
                reservedMinerals += mineralPrice;
                reservedGas += gasPrice;
                return true;
            }
        }

        return false;
    }

    private boolean assignUnitItem(Player self, PlannedItem plannedItem) {
        // Can we afford this unit?
        final int mineralPrice = plannedItem.getPlannedUnit().mineralPrice();
        final int gasPrice = plannedItem.getPlannedUnit().gasPrice();
        if (self.minerals() - reservedMinerals < mineralPrice || self.gas() - reservedGas < gasPrice) {
            return false;
        }

        // Attempt to find a builder
        for (Unit unit : self.getUnits()) {
            UnitType unitType = unit.getType();
            // If drone and not assigned, assign
            if (unitType == UnitType.Zerg_Larva && !assignedPlannedItems.containsKey(unit)) {
                assignedPlannedItems.put(unit, plannedItem);
                reservedMinerals += mineralPrice;
                reservedGas += gasPrice;
                return true;
            }
        }

        return false;
    }

    private boolean assignUpgradeItem(Player self, PlannedItem plannedItem) {
        final UpgradeType upgrade = plannedItem.getPlannedUpgrade();
        final int mineralPrice = upgrade.mineralPrice();
        final int gasPrice = upgrade.gasPrice();
        if (self.minerals() - reservedMinerals < mineralPrice || self.gas() - reservedGas < gasPrice) {
            return false;
        }

        for (Unit unit : self.getUnits()) {
            UnitType unitType = unit.getType();

            System.out.printf("assignUpgradeItem(), required: [%s], unitType: [%s] \n", upgrade.whatUpgrades(), unitType);
            if (unitType == upgrade.whatUpgrades() && !assignedPlannedItems.containsKey(unit)) {
                assignedPlannedItems.put(unit, plannedItem);
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

        // TODO: Attempt to assign worker to closest hatch
        if (unit.getType().isWorker() && !assignedWorkers.contains(unit)) {
            // Assign 3 per geyser
            if (gasWorkers < (3 * geyserAssignments.size())) {
                for (Unit geyser: geyserAssignments.keySet()) {
                    HashSet<Unit> geyserUnits = geyserAssignments.get(geyser);
                    if (geyserUnits.size() < 3) {
                        unit.gather(geyser);
                        gasWorkers += 1;
                        assignedWorkers.add(unit);
                        geyserUnits.add(unit);
                        break;
                    }
                }
            } else {
                if (mineralWorkers < (2 * mineralAssignments.size())) {
                    // Assign 2 per patch
                    if (mineralWorkers < (2 * mineralAssignments.size())) {
                        for (Unit mineral: mineralAssignments.keySet()) {
                            HashSet<Unit>mineralUnits = mineralAssignments.get(mineral);
                            if (mineralUnits.size() < 2) {
                                unit.gather(mineral);
                                mineralWorkers += 1;
                                assignedWorkers.add(unit);
                                mineralUnits.add(unit);
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (unit.getType() == UnitType.Zerg_Extractor) {
            geyserAssignments.put(unit, new HashSet<>());
            reservedMinerals -= 50; // TODO: This is a hacky workaround
        }

        if (unit.getType() == UnitType.Zerg_Hatchery) {
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

    private List<Unit> unitsWithinDistance(Unit unit, List<Unit> unitList, int distance) {
        List<Unit> list = new ArrayList<>();
        for (Unit u: unitList) {
            if (unit.getDistance(u) < distance) {
                list.add(u);
            }
        }
        return list;
    }

    // TODO: util
    private int numUnits(UnitType target) {
        int numUnits = 0;
        for (Unit unit: game.getAllUnits()) {
            if (unit.getType() == target) {
                numUnits += 1;
            }
        }

        return numUnits;
    }

    // TODO: Track workers on individual mineral patch / gas
    // TODO: Remove workers from tracker when they die
    public void onUnitDestroy(Unit unit) {
        Player self = game.self();
        if (unit.getPlayer() != self) {
            return;
        }
        if (assignedWorkers.contains(unit)) {
            for (HashSet<Unit> workers: mineralAssignments.values()) {
                if (workers.contains(unit)) {
                    workers.remove(unit);
                    mineralWorkers -= 1;
                }
            }
            for (HashSet<Unit> workers: geyserAssignments.values()) {
                if (workers.contains(unit)) {
                    workers.remove(unit);
                    gasWorkers -= 1;
                }
            }
        }

        if (mineralAssignments.containsKey(unit)) {
            HashSet<Unit> mineralWorkers = mineralAssignments.get(unit);
            for (Unit worker: mineralWorkers) {
                assignedWorkers.remove(worker);
            }
            mineralWorkers.remove(unit);
        }

        // No idle gas workers after geyser destroyed
        if (geyserAssignments.containsKey(unit)) {
            HashSet<Unit> geyserWorkers = geyserAssignments.get(unit);
            for (Unit worker: geyserWorkers) {
                assignedWorkers.remove(worker);
            }
            geyserWorkers.remove(unit);
        }
    }
}
