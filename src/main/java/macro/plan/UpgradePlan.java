package macro.plan;

import bwapi.UpgradeType;
import lombok.Getter;
import lombok.Setter;

public class UpgradePlan extends Plan {
    @Getter @Setter
    private UpgradeType plannedUpgrade;

    public UpgradePlan(UpgradeType upgrade, int priority, boolean isBlocking) {
        super(priority);
        this.plannedUpgrade = upgrade;
    }

    @Override
    public PlanType getType() {
        return PlanType.UPGRADE;
    }

    @Override
    public String getName() {
        return plannedUpgrade.toString();
    }

    @Override
    public int mineralPrice() {
        return plannedUpgrade.mineralPrice();
    }

    @Override
    public int gasPrice() {
        return plannedUpgrade.gasPrice();
    }
}