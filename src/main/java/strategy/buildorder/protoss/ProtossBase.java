package strategy.buildorder.protoss;

import bwapi.Race;
import bwapi.UnitType;
import info.GameState;
import info.tracking.StrategyTracker;
import macro.plan.Plan;
import strategy.buildorder.BuildOrder;
import util.Time;

import java.util.Collections;
import java.util.List;

public class ProtossBase extends BuildOrder {

    final int EXCESS_MINERALS = 350;
    
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

    @Override
    protected int zerglingsNeeded(GameState gameState) {
        if (gameState.ourUnitCount(UnitType.Zerg_Spawning_Pool) < 1) {
            return 0;
        }

        int zerglings = 6;
        int currentZerglings = gameState.getUnitTypeCount().get(UnitType.Zerg_Zergling);
        int zealots = gameState.enemyUnitCount(UnitType.Protoss_Zealot);
        int availableMinerals = gameState.getResourceCount().availableMinerals();

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
        if (strategyTracker.isDetectedStrategy("CannonRush")) {
            int cannons = gameState.getObservedUnitTracker()
                    .getCountOfLivingUnits(UnitType.Protoss_Photon_Cannon);
            zerglings = 8 + (cannons * 3);
            if (availableMinerals > EXCESS_MINERALS) {
                zerglings += availableMinerals % UnitType.Zerg_Zergling.mineralPrice();
            }
        }

        zerglings += zealots * 2;

        if (currentZerglings >= zerglings) {
            return 0;
        }
        return zerglings;
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

        if (strategyTracker.isDetectedStrategy("2Gate") && gameTime.greaterThan(new Time(3, 20))) {
            sunkens += 1;
        }

        if (gameState.enemyUnitCount(UnitType.Protoss_Zealot) > 3) {
            sunkens += 1;
        }

        if (gameState.enemyUnitCount(UnitType.Protoss_Zealot) > 6) {
            sunkens += 1;
        }

        if (gameTime.greaterThan(new Time(10, 0)) && gameState.ourUnitCount(UnitType.Zerg_Drone) > 20) {
            sunkens += 1;
        }

        return sunkens;
    }
}
