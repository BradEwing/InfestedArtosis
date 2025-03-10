package macro.plan;

import bwapi.TechType;
import lombok.Getter;
import lombok.Setter;

public class TechPlan extends Plan {
    @Getter @Setter
    private TechType plannedTechType;

    public TechPlan(TechType techType, int priority, boolean isBlocking) {
        super(priority, isBlocking);
        this.plannedTechType = techType;
    }

    @Override
    public PlanType getType() {
        return PlanType.TECH;
    }

    @Override
    public String getName() {
        return plannedTechType.toString();
    }

    @Override
    public int mineralPrice() {
        return plannedTechType.mineralPrice();
    }
}
