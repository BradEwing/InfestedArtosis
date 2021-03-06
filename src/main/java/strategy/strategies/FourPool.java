package strategy.strategies;

import bwapi.UnitType;
import bwapi.UpgradeType;
import planner.PlannedItem;
import strategy.Strategy;

import java.util.ArrayList;
import java.util.List;

public class FourPool implements Strategy {

    public List<PlannedItem> getBuildOrder() {

        List<PlannedItem> list = new ArrayList<>();
        list.add(new PlannedItem(UnitType.Zerg_Spawning_Pool, 0, true));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 1, false));
        list.add(new PlannedItem(UnitType.Zerg_Zergling, 2, false));
        list.add(new PlannedItem(UnitType.Zerg_Zergling, 2, false));
        list.add(new PlannedItem(UnitType.Zerg_Zergling, 2, false));
        list.add(new PlannedItem(UnitType.Zerg_Zergling, 2, false));
        list.add(new PlannedItem(UnitType.Zerg_Zergling, 2, false));

        return list;
    }

    public String getName() {
        return "FourPool";
    }

    public boolean playsFourPlayerMap() {
        return false;
    }
}
