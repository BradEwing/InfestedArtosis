package strategy.strategies;

import bwapi.UnitType;
import planner.PlannedItem;
import strategy.Strategy;

import java.util.ArrayList;
import java.util.List;

public class FivePool implements Strategy {

    public List<PlannedItem> getBuildOrder() {

        List<PlannedItem> list = new ArrayList<>();
        list.add(new PlannedItem(UnitType.Zerg_Drone, -1, false, true));
        list.add(new PlannedItem(UnitType.Zerg_Spawning_Pool, 0, true, true));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 1, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Zergling, 2, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Zergling, 2, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Zergling, 2, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Zergling, 2, false, false));
        
        return list;
    }

    public String getName() {
        return "FivePool";
    }

    public boolean playsFourPlayerMap() {
        return false;
    }
}
