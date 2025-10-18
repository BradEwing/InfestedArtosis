package strategy.buildorder.zerg;

import bwapi.Race;
import bwapi.UnitType;
import info.GameState;
import macro.plan.Plan;
import strategy.buildorder.BuildOrder;

import java.util.Collections;
import java.util.List;

public class ZergBase extends BuildOrder {
    protected ZergBase(String name) {
        super(name);
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        return Collections.emptyList();
    }

    @Override
    public boolean playsRace(Race race) {
        return race == Race.Zerg;
    }

    @Override
    protected int zerglingsNeeded(GameState gameState) {
        if (gameState.ourUnitCount(UnitType.Zerg_Spawning_Pool) < 1) {
            return 0;
        }

        int zerglings = 10;
        int currentZerglings = gameState.ourUnitCount(UnitType.Zerg_Zergling);
        int enemyZerglings = gameState.enemyUnitCount(UnitType.Zerg_Zergling);
        int lairCount = gameState.ourUnitCount(UnitType.Zerg_Lair);
        boolean hasMetabolicBoost = gameState.getTechProgression().isMetabolicBoost();

        zerglings += enemyZerglings;
        if (lairCount > 0) {
            zerglings += 6;
        }
        if (hasMetabolicBoost) {
            zerglings += 2;
        }

        final int excessMinerals = gameState.getResourceCount().availableMinerals() - 400;
        if (excessMinerals > 0) {
            int excessZerglings = excessMinerals / 50;
            zerglings += (excessZerglings * 2);
        }

        zerglings = Math.min(zerglings, 40);

        if (currentZerglings >= zerglings) {
            return 0;
        }
        return zerglings;
    }
}
