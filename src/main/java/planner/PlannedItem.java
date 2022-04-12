package planner;

import bwapi.TilePosition;
import bwapi.UnitType;
import bwapi.UpgradeType;

import lombok.Data;
import org.jetbrains.annotations.Nullable;

// TODO: Refactor with type reflection?
@Data
public class PlannedItem {
    private PlanType type;
    private PlanState state;

    // Simple prioritization, will increment elsewhere
    private int priority;
    private int frameStart;
    private int retries = 0;

    @Nullable
    private TilePosition buildPosition;

    @Nullable
    private UnitType plannedUnit;

    @Nullable
    private UpgradeType plannedUpgrade;

    public PlannedItem(UnitType unitType, int priority, boolean isBuilding) {
        this.priority = priority;
        this.plannedUnit = unitType;
        this.type = isBuilding ? PlanType.BUILDING : PlanType.UNIT;
        this.state = PlanState.PLANNED;
    }

    public PlannedItem(UnitType unitType, int priority, boolean isBuilding, TilePosition buildPosition) {
        this.priority = priority;
        this.plannedUnit = unitType;
        this.type = isBuilding ? PlanType.BUILDING : PlanType.UNIT;
        this.buildPosition = buildPosition;
    }

    public PlannedItem(UpgradeType upgrade, int priority) {
        this.priority = priority;
        this.plannedUpgrade = upgrade;
        this.type = PlanType.UPGRADE;
    }
    

    public String getName() {
        if (plannedUpgrade != null) {
            return plannedUpgrade.toString();
        } else if (plannedUnit != null) {
            return plannedUnit.toString();
        } else {
            return "NULL";
        }
    }
}
