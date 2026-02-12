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

    public BuildingPlan(UnitType unitType, int priority) {
        super(priority);
        this.plannedUnit = unitType;
    }

    public BuildingPlan(UnitType unitType, int priority, TilePosition buildPosition) {
        super(priority);
        this.plannedUnit = unitType;
        this.buildPosition = buildPosition;
    }

    public BuildingPlan(UnitType unitType, Time time) {
        super(time.getFrames());
        this.plannedUnit = unitType;
    }

    public BuildingPlan(UnitType unitType, Time time, TilePosition buildPosition) {
        super(time.getFrames());
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
