package unit;

import bwapi.Game;
import bwapi.Unit;
import bwapi.UnitType;
import info.GameState;
import plan.Plan;
import plan.PlanState;
import plan.PlanType;
import plan.PlanComparator;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages buildings for morphs
 *
 *  Hatch -> Lair -> Hive
 *  Creep_Colony -> Sunken | Spore
 *  Spire -> Greater_Spire
 *
 */
public class BuildingManager {
    private GameState gameState;

    private ManagedUnit lair;
    private HashSet<ManagedUnit> hatcheries = new HashSet();
    private HashSet<ManagedUnit> colonies = new HashSet<>();
    private HashSet<ManagedUnit> morphingManagedUnits = new HashSet<>();

    public BuildingManager(Game game, GameState gameState) {
        this.gameState = gameState;
    }

    public void onFrame() {
        assignScheduledPlannedItems();
    }

    public void add(ManagedUnit managedUnit) {
        UnitType unitType = managedUnit.getUnitType();
        switch(unitType) {
            case Zerg_Hatchery:
            case Zerg_Lair:
            case Zerg_Hive:
                this.hatcheries.add(managedUnit);
                break;
            case Zerg_Creep_Colony:
                this.colonies.add(managedUnit);
                break;
        }
    }

    public void remove(ManagedUnit managedUnit) {
        UnitType unitType = managedUnit.getUnitType();
        switch(unitType) {
            case Zerg_Hatchery:
            case Zerg_Lair:
            case Zerg_Hive:
                this.hatcheries.remove(managedUnit);
                break;
            case Zerg_Creep_Colony:
                this.colonies.remove(managedUnit);
                break;
        }
    }

    private void assignScheduledPlannedItems() {
        List<Plan> scheduledPlans = gameState.getPlansScheduled().stream().collect(Collectors.toList());
        if (scheduledPlans.size() < 1) {
            return;
        }

        Collections.sort(scheduledPlans, new PlanComparator());
        List<Plan> assignedPlans = new ArrayList<>();
        // TODO: Fix bug that keeps complete plan in scheduled
        List<Plan> completePlans = new ArrayList<>();

        for (Plan plan : scheduledPlans) {
            if (plan.getType() != PlanType.BUILDING) {
                return;
            }

            if (plan.getState() == PlanState.COMPLETE) {
                completePlans.add(plan);
                continue;
            }

            UnitType unitType = plan.getPlannedUnit();
            boolean didAssign = false;
            switch (unitType) {
                case Zerg_Lair:
                    didAssign = this.assignMorphLair(plan);
                    break;
                case Zerg_Sunken_Colony:
                    didAssign = this.assignMorphSunkenColony(plan);
                    break;
                case Zerg_Hive:
                    didAssign = this.assignMorphHive(plan);
                    break;
            }

            if (didAssign) {
                assignedPlans.add(plan);
                break;
            }
        }

        HashSet<Plan> morphingPlans = gameState.getPlansMorphing();
        for (Plan plan : assignedPlans) {
            scheduledPlans.remove(plan);
            morphingPlans.add(plan);
        }

        for (Plan plan: completePlans) {
            scheduledPlans.remove(plan);
        }

        gameState.setPlansScheduled(scheduledPlans.stream().collect(Collectors.toCollection(HashSet::new)));
    }

    private boolean assignMorphLair(Plan plan) {
        for (ManagedUnit managedHatchery : hatcheries) {
            Unit hatchery = managedHatchery.getUnit();
            if (hatchery.canBuild(plan.getPlannedUnit()) && !gameState.getAssignedPlannedItems().containsKey(hatchery)) {
                managedHatchery.setRole(UnitRole.MORPH);
                gameState.getAssignedPlannedItems().put(hatchery, plan);
                managedHatchery.setPlan(plan);
                lair = managedHatchery;
                return true;
            }
        }

        return false;
    }

    private boolean assignMorphHive(Plan plan) {
        Unit lairUnit = lair.getUnit();
        if (lairUnit.canBuild(plan.getPlannedUnit()) && !gameState.getAssignedPlannedItems().containsKey(lairUnit)) {
            lair.setRole(UnitRole.MORPH);
            gameState.getAssignedPlannedItems().put(lairUnit, plan);
            lair.setPlan(plan);
            return true;
        }

        return false;
    }

    private boolean assignMorphSunkenColony(Plan plan) {
        for (ManagedUnit managedColony : colonies) {
            Unit colony = managedColony.getUnit();
            if (colony.canBuild(plan.getPlannedUnit()) &&
                    !gameState.getAssignedPlannedItems().containsKey(colony)) {
                managedColony.setRole(UnitRole.MORPH);
                gameState.getAssignedPlannedItems().put(colony, plan);
                managedColony.setPlan(plan);
                return true;
            }
        }

        return false;
    }
}
