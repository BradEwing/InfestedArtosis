package macro.plan;

import bwapi.Game;
import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import info.GameState;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class PlanManager {

    private Game game;
    private GameState gameState;

    private HashSet<ManagedUnit> assignedManagedWorkers;
    private HashSet<ManagedUnit> gatherers;
    private HashSet<ManagedUnit> gasGatherers;
    private HashSet<ManagedUnit> larva;
    private HashSet<ManagedUnit> scheduledDrones = new HashSet<>();


    public PlanManager(Game game, GameState gameState) {
        this.game = game;
        this.gameState = gameState;
        this.gatherers = gameState.getGatherers();
        this.larva = gameState.getLarva();
        this.gasGatherers = gameState.getGasGatherers();
        this.assignedManagedWorkers = gameState.getAssignedManagedWorkers();
    }

    public void onFrame() {
        assignScheduledPlannedItems();
        executeScheduledDrones();
        releaseImpossiblePlans();
    }

    private void assignScheduledPlannedItems() {
        List<Plan> scheduledPlans = new ArrayList<>(gameState.getPlansScheduled());
        if (scheduledPlans.isEmpty()) {
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
                didAssign = assignMorphUnit(plan);
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

        gameState.setPlansScheduled(new HashSet<>(scheduledPlans));
    }

    private void releaseImpossiblePlans() {
        HashSet<Plan> impossiblePlans = gameState.getPlansImpossible();

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

    // TODO: Consider acceleration, more complex paths
    private int getTravelFrames(Unit unit, Position buildingPosition) {
        Position unitPosition = unit.getPosition();
        double distance = buildingPosition.getDistance(unitPosition);
        double unitSpeed = unit.getType().topSpeed();

        return (int)( distance / unitSpeed ) + 250;
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
                gameState.clearAssignments(managedUnit);
                // TODO: Plan and UnitRole have a state change IN agent inside of manager
                scheduledDrones.add(managedUnit);
                managedUnit.setPlan(plan);
                gameState.getAssignedPlannedItems().put(unit, plan);
                return true;
            }
        }

        return false;
    }

    private boolean assignMorphUnit(Plan plan) {
        switch(plan.getPlannedUnit()) {
            case Zerg_Lurker:
                return assignMorphHydralisk(plan);
            default:
                return assignMorphLarva(plan);
        }
    }

    private boolean assignMorphHydralisk(Plan plan) {
        List<ManagedUnit> hydralisks = gameState.getManagedUnitsByType(UnitType.Zerg_Hydralisk);
        for (ManagedUnit managedUnit: hydralisks) {
            Unit unit = managedUnit.getUnit();
            if (!gameState.getAssignedPlannedItems().containsKey(unit)) {
                gameState.clearAssignments(managedUnit);
                plan.setState(PlanState.MORPHING);
                managedUnit.setRole(UnitRole.MORPH);
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
                gameState.clearAssignments(managedUnit);
                plan.setState(PlanState.BUILDING);
                managedUnit.setRole(UnitRole.MORPH);
                managedUnit.setPlan(plan);
                gameState.getAssignedPlannedItems().put(unit, plan);
                return true;
            }
        }

        return false;
    }
}
