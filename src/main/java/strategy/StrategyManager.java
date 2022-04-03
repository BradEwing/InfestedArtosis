package strategy;

import bwapi.Game;
import bwapi.Player;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.UpgradeType;
import planner.PlannedItem;

import java.util.ArrayList;
import java.util.List;

// StrategyManager is responsible for determining strategies and queueing tasks to the build order
public class StrategyManager {
    private Game game;

    public StrategyManager(Game game) {
        this.game = game;
    }

    private int currentPriority = 4; // hardcoded to 9pool

    private boolean alwaysMacro;

    public void onFrame() {
        Player self = game.self();
        // Macro builder kicks in at 10 supply
        if (alwaysMacro || self.supplyUsed() < 20) {
            return;
        }

        // Once we come here, we never stop
        alwaysMacro = true;

        List<PlannedItem> list = new ArrayList<>();
        // Code in some basic macro loops, these will make assumptions that below build order has executed

        // Build hatch if
        if (self.minerals() > 300 && numUnits(UnitType.Zerg_Larva) == 0) {
            list.add(new PlannedItem(UnitType.Zerg_Hatchery, currentPriority, true));
        }
    }

    // TODO: util
    private int numUnits(UnitType target) {
        int numUnits = 0;
        for (Unit unit: game.getAllUnits()) {
            if (unit.getType() == target) {
                numUnits += 1;
            }
        }

        return numUnits;
    }

    // TODO: Define build orders in JSON and/or generate them dynamically based off of training / learned games
    // For now, hardcode a nine pool to get us going
    private List<PlannedItem> ninePool() {
        List<PlannedItem> list = new ArrayList<>();
        list.add(new PlannedItem(UnitType.Zerg_Drone, 0, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 0, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 0, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 0, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 0, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 0, false));
        list.add(new PlannedItem(UnitType.Zerg_Spawning_Pool, 1, true));
        list.add(new PlannedItem(UnitType.Zerg_Overlord, 2, false));
        list.add(new PlannedItem(UnitType.Zerg_Extractor, 2, true));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 3, false));
        list.add(new PlannedItem(UnitType.Zerg_Zergling, 4, false));
        list.add(new PlannedItem(UnitType.Zerg_Zergling, 4, false));
        list.add(new PlannedItem(UnitType.Zerg_Zergling, 4, false));
        list.add(new PlannedItem(UpgradeType.Metabolic_Boost, 4));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 4, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 4, false));
        list.add(new PlannedItem(UnitType.Zerg_Zergling, 4, false));
        list.add(new PlannedItem(UnitType.Zerg_Overlord, 4, false));
        list.add(new PlannedItem(UnitType.Zerg_Drone, 4, false));

        return list;
    }
}
