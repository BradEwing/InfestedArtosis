package strategy.strategies;

import bwapi.UnitType;
import bwapi.UpgradeType;
import planner.PlannedItem;
import strategy.Strategy;

import java.util.ArrayList;
import java.util.List;

public class NinePoolSpeed implements Strategy {

    public List<PlannedItem> getBuildOrder() {

        List<PlannedItem> list = new ArrayList<>();
        list.add(new PlannedItem(UnitType.Zerg_Drone, 0, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 0, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 0, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 0, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 0, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Spawning_Pool, 1, true, true));
        list.add(new PlannedItem(UnitType.Zerg_Overlord, 2, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Extractor, 3, true, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 3, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 4, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Zergling, 4, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Zergling, 4, false, false));
        list.add(new PlannedItem(UnitType.Zerg_Zergling, 4, false, false));
        list.add(new PlannedItem(UpgradeType.Metabolic_Boost, 5, true));

        return list;
    }

    public String getName() {
        return "9PoolSpeed";
    }

    public boolean playsFourPlayerMap() {
        return true;
    }
}
