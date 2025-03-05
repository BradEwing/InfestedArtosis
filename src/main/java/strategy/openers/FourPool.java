package strategy.openers;

import bwapi.UnitType;
import plan.BuildingPlan;
import plan.Plan;
import plan.UnitPlan;

import java.util.ArrayList;
import java.util.List;

public class FourPool implements Opener {

    public OpenerName getName() { return OpenerName.FOUR_POOL; }

    public List<Plan> getBuildOrder() {

        List<Plan> list = new ArrayList<>();
        list.add(new BuildingPlan(UnitType.Zerg_Spawning_Pool, 0, true));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 1, true));
        list.add(new UnitPlan(UnitType.Zerg_Zergling, 2, false));
        list.add(new UnitPlan(UnitType.Zerg_Zergling, 2, false));
        list.add(new UnitPlan(UnitType.Zerg_Zergling, 2, false));
        list.add(new UnitPlan(UnitType.Zerg_Zergling, 2, false));
        list.add(new UnitPlan(UnitType.Zerg_Zergling, 2, false));

        return list;
    }

    public boolean isAllIn() { return true; }
}
