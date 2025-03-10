package strategy.openers;

import bwapi.UnitType;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.UnitPlan;

import java.util.ArrayList;
import java.util.List;

public class TwelveHatch implements Opener {

    public OpenerName getName() { return OpenerName.TWELVE_HATCH; }

    public List<Plan> getBuildOrder() {

        List<Plan> list = new ArrayList<>();
        list.add(new UnitPlan(UnitType.Zerg_Drone, 1, false));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 1, false));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 1, false));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 1, false));
        list.add(new UnitPlan(UnitType.Zerg_Overlord, 2, true));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 3, false));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 3, true));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 3, true));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 3, true));
        list.add(new BuildingPlan(UnitType.Zerg_Hatchery, 4, true));

        return list;
    }

    public boolean isAllIn() { return false; }
}
