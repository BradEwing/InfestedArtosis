package macro;

import bwapi.Game;
import bwapi.Text;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import planner.PlanState;
import planner.PlanType;
import state.GameState;
import planner.PlannedItem;
import unit.ManagedUnit;
import unit.UnitRole;
import util.UnitDistanceComparator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class WorkerManager {
    private Game game;

    private GameState gameState;

    private HashSet<ManagedUnit> assignedManagedWorkers = new HashSet<>();
    private HashSet<ManagedUnit> gatherers = new HashSet<>();
    private HashSet<ManagedUnit> larva = new HashSet<>();
    private HashSet<ManagedUnit> morphingUnit = new HashSet<>();

    public WorkerManager(Game game, GameState gameState) {
        this.game = game;
        this.gameState = gameState;
    }

    public void onFrame() {
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
        morphingUnit.add(managedUnit);
    }


    public void addManagedWorker(ManagedUnit managedUnit) {
        assignWorker(managedUnit);
    }

    // onUnitDestroy OR worker is being reassigned to non-worker role
    public void removeManagedWorker(ManagedUnit managedUnit) {
        clearAssignments(managedUnit);
    }

    private void assignScheduledPlannedItems() {
        HashSet<PlannedItem> scheduledPlans = gameState.getPlansScheduled();
        List<PlannedItem> assignedPlans = new ArrayList<>();
        for (PlannedItem plannedItem: scheduledPlans) {
            boolean didAssign = false;
            if (plannedItem.getType() == PlanType.BUILDING) {
                didAssign = assignMorphDrone(plannedItem);
            } else if (plannedItem.getType() == PlanType.UNIT) {
                didAssign = assignMorphLarva(plannedItem);
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

        larva.remove(managedUnit);
        gatherers.remove(managedUnit);
        assignedManagedWorkers.remove(managedUnit);
        morphingUnit.remove(managedUnit);
    }

    // Initial assignment onUnitComplete
    //
    private void assignWorker(ManagedUnit managedUnit) {
        // Assign 3 per geyser
        if (gameState.getGeyserWorkers() < (3 * gameState.getGeyserAssignments().size())) {
            assignGeyser(managedUnit);
            return;
        }
        if (gameState.getMineralWorkers() < (2 * gameState.getMineralAssignments().size())) {
            assignMineral(managedUnit);
            return;
        }
    }

    private void assignMineral(ManagedUnit managedUnit) {
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
                assignedManagedWorkers.add(managedUnit);
                gameState.setMineralWorkers(gameState.getMineralWorkers()+1);
                mineralUnits.add(managedUnit);
                gatherers.add(managedUnit);
                break;
            }
        }
    }

    // TODO: Assign closest geyser
    private void assignGeyser(ManagedUnit managedUnit) {
        for (Unit geyser: gameState.getGeyserAssignments().keySet()) {
            HashSet<ManagedUnit> geyserUnits = gameState.getGeyserAssignments().get(geyser);
            if (geyserUnits.size() < 3) {
                managedUnit.setRole(UnitRole.GATHER);
                managedUnit.setGatherTarget(geyser);
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
            // If drone and not assigned, assign
            if (!gameState.getAssignedPlannedItems().containsKey(unit) && !morphingUnit.contains(managedUnit)) {
                clearAssignments(managedUnit);
                plannedItem.setState(PlanState.BUILDING);
                managedUnit.setRole(UnitRole.MORPH);
                managedUnit.setPlannedItem(plannedItem);
                gameState.getAssignedPlannedItems().put(unit, plannedItem);
                return true;
            }
        }

        return false;
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
