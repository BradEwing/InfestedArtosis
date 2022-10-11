package strategy.strategies;

import bwapi.UnitType;
import bwapi.UpgradeType;
import planner.PlannedItem;
import strategy.Strategy;

import java.util.ArrayList;
import java.util.List;

public class OverPool implements Strategy {

    public List<PlannedItem> getBuildOrder() {

        List<PlannedItem> list = new ArrayList<>();
        list.add(new PlannedItem(UnitType.Zerg_Drone, 0, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 0, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 0, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 0, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 0, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Overlord, 1, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Spawning_Pool, 2, true, true));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 3, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 3, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 3, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Zergling, 4, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Zergling, 4, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Zergling, 4, false, false));

        return list;
    }

    public boolean playsFourPlayerMap() {
        return true;
    }

    public boolean isAllIn() { return false; }
}
