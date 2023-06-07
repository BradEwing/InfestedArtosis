package strategy.openers;

import bwapi.UnitType;
import planner.Plan;

import java.util.ArrayList;
import java.util.List;

public class FourPool implements Opener {

    public OpenerName getName() { return OpenerName.FOUR_POOL; }

    public List<Plan> getBuildOrder() {

        List<Plan> list = new ArrayList<>();
        list.add(new Plan(UnitType.Zerg_Spawning_Pool, 0, true, true ));
        list.add(new Plan(UnitType.Zerg_Drone, 1, false, true));
        list.add(new Plan(UnitType.Zerg_Zergling, 2, false, false));
        list.add(new Plan(UnitType.Zerg_Zergling, 2, false, false));
        list.add(new Plan(UnitType.Zerg_Zergling, 2, false, false));
        list.add(new Plan(UnitType.Zerg_Zergling, 2, false, false));
        list.add(new Plan(UnitType.Zerg_Zergling, 2, false, false));

        return list;
    }

    public boolean isAllIn() { return true; }
}
