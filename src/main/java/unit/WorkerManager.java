package unit;

import bwapi.Game;
import bwapi.Player;
import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import bwem.Base;
import info.GameState;
import info.ResourceCount;
import planner.Plan;
import planner.PlanComparator;
import planner.PlanState;
import planner.PlanType;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;
import util.BaseUnitDistanceComparator;
import util.UnitDistanceComparator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WorkerManager {
    private Game game;

    private GameState gameState;

    private HashSet<ManagedUnit> assignedManagedWorkers = new HashSet<>();
    private HashSet<ManagedUnit> gatherers;
    private HashSet<ManagedUnit> mineralGatherers;
    private HashSet<ManagedUnit> gasGatherers;
    private HashSet<ManagedUnit> larva;
    private HashSet<ManagedUnit> eggs = new HashSet<>();
    private HashSet<ManagedUnit> scheduledDrones = new HashSet<>();

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
    }

    public void onFrame() {
        checksLarvaDeadlock();
        handleLarvaDeadlock();

        assignScheduledPlannedItems();
        executeScheduledDrones();
        releaseImpossiblePlans();
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
            if (newGeyserWorkers.size() >= 3) {
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
        List<Plan> scheduledPlans = gameState.getPlansScheduled().stream().collect(Collectors.toList());
        if (scheduledPlans.size() < 1) {
            return;
        }

        Collections.sort(scheduledPlans, new PlanComparator());
        List<Plan> assignedPlans = new ArrayList<>();


        for (Plan plan : scheduledPlans) {
            UnitType planType = plan.getPlannedUnit();

            boolean didAssign = false;
            if (plan.getType() == PlanType.BUILDING) {
                if (isBuildingMorph(planType)) continue;
                didAssign = assignMorphDrone(plan);
            } else if (plan.getType() == PlanType.UNIT) {
                didAssign = assignMorphLarva(plan);
            }

            if (didAssign) {
                assignedPlans.add(plan);
            }
        }

        HashSet<Plan> buildingPlans = gameState.getPlansBuilding();
        for (Plan plan : assignedPlans) {
            scheduledPlans.remove(plan);
            buildingPlans.add(plan);
        }

        gameState.setPlansScheduled(scheduledPlans.stream().collect(Collectors.toCollection(HashSet::new)));
    }

    private void releaseImpossiblePlans() {
        HashSet<Plan> impossiblePlans = gameState.getPlansImpossible();
        List<Plan> cancelledPlans = new ArrayList<>();

        for (ManagedUnit larva: larva) {
            Plan currentPlan = larva.getPlan();
            if (currentPlan != null && impossiblePlans.contains(currentPlan)) {
                gameState.cancelPlan(larva.getUnit(), currentPlan);
            }
        }

        for (ManagedUnit drone: scheduledDrones) {
            Plan currentPlan = drone.getPlan();
            if (currentPlan != null && impossiblePlans.contains(currentPlan)) {
                gameState.cancelPlan(drone.getUnit(), currentPlan);
            }
        }
    }

    private boolean isBuildingMorph(UnitType unitType) {
        switch(unitType) {
            case Zerg_Lair:
            case Zerg_Sunken_Colony:
                return true;
            default:
                return false;
        }
    }

    // TODO: Consider acceleration, more complex paths
    private int getTravelFrames(Unit unit, Position buildingPosition) {
        Position unitPosition = unit.getPosition();
        double distance = buildingPosition.getDistance(unitPosition);
        double unitSpeed = unit.getType().topSpeed();

        return (int)( distance / unitSpeed );
    }

    private void executeScheduledDrones() {
        final int currentFrame = game.getFrameCount();
        List<ManagedUnit> executed = new ArrayList<>();
        for (ManagedUnit managedUnit: scheduledDrones) {
            Plan plan = managedUnit.getPlan();
            // TODO: Set build position for all scheduled build plans
            int travelFrames = this.getTravelFrames(managedUnit.getUnit(), plan.getBuildPosition().toPosition());
            if (currentFrame > plan.getPredictedReadyFrame() - travelFrames) {
                plan.setState(PlanState.BUILDING);
                managedUnit.setRole(UnitRole.BUILD);
                executed.add(managedUnit);
            }
        }

        for (ManagedUnit managedUnit: executed) {
            scheduledDrones.remove(managedUnit);
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

        eggs.remove(managedUnit);
        larva.remove(managedUnit);
        gatherers.remove(managedUnit);
        mineralGatherers.remove(managedUnit);
        gasGatherers.remove(managedUnit);
        assignedManagedWorkers.remove(managedUnit);

        for (HashSet<ManagedUnit> managedUnitAssignments: gameState.getGatherersAssignedToBase().values()) {
            if (managedUnitAssignments.contains(managedUnit)) {
                managedUnitAssignments.remove(managedUnit);
            }
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
        List<Base> bases = gatherersAssignedToBase.keySet().stream().collect(Collectors.toList());
        if (bases.size() == 0) {
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
                managedUnit.hasNewGatherTarget(true);
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
                managedUnit.hasNewGatherTarget(true);
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

    /**
     * Assign a drone to the building plan if it's not carrying resources, not mining gas and not already assigned to a plan
     * The unit will store a scheduled plan until it's time to execute
     * @param plan plan to build
     * @return
     */
    private boolean assignMorphDrone(Plan plan) {
        for (ManagedUnit managedUnit : assignedManagedWorkers) {
            Unit unit = managedUnit.getUnit();
            if (!unit.isCarrying() && !gasGatherers.contains(managedUnit) && !gameState.getAssignedPlannedItems().containsKey(unit)) {
                clearAssignments(managedUnit);
                // TODO: Plan and UnitRole have a state change IN agent inside of manager
                scheduledDrones.add(managedUnit);
                managedUnit.setPlan(plan);
                gameState.getAssignedPlannedItems().put(unit, plan);
                return true;
            }
        }

        return false;
    }

    private boolean assignMorphLarva(Plan plan) {
        for (ManagedUnit managedUnit : larva) {
            Unit unit = managedUnit.getUnit();
            // If larva and not assigned, assign
            if (!gameState.getAssignedPlannedItems().containsKey(unit)) {
                clearAssignments(managedUnit);
                plan.setState(PlanState.BUILDING);
                managedUnit.setRole(UnitRole.MORPH);
                managedUnit.setPlan(plan);
                gameState.getAssignedPlannedItems().put(unit, plan);
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

        int curFrame = game.getFrameCount();

        if (supplyTotal - supplyUsed + gameState.getResourceCount().getPlannedSupply() > 0) {
            gameState.setLarvaDeadlocked(false);
            return;
        }

        if (gameState.isLarvaDeadlocked() && curFrame < gameState.getLarvaDeadlockDetectedFrame() + OVERLORD_HATCH_ANIMATION_FRAMES) {
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

        Boolean isOverlordAssignedOrMorphing = false;
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
            clearAssignments(managedUnit);
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
            clearAssignments(worker);
            assignToGeyser(worker);
        }
    }

    /**
     * Cuts gas harvesting.
     *
     * TODO: Partially cut, set reactions of when to cut.
     */
    private void cutGasHarvesting() {
        List<ManagedUnit> geyserWorkers = gasGatherers.stream().collect(Collectors.toList());
        for (ManagedUnit worker: geyserWorkers) {
            clearAssignments(worker);
            assignToMineral(worker);
        }
    }
}
