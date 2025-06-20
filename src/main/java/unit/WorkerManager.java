package unit;

import bwapi.Game;
import bwapi.Player;
import bwapi.Unit;
import bwapi.UnitType;
import bwem.Base;
import info.GameState;
import info.ResourceCount;
import macro.plan.Plan;
import macro.plan.PlanState;
import macro.plan.PlanType;
import unit.managed.ManagedUnit;
import unit.managed.ManagedUnitToPositionComparator;
import unit.managed.UnitRole;
import util.BaseUnitDistanceComparator;
import util.UnitDistanceComparator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WorkerManager {
    private Game game;

    private GameState gameState;

    private HashSet<ManagedUnit> assignedManagedWorkers;
    private HashSet<ManagedUnit> gatherers;
    private HashSet<ManagedUnit> mineralGatherers;
    private HashSet<ManagedUnit> gasGatherers;
    private HashSet<ManagedUnit> larva;
    private HashSet<ManagedUnit> eggs = new HashSet<>();

    // Overlord takes 16 frames to hatch from egg
    // Buffered w/ 10 additional frames
    final int OVERLORD_HATCH_ANIMATION_FRAMES = 26;

    public WorkerManager(Game game, GameState gameState) {
        this.game = game;
        this.gameState = gameState;
        this.gatherers = gameState.getGatherers();
        this.larva = gameState.getLarva();
        this.mineralGatherers = gameState.getMineralGatherers();
        this.gasGatherers = gameState.getGasGatherers();
        this.assignedManagedWorkers = gameState.getAssignedManagedWorkers();
    }

    public void onFrame() {
        checksLarvaDeadlock();
        handleLarvaDeadlock();

        rebalanceCheck();
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
        }
    }

    public void addManagedWorker(ManagedUnit managedUnit) {
        assignWorker(managedUnit);
    }

    // onUnitDestroy OR worker is being reassigned to non-worker role
    public void removeManagedWorker(ManagedUnit managedUnit) {
        gameState.clearAssignments(managedUnit);
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
            gameState.clearAssignments(managedUnit);
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
            gameState.clearAssignments(managedUnit);
            assignWorker(managedUnit);
        }
    }

    // onExtractorComplete is called when an extractor is complete, to immediately pull 3 mineral gathering drones
    // onto the extractor
    public void onExtractorComplete(Unit unit) {
        final List<ManagedUnit> newGeyserWorkers = new ArrayList<>();

        // If less than 4 mineral workers, there are probably other problems
        if (mineralGatherers.size() < 4) {
            return;
        }

        List<ManagedUnit> sortedGatherers = mineralGatherers.stream()
                .sorted(new ManagedUnitToPositionComparator(unit.getPosition()))
                .collect(Collectors.toList());

        for (ManagedUnit managedUnit: sortedGatherers) {
            if (newGeyserWorkers.size() >= 3) {
                break;
            }
            newGeyserWorkers.add(managedUnit);
        }

        for (ManagedUnit managedUnit: newGeyserWorkers) {
            gameState.clearAssignments(managedUnit);
            assignToGeyser(managedUnit);
        }
    }

    // Initial assignment onUnitComplete
    // Default to mineral
    private void assignWorker(ManagedUnit managedUnit) {
        // Assign 3 per geyser
        if (gameState.needGeyserWorkers() && gameState.needGeyserWorkersAmount() > 0) {
            assignToGeyser(managedUnit);
            return;
        }

        assignToMineral(managedUnit);
    }

    private void assignToClosestBase(Unit gatherTarget, ManagedUnit gatherer) {
        HashMap<Base, HashSet<ManagedUnit>> gatherersAssignedToBase = gameState.getGatherersAssignedToBase();
        List<Base> bases = new ArrayList<>(gatherersAssignedToBase.keySet());
        if (bases.isEmpty()) {
             return;
        }

        bases.sort(new BaseUnitDistanceComparator(gatherTarget));
        HashSet<ManagedUnit> assignedManagedUnits = gatherersAssignedToBase.get(bases.get(0));
        assignedManagedUnits.add(gatherer);
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
            // NOTE: Assign 1 per patch but buffer with 5 extra
            fewestMineralAssignments = gameState.getMineralAssignments().size() / gameState.getMineralWorkers() <= 1 ? 1 : 0;
        }
        List<Unit> claimedMinerals = gameState.getMineralAssignments().keySet().stream().collect(Collectors.toList());
        claimedMinerals.sort(new UnitDistanceComparator(unit));

        for (Unit mineral: claimedMinerals) {
            HashSet<ManagedUnit> mineralUnits = gameState.getMineralAssignments().get(mineral);
            if (mineralUnits.size() <= fewestMineralAssignments) {
                managedUnit.setRole(UnitRole.GATHER);
                managedUnit.setGatherTarget(mineral);
                managedUnit.setNewGatherTarget(true);
                assignedManagedWorkers.add(managedUnit);
                gameState.setMineralWorkers(gameState.getMineralWorkers()+1);
                mineralUnits.add(managedUnit);
                gatherers.add(managedUnit);
                mineralGatherers.add(managedUnit);
                assignToClosestBase(mineral, managedUnit);
                return;
            }
        }

        return;
    }

    // TODO: Assign closest geyser
    private void assignToGeyser(ManagedUnit managedUnit) {
        for (Unit geyser: gameState.getGeyserAssignments().keySet()) {
            HashSet<ManagedUnit> geyserUnits = gameState.getGeyserAssignments().get(geyser);
            if (geyserUnits.size() < 3) {
                managedUnit.setRole(UnitRole.GATHER);
                managedUnit.setGatherTarget(geyser);
                managedUnit.setNewGatherTarget(true);
                assignedManagedWorkers.add(managedUnit);
                gameState.setGeyserWorkers(gameState.getGeyserWorkers()+1);
                geyserUnits.add(managedUnit);
                gatherers.add(managedUnit);
                gasGatherers.add(managedUnit);
                assignToClosestBase(geyser, managedUnit);
                break;
            }
        }
    }

    // checksLarvaDeadlock determines if all larva are assigned to morph into non overlords and if we're supply blocked
    // if we meet both conditions, cancel these planned items and unassign the larva (there should be an overlord at top of queue)
    // third condition: scheduled queue is NOT empty and contains 0 overlords
    private void checksLarvaDeadlock() {
        Player self = game.self();
        int supplyTotal = self.supplyTotal();
        int supplyUsed = self.supplyUsed();

        int curFrame = game.getFrameCount();

        if (supplyTotal - supplyUsed + gameState.getResourceCount().getPlannedSupply() > 0) {
            gameState.setLarvaDeadlocked(false);
            return;
        }

        if (gameState.isLarvaDeadlocked() && curFrame < gameState.getLarvaDeadlockDetectedFrame() + OVERLORD_HATCH_ANIMATION_FRAMES) {
            return;
        }

        if (gameState.getPlansScheduled().isEmpty()) {
            return;
        }

        for (ManagedUnit managedUnit : larva) {
            if (managedUnit.getRole() == UnitRole.LARVA) {
                return;
            }
            if (managedUnit.getRole() == UnitRole.MORPH) {
                Plan plan = managedUnit.getPlan();
                if (plan != null & plan.getPlannedUnit() == UnitType.Zerg_Overlord) {
                    return;
                }
            }
        }

        for (ManagedUnit managedUnit: eggs) {
            if (managedUnit.getRole() == UnitRole.MORPH) {
                Plan plan = managedUnit.getPlan();
                if (plan != null & plan.getPlannedUnit() == UnitType.Zerg_Overlord) {
                    return;
                }
            }
        }

        boolean isOverlordAssignedOrMorphing = false;
        for (Plan plan : gameState.getAssignedPlannedItems().values()) {
            if (plan.getType() != PlanType.UNIT) {
                continue;
            }

            if (plan.getPlannedUnit() == UnitType.Zerg_Overlord) {
                isOverlordAssignedOrMorphing = true;
                break;
            }
        }

        // Larva Deadlock Criteria:
        // - ALL larva assigned
        // - NO overlord building
        // - NO overlord scheduled

        // Overlord in production
        for (Plan plan : gameState.getPlansMorphing()) {
            if (plan.getType() != PlanType.UNIT) {
                continue;
            }

            if (plan.getPlannedUnit() == UnitType.Zerg_Overlord) {
                isOverlordAssignedOrMorphing = true;
                break;
            }
        }

        if (isOverlordAssignedOrMorphing) {
            return;
        }

        gameState.setLarvaDeadlocked(true);
        gameState.setLarvaDeadlockDetectedFrame(curFrame);
    }

    private void handleLarvaDeadlock() {
        if (!gameState.isLarvaDeadlocked()) {
            return;
        }

        int frameCount = game.getFrameCount();
        int frameDeadlockDetected = gameState.getLarvaDeadlockDetectedFrame();
        if (frameCount < frameDeadlockDetected + OVERLORD_HATCH_ANIMATION_FRAMES) {
            return;
        }

        List<ManagedUnit> larvaCopy = larva.stream().collect(Collectors.toList());
        for (ManagedUnit managedUnit : larvaCopy) {
            Unit unit = managedUnit.getUnit();
            gameState.clearAssignments(managedUnit);
            managedUnit.setRole(UnitRole.LARVA);
            larva.add(managedUnit);

            Plan plan = managedUnit.getPlan();
            // TODO: Handle cancelled items. Are they requeued?
            if (plan != null) {
                plan.setState(PlanState.CANCELLED);
                managedUnit.setPlan(null);
            }

            gameState.getAssignedPlannedItems().remove(unit);
        }

        gameState.setLarvaDeadlocked(false);
    }

    private void rebalanceCheck() {
        ResourceCount resourceCount = gameState.getResourceCount();

        if (resourceCount.isFloatingGas()) {
            cutGasHarvesting();
        } else if (resourceCount.isFloatingMinerals()) {
            saturateGeysers();
        }
    }

    /**
     * Saturate geysers from gatherers
     *
     * TODO: Find closest drones
     */
    private void saturateGeysers() {
        if (!gameState.needGeyserWorkers()) {
            return;
        }

        List<ManagedUnit> reassignedWorkers = new ArrayList<>();
        for (ManagedUnit managedUnit: mineralGatherers) {
            if (reassignedWorkers.size() >= gameState.needGeyserWorkersAmount()) {
                break;
            }

            reassignedWorkers.add(managedUnit);
        }

        for (ManagedUnit worker: reassignedWorkers) {
            gameState.clearAssignments(worker);
            assignToGeyser(worker);
        }
    }

    /**
     * Cuts gas harvesting.
     *
     * TODO: Partially cut, set reactions of when to cut.
     */
    private void cutGasHarvesting() {
        List<ManagedUnit> geyserWorkers = new ArrayList<>(gasGatherers);
        for (ManagedUnit worker: geyserWorkers) {
            gameState.clearAssignments(worker);
            assignToMineral(worker);
        }
    }
}
