package strategy.buildorder;

import bwapi.Race;
import bwapi.UnitType;
import info.GameState;
import info.tracking.StrategyTracker;
import macro.plan.Plan;

import java.util.Collections;
import java.util.List;

public class ProtossBase extends BuildOrder {
    protected ProtossBase(String name) {
        super(name);
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        return Collections.emptyList();
    }

    @Override
    public boolean playsRace(Race race) {
        return false;
    }

    protected int zerglingsNeeded(GameState gameState) {
        if (gameState.ourUnitCount(UnitType.Zerg_Spawning_Pool) < 1) {
            return 0;
        }

        int zerglings = 6;
        int currentZerglings = gameState.getUnitTypeCount().get(UnitType.Zerg_Zergling);
        int zealots = gameState.enemyUnitCount(UnitType.Protoss_Zealot);

        StrategyTracker strategyTracker = gameState.getStrategyTracker();

        if (strategyTracker.isDetectedStrategy("2Gate")) {
            zerglings = 12;
        }
        if (strategyTracker.isDetectedStrategy("1GateCore")) {
            zerglings = 4;
        }
        if (strategyTracker.isDetectedStrategy("FFE")) {
            zerglings = 2;
        }
        if (strategyTracker.isDetectedStrategy("NexusFirst")) {
            zerglings = 2;
        }

        zerglings += (zealots * 2);

        if (currentZerglings >= zerglings) {
            return 0;
        }
        return zerglings;
    }
}
