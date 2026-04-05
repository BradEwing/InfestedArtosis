package macro.plan;

import bwapi.UpgradeType;
import lombok.Getter;
import lombok.Setter;

public class UpgradePlan extends Plan {
    @Getter @Setter
    private UpgradeType plannedUpgrade;

    public UpgradePlan(UpgradeType upgrade, int priority) {
        super(priority);
        this.plannedUpgrade = upgrade;
    }

    public UpgradePlan(UpgradeType upgrade, int priority, int currentLevel) {
        super(priority);
        this.plannedUpgrade = upgrade;
        this.setPlannedUpgradeLevel(currentLevel);
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
        return plannedUpgrade.mineralPrice(getPlannedUpgradeLevel() + 1);
    }

    @Override
    public int gasPrice() {
        return plannedUpgrade.gasPrice(getPlannedUpgradeLevel() + 1);
    }
}
