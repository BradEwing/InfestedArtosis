package strategy;

import bwapi.Game;

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
