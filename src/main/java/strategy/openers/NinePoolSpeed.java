package strategy.openers;

import bwapi.UnitType;
import bwapi.UpgradeType;
import plan.BuildingPlan;
import plan.Plan;
import plan.UpgradePlan;

import java.util.List;

public class NinePoolSpeed extends NinePool implements Opener {

    @Override
    public OpenerName getName() { return OpenerName.NINE_POOL_SPEED; }

    @Override
    public List<Plan> getBuildOrder() {

        List<Plan> list = super.getBuildOrder();
        list.add(new BuildingPlan(UnitType.Zerg_Extractor, 2, true));
        list.add(new UpgradePlan(UpgradeType.Metabolic_Boost, 5, true));
        return list;
    }
}
