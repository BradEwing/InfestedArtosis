package macro.plan;

import bwapi.Game;
import bwapi.Position;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwem.Base;
import bwem.Mineral;
import info.BaseData;
import info.GameState;
import unit.managed.ManagedUnit;
import unit.managed.ManagedUnitToPositionComparator;
import unit.managed.UnitRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class PlanManager {

    private Game game;
    private GameState gameState;

    private HashSet<ManagedUnit> assignedManagedWorkers;
    private HashSet<ManagedUnit> gasGatherers;
    private HashSet<ManagedUnit> larva;
    private HashSet<ManagedUnit> scheduledDrones = new HashSet<>();


    public PlanManager(Game game, GameState gameState) {
        this.game = game;
        this.gameState = gameState;
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
     * Assign a drone to the building plan if it's not carrying resources, not mining gas and not already assigned to a plan.
     * The unit will store a scheduled plan until it's time to execute.
     * If the plan has an assigned building location, find the drone closest to the location.
     * @param plan plan to build
     * @return true if plan assigned, false otherwise
     */
    private boolean assignMorphDrone(Plan plan) {
        List<ManagedUnit> eligibleDrones = assignedManagedWorkers
                .stream()
                .filter(d -> {
                    Unit unit = d.getUnit();
                    return !unit.isCarrying() && !gasGatherers.contains(d) && !gameState.getAssignedPlannedItems().containsKey(unit);
                })
                .collect(Collectors.toList());

        TilePosition buildPosition = plan.getBuildPosition();
        if (buildPosition != null) {
            eligibleDrones.sort(new ManagedUnitToPositionComparator(buildPosition.toPosition()));
        }

        if (eligibleDrones.isEmpty()) {
            return false;
        }

        ManagedUnit managedUnit = eligibleDrones.get(0);
        Unit unit = managedUnit.getUnit();
        gameState.clearAssignments(managedUnit);
        scheduledDrones.add(managedUnit);
        managedUnit.setPlan(plan);
        gameState.getAssignedPlannedItems().put(unit, plan);
        return true;
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
        List<ManagedUnit> availableLarva = new ArrayList<>();
        for (ManagedUnit managedUnit : larva) {
            Unit unit = managedUnit.getUnit();
            if (!gameState.getAssignedPlannedItems().containsKey(unit)) {
                availableLarva.add(managedUnit);
            }
        }

        if (availableLarva.isEmpty()) {
            return false;
        }

        UnitType plannedUnit = plan.getPlannedUnit();
        ManagedUnit selectedLarva = null;

        if (availableLarva.size() > 1) {
            if (plannedUnit == UnitType.Zerg_Drone) {
                List<ManagedUnit> prioritizedLarva = availableLarva.stream()
                        .filter(l -> {
                            Base base = getBaseForLarva(l);
                            return base != null && hasUnderSaturatedResources(base);
                        })
                        .collect(Collectors.toList());

                if (!prioritizedLarva.isEmpty()) {
                    selectedLarva = prioritizedLarva.get(0);
                }
            } else if (plannedUnit == UnitType.Zerg_Overlord) {
                Base naturalBase = getNaturalExpansionBase();
                if (naturalBase != null) {
                    List<ManagedUnit> naturalLarva = availableLarva.stream()
                            .filter(l -> {
                                Base base = getBaseForLarva(l);
                                return base != null && base.equals(naturalBase);
                            })
                            .collect(Collectors.toList());

                    if (!naturalLarva.isEmpty()) {
                        selectedLarva = naturalLarva.get(0);
                    }
                }
            }
        }

        if (selectedLarva == null) {
            selectedLarva = availableLarva.get(0);
        }

        Unit unit = selectedLarva.getUnit();
        gameState.clearAssignments(selectedLarva);
        plan.setState(PlanState.BUILDING);
        selectedLarva.setRole(UnitRole.MORPH);
        selectedLarva.setPlan(plan);
        gameState.getAssignedPlannedItems().put(unit, plan);
        return true;
    }

    private Base getBaseForLarva(ManagedUnit larva) {
        Unit larvaUnit = larva.getUnit();
        Unit hatchery = larvaUnit.getHatchery();

        if (hatchery != null) {
            return gameState.getBaseData().get(hatchery);
        }

        HashSet<Unit> baseHatcheries = gameState.getBaseData().baseHatcheries();
        if (baseHatcheries.isEmpty()) {
            return null;
        }

        Unit closestHatchery = null;
        double closestDistance = Double.MAX_VALUE;
        for (Unit h : baseHatcheries) {
            double distance = larvaUnit.getDistance(h);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestHatchery = h;
            }
        }

        if (closestHatchery != null) {
            return gameState.getBaseData().get(closestHatchery);
        }

        return null;
    }

    private boolean hasUnderSaturatedResources(Base base) {
        HashMap<Unit, HashSet<ManagedUnit>> mineralAssignments = gameState.getMineralAssignments();
        for (Mineral mineral : base.getMinerals()) {
            Unit mineralUnit = mineral.getUnit();
            if (mineralAssignments.containsKey(mineralUnit)) {
                HashSet<ManagedUnit> mineralUnits = mineralAssignments.get(mineralUnit);
                if (mineralUnits.size() < 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private Base getNaturalExpansionBase() {
        BaseData baseData = gameState.getBaseData();
        if (!baseData.hasNaturalExpansion()) {
            return null;
        }
        return baseData.baseAtTilePosition(baseData.naturalExpansionPosition());
    }
}
