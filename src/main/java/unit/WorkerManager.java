package unit;

import bwapi.Game;
import bwapi.Player;
import bwapi.Text;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import planner.PlanState;
import planner.PlanType;
import planner.PlannedItemComparator;
import info.GameState;
import planner.PlannedItem;
import util.UnitDistanceComparator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WorkerManager {
    private Game game;

    private GameState gameState;

    private HashSet<ManagedUnit> assignedManagedWorkers = new HashSet<>();
    private HashSet<ManagedUnit> gatherers = new HashSet<>();
    private HashSet<ManagedUnit> mineralGatherers = new HashSet<>();
    private HashSet<ManagedUnit> larva = new HashSet<>();
    private HashSet<ManagedUnit> eggs = new HashSet<>();

    public WorkerManager(Game game, GameState gameState) {
        this.game = game;
        this.gameState = gameState;
    }

    public void onFrame() {
        checksLarvaDeadlock();
        handleLarvaDeadlock();

        assignScheduledPlannedItems();
    }

    public void onUnitComplete(ManagedUnit managedUnit) {
        // Assign larva, drones
        UnitType unitType = managedUnit.getUnitType();
        if (unitType == UnitType.Zerg_Drone) {
            assignWorker(managedUnit);
            return;
        }

        if (unitType == UnitType.Zerg_Larva) {
            larva.add(managedUnit);
            return;
        }
    }

    public void onUnitMorph(ManagedUnit managedUnit) {
        clearAssignments(managedUnit);
        if (managedUnit.getUnitType() == UnitType.Zerg_Drone) {
            assignWorker(managedUnit);
        }
        // eggs
        if (managedUnit.getUnitType() == UnitType.Zerg_Egg) {
            eggs.add(managedUnit);
        }
    }


    public void addManagedWorker(ManagedUnit managedUnit) {
        assignWorker(managedUnit);
    }

    // onUnitDestroy OR worker is being reassigned to non-worker role
    public void removeManagedWorker(ManagedUnit managedUnit) {
        clearAssignments(managedUnit);
    }

    public void removeMineral(Unit unit) {
        Map<Unit, HashSet<ManagedUnit>> mineralAssignments = gameState.getMineralAssignments();
        HashSet<ManagedUnit> mineralWorkers = mineralAssignments.get(unit);
        mineralAssignments.remove(unit);
        // TODO: Determine why this is null, it's causing crashes
        if (mineralWorkers == null) {
            //System.out.printf("no geyserWorkers found for unit: [%s]\n", unit);
            return;
        }
        for (ManagedUnit managedUnit: mineralWorkers) {
            clearAssignments(managedUnit);
            assignWorker(managedUnit);
        }
    }

    public void removeGeyser(Unit unit) {
        Map<Unit, HashSet<ManagedUnit>> geyserAssignments = gameState.getGeyserAssignments();
        HashSet<ManagedUnit> geyserWorkers = geyserAssignments.get(unit);
        geyserAssignments.remove(unit);
        // TODO: Determine why this is null, it's causing crashes
        if (geyserWorkers == null) {
            //System.out.printf("no geyserWorkers found for unit: [%s]\n", unit);
            return;
        }
        for (ManagedUnit managedUnit: geyserWorkers) {
            clearAssignments(managedUnit);
            assignWorker(managedUnit);
        }
    }

    // onExtractorComplete is called when an extractor is complete, to immediately pull 3 mineral gathering drones
    // onto the extractor
    public void onExtractorComplete() {
        final List<ManagedUnit> newGeyserWorkers = new ArrayList<>();

        // If less than 4 mineral workers, there are probably other problems
        if (mineralGatherers.size() < 4) {
            return;
        }

        for (ManagedUnit managedUnit: mineralGatherers) {
            if (newGeyserWorkers.size() >= 2) {
                break;
            }
            newGeyserWorkers.add(managedUnit);
        }

        for (ManagedUnit managedUnit: newGeyserWorkers) {
            clearAssignments(managedUnit);
            assignToGeyser(managedUnit);
        }
    }

    private void assignScheduledPlannedItems() {
        List<PlannedItem> scheduledPlans = gameState.getPlansScheduled().stream().collect(Collectors.toList());
        if (scheduledPlans.size() < 1) {
            return;
        }

        Collections.sort(scheduledPlans, new PlannedItemComparator());
        List<PlannedItem> assignedPlans = new ArrayList<>();

        boolean blockMorphDroneAssignment = false;
        boolean blockMorphLarvaAssignment = false;
        for (PlannedItem plannedItem: scheduledPlans) {
            // Are we broken here?
            if (blockMorphDroneAssignment && blockMorphLarvaAssignment) {
                break;
            }
            boolean didAssign = false;
            if (plannedItem.getType() == PlanType.BUILDING) {
                if (blockMorphDroneAssignment) {
                    continue;
                }
                didAssign = assignMorphDrone(plannedItem);
                if (!didAssign) {
                    blockMorphDroneAssignment = true;
                }
            } else if (plannedItem.getType() == PlanType.UNIT) {
                if (blockMorphLarvaAssignment) {
                    continue;
                }
                didAssign = assignMorphLarva(plannedItem);
                if (!didAssign) {
                    blockMorphLarvaAssignment = true;
                }
            }

            if (didAssign) {
                assignedPlans.add(plannedItem);
            }
        }

        HashSet<PlannedItem> buildingPlans = gameState.getPlansBuilding();
        for (PlannedItem plannedItem: assignedPlans) {
            scheduledPlans.remove(plannedItem);
            buildingPlans.add(plannedItem);
        }

        gameState.setPlansScheduled(scheduledPlans.stream().collect(Collectors.toCollection(HashSet::new)));
    }

    private void clearAssignments(ManagedUnit managedUnit) {
        if (assignedManagedWorkers.contains(managedUnit)) {
            for (HashSet<ManagedUnit> mineralWorkers: gameState.getMineralAssignments().values()) {
                if (mineralWorkers.contains(managedUnit)) {
                    gameState.setMineralWorkers(gameState.getMineralWorkers()-1);
                    mineralWorkers.remove(managedUnit);
                }
            }
            for (HashSet<ManagedUnit> geyserWorkers: gameState.getGeyserAssignments().values()) {
                if (geyserWorkers.contains(managedUnit)) {
                    gameState.setGeyserWorkers(gameState.getGeyserWorkers()-1);
                    geyserWorkers.remove(managedUnit);
                }
            }
        }

        eggs.remove(managedUnit);
        larva.remove(managedUnit);
        gatherers.remove(managedUnit);
        mineralGatherers.remove(managedUnit);
        assignedManagedWorkers.remove(managedUnit);
    }

    // Initial assignment onUnitComplete
    //
    private void assignWorker(ManagedUnit managedUnit) {
        // Assign 3 per geyser
        if (gameState.getGeyserWorkers() < (3 * gameState.getGeyserAssignments().size())) {
            assignToGeyser(managedUnit);
            return;
        }
        if (gameState.getMineralWorkers() < (2 * gameState.getMineralAssignments().size())) {
            assignToMineral(managedUnit);
            return;
        }

        assignedManagedWorkers.add(managedUnit);
        managedUnit.setRole(UnitRole.IDLE);
    }

    private void assignToMineral(ManagedUnit managedUnit) {
        Unit unit = managedUnit.getUnit();
        // Consider how many mineral workers are mining, compare to size of taken mineral patches
        // Gather all mineral patches, sort by distance
        // For each, check for patch with the least amount of workers
        int fewestMineralAssignments;
        if (gameState.getMineralWorkers() == 0) {
            fewestMineralAssignments = 0;
        } else {
            fewestMineralAssignments = gameState.getMineralAssignments().size() / gameState.getMineralWorkers() <= 0.5 ? 1 : 0;
        }
        List<Unit> claimedMinerals = gameState.getMineralAssignments().keySet().stream().collect(Collectors.toList());
        claimedMinerals.sort(new UnitDistanceComparator(unit));

        for (Unit mineral: claimedMinerals) {
            HashSet<ManagedUnit> mineralUnits = gameState.getMineralAssignments().get(mineral);
            if (mineralUnits.size() == fewestMineralAssignments) {
                managedUnit.setRole(UnitRole.GATHER);
                managedUnit.setGatherTarget(mineral);
                managedUnit.setHasNewGatherTarget(true);
                assignedManagedWorkers.add(managedUnit);
                gameState.setMineralWorkers(gameState.getMineralWorkers()+1);
                mineralUnits.add(managedUnit);
                gatherers.add(managedUnit);
                mineralGatherers.add(managedUnit);
                break;
            }
        }
    }

    // TODO: Assign closest geyser
    private void assignToGeyser(ManagedUnit managedUnit) {
        for (Unit geyser: gameState.getGeyserAssignments().keySet()) {
            HashSet<ManagedUnit> geyserUnits = gameState.getGeyserAssignments().get(geyser);
            if (geyserUnits.size() < 3) {
                managedUnit.setRole(UnitRole.GATHER);
                managedUnit.setGatherTarget(geyser);
                managedUnit.setHasNewGatherTarget(true);
                assignedManagedWorkers.add(managedUnit);
                gameState.setGeyserWorkers(gameState.getGeyserWorkers()+1);
                geyserUnits.add(managedUnit);
                gatherers.add(managedUnit);
                break;
            }
        }
    }

    private boolean assignMorphDrone(PlannedItem plannedItem) {
        for (ManagedUnit managedUnit : assignedManagedWorkers) {
            Unit unit = managedUnit.getUnit();
            if (unit.canBuild(plannedItem.getPlannedUnit()) && !gameState.getAssignedPlannedItems().containsKey(unit)) {
                clearAssignments(managedUnit);
                plannedItem.setState(PlanState.BUILDING);
                managedUnit.setRole(UnitRole.BUILD);
                managedUnit.setPlannedItem(plannedItem);
                gameState.getAssignedPlannedItems().put(unit, plannedItem);
                return true;
            }
        }

        return false;
    }

    private boolean assignMorphLarva(PlannedItem plannedItem) {
        for (ManagedUnit managedUnit : larva) {
            Unit unit = managedUnit.getUnit();
            // If larva and not assigned, assign
            if (!gameState.getAssignedPlannedItems().containsKey(unit)) {
                //clearAssignments(managedUnit);
                plannedItem.setState(PlanState.BUILDING);
                managedUnit.setRole(UnitRole.MORPH);
                managedUnit.setPlannedItem(plannedItem);
                gameState.getAssignedPlannedItems().put(unit, plannedItem);
                return true;
            }
        }

        return false;
    }

    // checksLarvaDeadlock determines if all larva are assigned to morph into non overlords and if we're supply blocked
    // if we meet both conditions, cancel these planned items and unassign the larva (there should be an overlord at top of queue)
    // third condition: scheduled queue is NOT empty and contains 0 overlords
    private void checksLarvaDeadlock() {
        Player self = game.self();
        int supplyTotal = self.supplyTotal();
        int supplyUsed = self.supplyUsed();
        if (supplyTotal - supplyUsed > 0) {
            return;
        }
        if (gameState.getPlansScheduled().size() == 0) {
            return;
        }

        for (ManagedUnit managedUnit : larva) {
            if (managedUnit.getRole() == UnitRole.LARVA) {
                return;
            }
            if (managedUnit.getRole() == UnitRole.MORPH) {
                PlannedItem plannedItem = managedUnit.getPlannedItem();
                if (plannedItem != null & plannedItem.getPlannedUnit() == UnitType.Zerg_Overlord) {
                    return;
                }
            }
        }

        for (ManagedUnit managedUnit: eggs) {
            if (managedUnit.getRole() == UnitRole.MORPH) {
                PlannedItem plannedItem = managedUnit.getPlannedItem();
                if (plannedItem != null & plannedItem.getPlannedUnit() == UnitType.Zerg_Overlord) {
                    return;
                }
            }
        }

        Boolean isOverlordAssignedOrMorphing = false;
        for (PlannedItem plannedItem: gameState.getAssignedPlannedItems().values()) {
            if (plannedItem.getType() != PlanType.UNIT) {
                continue;
            }

            if (plannedItem.getPlannedUnit() == UnitType.Zerg_Overlord) {
                isOverlordAssignedOrMorphing = true;
                break;
            }
        }

        // Larva Deadlock Criteria:
        // - ALL larva assigned
        // - NO overlord building
        // - NO overlord scheduled

        // Overlord in production
        for (PlannedItem plannedItem: gameState.getPlansMorphing()) {
            if (plannedItem.getType() != PlanType.UNIT) {
                continue;
            }

            if (plannedItem.getPlannedUnit() == UnitType.Zerg_Overlord) {
                isOverlordAssignedOrMorphing = true;
                break;
            }
        }

        if (isOverlordAssignedOrMorphing) {
            return;
        }

        gameState.setLarvaDeadlocked(true);
    }

    private void handleLarvaDeadlock() {
        if (!gameState.isLarvaDeadlocked()) {
            return;
        }

        for (ManagedUnit managedUnit : larva) {
            Unit unit = managedUnit.getUnit();
            clearAssignments(managedUnit);
            managedUnit.setRole(UnitRole.LARVA);

            PlannedItem plannedItem = managedUnit.getPlannedItem();
            // TODO: Handle cancelled items. Are they requeued?
            if (plannedItem != null) {
                plannedItem.setState(PlanState.CANCELLED);
                managedUnit.setPlannedItem(null);
            }

            gameState.getAssignedPlannedItems().remove(unit);
        }

        gameState.setLarvaDeadlocked(false);
    }


    // TODO: display this as a counter on the existing base
    // TODO: There's a bug where initial workers aren't assigned
    private void debugMineralPatches() {
        for (Unit mineral: gameState.getMineralAssignments().keySet()) {
            TilePosition tilePosition = mineral.getTilePosition();
            game.drawTextMap(tilePosition.getX() * 32, tilePosition.getY() * 32, String.valueOf(gameState.getMineralAssignments().get(mineral).size()), Text.Yellow);
        }
    }
}
