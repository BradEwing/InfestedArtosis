package macro.plan;

import bwapi.UnitType;
import lombok.Getter;
import lombok.Setter;

public class UnitPlan extends Plan {
    @Getter @Setter
    private UnitType plannedUnit;

    public UnitPlan(UnitType unitType, int priority, boolean isBlocking) {
        super(priority, isBlocking);
        this.plannedUnit = unitType;
    }

    @Override
    public PlanType getType() {
        return PlanType.UNIT;
    }

    @Override
    public String getName() {
        return plannedUnit.toString();
    }

    @Override
    public int mineralPrice() {
        return plannedUnit.mineralPrice();
    }
}