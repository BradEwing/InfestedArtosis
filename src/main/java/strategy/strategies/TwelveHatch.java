package strategy.strategies;

import bwapi.UnitType;
import bwapi.UpgradeType;
import planner.PlannedItem;
import strategy.Strategy;

import java.util.ArrayList;
import java.util.List;

public class TwelveHatch implements Strategy {

    public List<PlannedItem> getBuildOrder() {

        List<PlannedItem> list = new ArrayList<>();
        list.add(new PlannedItem(UnitType.Zerg_Drone, 1, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 1, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 1, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 1, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 1, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 1, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 1, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 1, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Hatchery, 2, false, true));

        return list;
    }

    public String getName() {
        return "TwelveHatch";
    }

    public boolean playsFourPlayerMap() {
        return true;
    }
}
