package strategy.buildorder.terran;

import bwapi.Race;
import bwapi.UnitType;
import info.GameState;
import info.tracking.StrategyTracker;
import macro.plan.Plan;
import strategy.buildorder.BuildOrder;
import util.Time;

import java.util.Collections;
import java.util.List;

public class TerranBase extends BuildOrder {
    protected TerranBase(String name) {
        super(name);
    }

    @Override
    protected int zerglingsNeeded(GameState gameState) {
        if (gameState.ourUnitCount(UnitType.Zerg_Spawning_Pool) < 1) {
            return 0;
        }

        int zerglings = 4;
        int currentZerglings = gameState.getUnitTypeCount().get(UnitType.Zerg_Zergling);
        int medicCount = gameState.enemyUnitCount(UnitType.Terran_Medic);
        int firebatCount = gameState.enemyUnitCount(UnitType.Terran_Firebat);
        int marineCount = gameState.enemyUnitCount(UnitType.Terran_Marine);
        int bunkerCount = gameState.enemyUnitCount(UnitType.Terran_Bunker);

        zerglings += medicCount;
        zerglings -= firebatCount * 2;
        zerglings += marineCount * 2;
        zerglings += bunkerCount * 4;
        if (currentZerglings >= zerglings) {
            return 0;
        }
        return Math.min(40, zerglings);
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        return Collections.emptyList();
    }

    @Override
    public boolean playsRace(Race race) {
        return race == Race.Terran;
    }

    /**
     * requiredSunkens per base
     *
     * Take a sunken if 2Gate is detected.
     * Taken a sunken if 6+ zealots are detected
     * Take a sunken if game time over 10 minutes and drone supply is healthy (20+)
     */
    @Override
    protected int requiredSunkens(GameState gameState) {
        int sunkens = 0;
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        Time gameTime = gameState.getGameTime();

        int medicCount = gameState.enemyUnitCount(UnitType.Terran_Medic);
        int firebatCount = gameState.enemyUnitCount(UnitType.Terran_Firebat);
        int marineCount = gameState.enemyUnitCount(UnitType.Terran_Firebat);
        int factoryCount = gameState.enemyUnitCount(UnitType.Terran_Factory);
        if (gameTime.lessThanOrEqual(new Time(8, 0))) {
            if (medicCount >= 2) {
                sunkens += 1;
            }
            if (firebatCount >= 4) {
                sunkens += 1;
            }
            if (marineCount > 8) {
                sunkens += 1;
            }
        }

        if (gameState.enemyUnitCount(UnitType.Protoss_Zealot) > 5) {
            sunkens += 1;
        }

        if (gameState.enemyUnitCount(UnitType.Protoss_Zealot) > 10) {
            sunkens += 1;
        }

        if (gameTime.greaterThan(new Time(8, 0)) && gameState.ourUnitCount(UnitType.Zerg_Drone) > 14) {
            sunkens += 1;
        }
        if (factoryCount > 0) {
            sunkens += 1;
        }

        return sunkens;
    }
}

