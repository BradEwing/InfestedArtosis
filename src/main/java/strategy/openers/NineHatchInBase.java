package strategy.openers;

import bwapi.UnitType;
import plan.Plan;

import java.util.ArrayList;
import java.util.List;

public class NineHatchInBase implements Opener {

    public OpenerName getName() { return OpenerName.NINE_HATCH_IN_BASE; }

    public List<Plan> getBuildOrder() {

        List<Plan> list = new ArrayList<>();
        list.add(new Plan(UnitType.Zerg_Drone, 0, false, true));
        list.add(new Plan(UnitType.Zerg_Drone, 0, false, true));
        list.add(new Plan(UnitType.Zerg_Drone, 0, false, true));
        list.add(new Plan(UnitType.Zerg_Drone, 0, false, true));
        list.add(new Plan(UnitType.Zerg_Drone, 0, false, true));
        list.add(new Plan(UnitType.Zerg_Hatchery, 1, true, true));
        list.add(new Plan(UnitType.Zerg_Spawning_Pool, 2, true, true));
        list.add(new Plan(UnitType.Zerg_Drone, 3, false, false));
        list.add(new Plan(UnitType.Zerg_Drone, 3, false, false));
        list.add(new Plan(UnitType.Zerg_Overlord, 4, false, false));
        list.add(new Plan(UnitType.Zerg_Zergling, 5, false, false));
        list.add(new Plan(UnitType.Zerg_Zergling, 5, false, false));
        list.add(new Plan(UnitType.Zerg_Zergling, 5, false, false));

        return list;
    }

    public boolean isAllIn() { return false; }

}
