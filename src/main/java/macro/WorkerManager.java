package macro;

import bwapi.Game;
import bwapi.Player;
import bwapi.Position;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import info.GameState;
import planner.PlannedItem;
import unit.ManagedUnit;
import unit.UnitRole;
import util.UnitDistanceComparator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class WorkerManager {
    private Game game;

    private GameState gameState;

    private int mineralWorkers = 0;
    private int gasWorkers = 0;

    private HashMap<Unit, HashSet<Unit>> geyserAssignments = new HashMap<>();
    private HashMap<Unit, HashSet<Unit>> mineralAssignments = new HashMap<>();
    private HashMap<Unit, UnitType> builderAssignments = new HashMap<>();

    private HashMap<Unit, PlannedItem> assignedPlannedItems = new HashMap<>();


    private HashSet<Unit> assignedWorkers = new HashSet<>();

    private HashSet<ManagedUnit> assignedManagedWorkers = new HashSet<>();
    private HashSet<ManagedUnit> gatherers = new HashSet<>();
    private HashSet<ManagedUnit> larva = new HashSet<>();

    public WorkerManager(Game game, GameState gameState) {
        this.game = game;
        this.gameState = gameState;
    }

    public void onUnitComplete(ManagedUnit managedUnit) {
        // Assign larva, drones
        if (managedUnit.getRole() == UnitRole.MORPH) {
            assignWorker(managedUnit);
        }
    }

    public void onUnitMorph(ManagedUnit managedUnit) {

    }

    public void addMineral(Unit mineral) {
        mineralAssignments.put(mineral, new HashSet<>());
    }

    public void addGeyser(Unit geyser) {
        geyserAssignments.put(geyser, new HashSet<>());
    }

    public void removeMineral(Unit mineral) {

    }

    public void removeGeyser(Unit geyser) {

    }

    public void addManagedWorker(ManagedUnit managedUnit) {}

    public void removeManagedWorker(ManagedUnit managedUnit) {

    }

    private void clearAssignments() {}

    // Initial assignment onUnitComplete
    private void assignWorker(ManagedUnit managedUnit) {
        // Assign 3 per geyser
        if (gasWorkers < (3 * geyserAssignments.size())) {
            assignGeyser(managedUnit);
            return;
        }
        if (mineralWorkers < (2 * mineralAssignments.size())) {
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
        if (mineralWorkers == 0) {
            fewestMineralAssignments = 0;
        } else {
            fewestMineralAssignments = mineralAssignments.size() / mineralWorkers <= 0.5 ? 1 : 0;
        }
        List<Unit> claimedMinerals = mineralAssignments.keySet().stream().collect(Collectors.toList());
        claimedMinerals.sort(new UnitDistanceComparator(unit));

        for (Unit mineral: claimedMinerals) {
            HashSet<Unit> mineralUnits = mineralAssignments.get(mineral);
            if (mineralUnits.size() == fewestMineralAssignments) {
                managedUnit.setRole(UnitRole.GATHER);
                managedUnit.setGatherTarget(mineral);
                assignedManagedWorkers.add(managedUnit);
                mineralWorkers += 1;
                assignedWorkers.add(unit);
                mineralUnits.add(unit);
                break;
            }
        }
    }

    // TODO: pass ManagedUnit?
    private void assignGeyser(ManagedUnit managedUnit) {
        Unit unit = managedUnit.getUnit();
        for (Unit geyser: geyserAssignments.keySet()) {
            HashSet<Unit> geyserUnits = geyserAssignments.get(geyser);
            if (geyserUnits.size() < 3) {
                managedUnit.setRole(UnitRole.GATHER);
                managedUnit.setGatherTarget(geyser);
                assignedManagedWorkers.add(managedUnit);
                gasWorkers += 1;
                assignedWorkers.add(unit);
                geyserUnits.add(unit);
                break;
            }
        }
    }

    private boolean assignBuildingItem(PlannedItem plannedItem) {
        // TODO: Iterate through ManagedWorkers
        for (Unit unit : self.getUnits()) {
            if (unit.canBuild(plannedItem.getPlannedUnit()) && !gameState.getAssignedPlannedItems().containsKey(unit) && !builderAssignments.containsKey(unit)) {
                clearAssignments(unit, false);
                gameState.getAssignedPlannedItems().put(unit, plannedItem);
                builderAssignments.put(unit, plannedUnit);
                return true;
            }
        }

        return false;
    }

    // TODO: SCHEDULED -> BUILDING in WorkerManager
    private boolean assignUnitItem(Player self, PlannedItem plannedItem) {

        // Attempt to find a builder
        // TODO: Iterate through Larva
        for (Unit unit : self.getUnits()) {
            UnitType unitType = unit.getType();
            // If drone and not assigned, assign
            if (unitType == UnitType.Zerg_Larva && !gameState.getAssignedPlannedItems().containsKey(unit)) {
                gameState.getAssignedPlannedItems().put(unit, plannedItem);
                return true;
            }
        }

        return false;
    }

    private void assignUnit(Unit unit) {
        Player self = game.self();
        if (unit.getPlayer() != self) {
            return;
        }

        // TODO: Attempt to assign worker to closest hatch
        //
        // TODO: Sort geysers to unit, iterate until we find one available for assignment
        // TODO: Sort minerals to unit, iterate until we find one available for assignment
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
                    assignMineral(unit);
                }
            }
        }

        // TODO: onRenegade
        if (unit.getType() == UnitType.Zerg_Extractor) {
            geyserAssignments.put(unit, new HashSet<>());
        }
    }
}
