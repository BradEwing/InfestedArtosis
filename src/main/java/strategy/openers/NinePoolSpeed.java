package strategy.openers;

import bwapi.UnitType;
import bwapi.UpgradeType;
import plan.Plan;

import java.util.ArrayList;
import java.util.List;

public class NinePoolSpeed implements Opener {

    public OpenerName getName() { return OpenerName.NINE_POOL_SPEED; }

    public List<Plan> getBuildOrder() {

        List<Plan> list = new ArrayList<>();
        list.add(new Plan(UnitType.Zerg_Drone, 0, false, true));
        list.add(new Plan(UnitType.Zerg_Drone, 0, false, true));
        list.add(new Plan(UnitType.Zerg_Drone, 0, false, true));
        list.add(new Plan(UnitType.Zerg_Drone, 0, false, true));
        list.add(new Plan(UnitType.Zerg_Drone, 0, false, true));
        list.add(new Plan(UnitType.Zerg_Spawning_Pool, 1, true, true));
        list.add(new Plan(UnitType.Zerg_Overlord, 2, false, false));
        list.add(new Plan(UnitType.Zerg_Extractor, 3, true, false));
        list.add(new Plan(UnitType.Zerg_Drone, 3, false, false));
        list.add(new Plan(UnitType.Zerg_Drone, 4, false, false));
        list.add(new Plan(UnitType.Zerg_Zergling, 4, false, false));
        list.add(new Plan(UnitType.Zerg_Zergling, 4, false, false));
        list.add(new Plan(UnitType.Zerg_Zergling, 4, false, false));
        list.add(new Plan(UpgradeType.Metabolic_Boost, 5, true));

        return list;
    }

    public boolean isAllIn() { return false; }
}
