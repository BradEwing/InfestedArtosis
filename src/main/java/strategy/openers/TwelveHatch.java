package strategy.openers;

import bwapi.UnitType;
import planner.PlannedItem;
import strategy.Opener;

import java.util.ArrayList;
import java.util.List;

public class TwelveHatch implements Opener {

    public List<PlannedItem> getBuildOrder() {

        List<PlannedItem> list = new ArrayList<>();
        list.add(new PlannedItem(UnitType.Zerg_Drone, 1, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 1, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 1, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 1, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Overlord, 2, false, true));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 3, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 3, false, true));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 3, false, true));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 3, false, true));
        list.add(new PlannedItem(UnitType.Zerg_Hatchery, 4, true, true));

        return list;
    }

    public boolean playsFourPlayerMap() {
        return true;
    }

    public boolean isAllIn() { return false; }
}
