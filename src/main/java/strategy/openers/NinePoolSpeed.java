package strategy.openers;

import bwapi.UnitType;
import bwapi.UpgradeType;
import planner.PlannedItem;

import java.util.ArrayList;
import java.util.List;

public class NinePoolSpeed implements Opener {

    public OpenerName getName() { return OpenerName.NINE_POOL_SPEED; }

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

    public boolean playsFourPlayerMap() {
        return true;
    }

    public boolean isAllIn() { return false; }
}
