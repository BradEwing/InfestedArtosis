package unit;

import bwapi.Game;
import bwapi.Unit;
import bwapi.UnitType;
import info.GameState;
import planner.PlanState;
import planner.PlanType;
import planner.PlannedItem;
import planner.PlannedItemComparator;
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
    private Game game;
    private GameState gameState;

    private HashSet<Unit> hatcheries = new HashSet();

    public BuildingManager(Game game, GameState gameState) {
        this.game = game;
        this.gameState = gameState;
    }

    public void onFrame() {
        assignScheduledPlannedItems();
    }

    public void add(Unit unit) {
        UnitType unitType = unit.getType();
        switch(unitType) {
            case Zerg_Hatchery:
                this.hatcheries.add(unit);
                break;
        }
    }

    public void remove(Unit unit) {
        UnitType unitType = unit.getType();
        switch(unitType) {
            case Zerg_Hatchery:
                this.hatcheries.remove(unit);
                break;
        }
    }

    private void assignScheduledPlannedItems() {
        List<PlannedItem> scheduledPlans = gameState.getPlansScheduled().stream().collect(Collectors.toList());
        if (scheduledPlans.size() < 1) {
            return;
        }

        Collections.sort(scheduledPlans, new PlannedItemComparator());
        List<PlannedItem> assignedPlans = new ArrayList<>();

        for (PlannedItem plannedItem: scheduledPlans) {
            if (plannedItem.getType() != PlanType.BUILDING) {
                return;
            }

            UnitType unitType = plannedItem.getPlannedUnit();
            boolean didAssign = false;
            switch (unitType) {
                case Zerg_Lair:
                    didAssign = this.assignMorphLair(plannedItem);
                    break;
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

    private boolean assignMorphLair(PlannedItem plannedItem) {
        for (Unit hatchery : hatcheries) {
            if (hatchery.canBuild(plannedItem.getPlannedUnit()) && !gameState.getAssignedPlannedItems().containsKey(hatchery)) {
                plannedItem.setState(PlanState.BUILDING);
                gameState.getAssignedPlannedItems().put(hatchery, plannedItem);
                return true;
            }
        }

        return false;
    }
}
