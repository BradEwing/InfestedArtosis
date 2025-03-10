package strategy.openers;

import bwapi.UnitType;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.UnitPlan;

import java.util.ArrayList;
import java.util.List;

public class OverPool implements Opener {

    public OpenerName getName() { return OpenerName.OVER_POOL; }

    public List<Plan> getBuildOrder() {

        List<Plan> list = new ArrayList<>();
        list.add(new UnitPlan(UnitType.Zerg_Drone, 0, true));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 0, true));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 0, true));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 0, true));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 0, true));
        list.add(new UnitPlan(UnitType.Zerg_Overlord, 1, true));
        list.add(new BuildingPlan(UnitType.Zerg_Spawning_Pool, 2, true));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 3, false));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 3, false));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 3, false));
        list.add(new UnitPlan(UnitType.Zerg_Zergling, 4, false));
        list.add(new UnitPlan(UnitType.Zerg_Zergling, 4, false));
        list.add(new UnitPlan(UnitType.Zerg_Zergling, 4, false));

        return list;
    }

    public boolean isAllIn() { return false; }
}
