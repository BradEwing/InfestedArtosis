package macro.plan;

import bwapi.TilePosition;
import bwapi.UnitType;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import util.Time;

public class BuildingPlan extends Plan {

    @Nullable
    @Getter @Setter
    private TilePosition buildPosition;
    @Getter @Setter
    private UnitType plannedUnit;

    public BuildingPlan(UnitType unitType, int priority, boolean isBlocking) {
        super(priority, isBlocking);
        this.plannedUnit = unitType;
        this.blockOtherPlans = isBlocking;
    }

    public BuildingPlan(UnitType unitType, int priority, boolean isBlocking, TilePosition buildPosition) {
        super(priority, isBlocking);
        this.plannedUnit = unitType;
        this.buildPosition = buildPosition;
    }

    public BuildingPlan(UnitType unitType, Time time, boolean isBlocking) {
        super(time.getFrames(), isBlocking);
        this.plannedUnit = unitType;
        this.blockOtherPlans = isBlocking;
    }

    public BuildingPlan(UnitType unitType, Time time, boolean isBlocking, TilePosition buildPosition) {
        super(time.getFrames(), isBlocking);
        this.plannedUnit = unitType;
        this.buildPosition = buildPosition;
    }

    @Override
    public PlanType getType() {
        return PlanType.BUILDING;
    }

    @Override
    public String getName() {
        return plannedUnit.toString();
    }

    @Override
    public int mineralPrice() {
        return plannedUnit.mineralPrice();
    }

    @Override
    public int gasPrice() {
        return plannedUnit.gasPrice();
    }

}
