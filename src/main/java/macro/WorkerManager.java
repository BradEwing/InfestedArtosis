package macro;

import bwapi.Game;
import bwapi.Player;
import bwapi.Position;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import planner.PlannedItem;
import unit.ManagedUnit;
import util.UnitDistanceComparator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class WorkerManager {
    private Game game;

    private int mineralWorkers = 0;
    private int gasWorkers = 0;

    private HashMap<Unit, HashSet<Unit>> geyserAssignments = new HashMap<>();
    private HashMap<Unit, HashSet<Unit>> mineralAssignments = new HashMap<>();
    private HashMap<Unit, UnitType> builderAssignments = new HashMap<>();

    private HashMap<Unit, PlannedItem> assignedPlannedItems = new HashMap<>();


    private HashSet<Unit> assignedWorkers = new HashSet<>();

    private HashSet<ManagedUnit> gatherers = new HashSet<>();
    private HashSet<ManagedUnit> larva = new HashSet<>();

    public WorkerManager() {

    }

    public void onUnitComplete(Unit unit) {
        // Assign larva, drones
    }
    // TODO: Refactor
    // The unit.gather needs to be in the unit manager
    // That requires knowledge about available mineral patches and their assignments
    // Maybe WorkerManager?
    // Unit Manager can exchange units with Worker Manager
    // Need to be able to pass build positions

    private void assignWorker(ManagedUnit managedUnit) {
        Player self = game.self();
        Unit unit = managedUnit.getUnit();
        if (unit.getPlayer() != self) {
            return;
        }

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
                    assignMineral(managedUnit);
                }
            }
        }

        // TODO: onRenegade
        if (unit.getType() == UnitType.Zerg_Extractor) {
            geyserAssignments.put(unit, new HashSet<>());
        }
    }

    private boolean morphUnit(ManagedUnit unit, PlannedItem plannedItem) {
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
                unit.gather(mineral);
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
                unit.gather(mineral);
                mineralWorkers += 1;
                assignedWorkers.add(unit);
                mineralUnits.add(unit);
                break;
            }
        }
    }

    private void
}
