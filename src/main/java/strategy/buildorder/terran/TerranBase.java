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
     */
    @Override
    protected int requiredSunkens(GameState gameState) {
        int sunkens = 0;
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        Time gameTime = gameState.getGameTime();

        int medicCount = gameState.enemyUnitCount(UnitType.Terran_Medic);
        int firebatCount = gameState.enemyUnitCount(UnitType.Terran_Firebat);
        int marineCount = gameState.enemyUnitCount(UnitType.Terran_Firebat);
        int vultureCount = gameState.enemyUnitCount(UnitType.Terran_Vulture);
        int factoryCount = gameState.enemyUnitCount(UnitType.Terran_Factory);
        int bioCount = marineCount + firebatCount + medicCount;
        boolean possibleEarlyBioPressire = bioCount > 5 && gameTime.lessThanOrEqual(new Time (5, 0));
        boolean is2RaxAcademy = strategyTracker.isDetectedStrategy("2RaxAcademy");
        if (gameTime.lessThanOrEqual(new Time(8, 0))) {
            if (is2RaxAcademy && gameTime.greaterThan(new Time(4, 0))) {
                sunkens = 3;
            } else if (possibleEarlyBioPressire) {
                sunkens += 1;
            }
        }

        if (gameTime.greaterThan(new Time(8, 0)) && gameState.ourUnitCount(UnitType.Zerg_Drone) > 14) {
            sunkens += 1;
        }
        if (factoryCount > 0 || vultureCount > 1) {
            sunkens += 1;
        }

        return sunkens;
    }
}

