package strategy.openers;

import bwapi.UnitType;
import plan.BuildingPlan;
import plan.Plan;
import plan.UnitPlan;

import java.util.ArrayList;
import java.util.List;

public class NinePool implements Opener {

    public OpenerName getName() { return OpenerName.NINE_POOL; }

    public List<Plan> getBuildOrder() {

        List<Plan> list = new ArrayList<>();
        list.add(new UnitPlan(UnitType.Zerg_Drone, 0, true));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 0, true));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 0, true));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 0, true));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 0, true));
        list.add(new BuildingPlan(UnitType.Zerg_Spawning_Pool, 1, true));
        list.add(new UnitPlan(UnitType.Zerg_Overlord, 2, false));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 3, false));
        list.add(new UnitPlan(UnitType.Zerg_Zergling, 4, false));
        list.add(new UnitPlan(UnitType.Zerg_Zergling, 4, false));
        list.add(new UnitPlan(UnitType.Zerg_Zergling, 4, false));

        return list;
    }

    public boolean isAllIn() { return false; }
}
