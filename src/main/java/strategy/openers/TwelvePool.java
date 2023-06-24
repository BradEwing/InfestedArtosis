package strategy.openers;

import bwapi.UnitType;
import planner.Plan;

import java.util.ArrayList;
import java.util.List;

public class TwelvePool implements Opener {

    public OpenerName getName() { return OpenerName.TWELVE_POOL; }

    public List<Plan> getBuildOrder() {

        List<Plan> list = new ArrayList<>();
        list.add(new Plan(UnitType.Zerg_Drone, 1, false, false));
        list.add(new Plan(UnitType.Zerg_Drone, 1, false, false));
        list.add(new Plan(UnitType.Zerg_Drone, 1, false, false));
        list.add(new Plan(UnitType.Zerg_Drone, 1, false, false));
        list.add(new Plan(UnitType.Zerg_Overlord, 2, false, true));
        list.add(new Plan(UnitType.Zerg_Drone, 3, false, false));
        list.add(new Plan(UnitType.Zerg_Drone, 3, false, true));
        list.add(new Plan(UnitType.Zerg_Drone, 3, false, true));
        list.add(new Plan(UnitType.Zerg_Drone, 3, false, true));
        list.add(new Plan(UnitType.Zerg_Spawning_Pool, 4, true, true));
        list.add(new Plan(UnitType.Zerg_Zergling, 5, false, true));
        list.add(new Plan(UnitType.Zerg_Zergling, 5, false, true));
        list.add(new Plan(UnitType.Zerg_Zergling, 5, false, true));

        return list;
    }

    public boolean isAllIn() { return false; }
}
