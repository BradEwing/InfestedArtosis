package strategy.openers;

import bwapi.UnitType;
import planner.Plan;

import java.util.ArrayList;
import java.util.List;

public class OverPool implements Opener {

    public OpenerName getName() { return OpenerName.OVER_POOL; }

    public List<Plan> getBuildOrder() {

        List<Plan> list = new ArrayList<>();
        list.add(new Plan(UnitType.Zerg_Drone, 0, false, true));
        list.add(new Plan(UnitType.Zerg_Drone, 0, false, true));
        list.add(new Plan(UnitType.Zerg_Drone, 0, false, true));
        list.add(new Plan(UnitType.Zerg_Drone, 0, false, true));
        list.add(new Plan(UnitType.Zerg_Drone, 0, false, true));
        list.add(new Plan(UnitType.Zerg_Overlord, 1, false, true));
        list.add(new Plan(UnitType.Zerg_Spawning_Pool, 2, true, true));
        list.add(new Plan(UnitType.Zerg_Drone, 3, false, false));
        list.add(new Plan(UnitType.Zerg_Drone, 3, false, false));
        list.add(new Plan(UnitType.Zerg_Drone, 3, false, false));
        list.add(new Plan(UnitType.Zerg_Zergling, 4, false, false));
        list.add(new Plan(UnitType.Zerg_Zergling, 4, false, false));
        list.add(new Plan(UnitType.Zerg_Zergling, 4, false, false));

        return list;
    }

    public boolean isAllIn() { return false; }
}
