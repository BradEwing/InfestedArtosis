package strategy.openers;

import bwapi.UnitType;
import plan.BuildingPlan;
import plan.Plan;
import plan.UnitPlan;

import java.util.ArrayList;
import java.util.List;

public class NineHatch implements Opener {

    public OpenerName getName() { return OpenerName.NINE_HATCH; }

    public List<Plan> getBuildOrder() {

        List<Plan> list = new ArrayList<>();
        list.add(new UnitPlan(UnitType.Zerg_Drone, 0, true));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 0, true));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 0, true));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 0, true));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 0, true));
        list.add(new BuildingPlan(UnitType.Zerg_Hatchery, 1, true));
        list.add(new BuildingPlan(UnitType.Zerg_Spawning_Pool, 2, true));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 3, false));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 3, false));
        list.add(new UnitPlan(UnitType.Zerg_Overlord, 4, false));
        list.add(new UnitPlan(UnitType.Zerg_Zergling, 5, false));
        list.add(new UnitPlan(UnitType.Zerg_Zergling, 5, false));
        list.add(new UnitPlan(UnitType.Zerg_Zergling, 5,  false));

        return list;
    }

    public boolean isAllIn() { return false; }

}
