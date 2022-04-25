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

    }


}
