package strategy.buildorder;

import bwapi.Race;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.GameState;
import info.TechProgression;
import macro.plan.Plan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NinePoolSpeed extends BuildOrder {
    public NinePoolSpeed() {
        super("9PoolSpeed");
    }

    @Override
    public boolean shouldTransition(GameState gameState) {
        return gameState.ourUnitCount(UnitType.Zerg_Spawning_Pool) > 0;
    }

    @Override
    public Set<BuildOrder> transition(GameState gameState) {
        Set<BuildOrder> next = new HashSet<>();
        next.add(new ThreeHatchMuta());
        return next;
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        List<Plan> plans = new ArrayList<>();
        TechProgression techProgression = gameState.getTechProgression();

        int droneCount     = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int overlordCount  = gameState.ourUnitCount(UnitType.Zerg_Overlord);
        int poolCount      = gameState.ourUnitCount(UnitType.Zerg_Spawning_Pool);
        int extractorCount = gameState.ourUnitCount(UnitType.Zerg_Extractor);
        int zerglingCount  = gameState.ourUnitCount(UnitType.Zerg_Zergling);

        if (droneCount < 9 && gameState.canPlanDrone()) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (droneCount >=  9 && poolCount < 1 && techProgression.canPlanPool()) {
            plans.add(planSpawningPool(gameState));
            return plans;
        }

        if (droneCount > 8 && overlordCount < 2) {
            plans.add(planUnit(gameState, UnitType.Zerg_Overlord));
            return plans;
        }

        if (droneCount < 6 && gameState.canPlanDrone()) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (zerglingCount < 3 && gameState.canPlanUnit(UnitType.Zerg_Zergling)) {
            plans.add(planUnit(gameState, UnitType.Zerg_Zergling));
            return plans;
        }

        if (extractorCount < 1 && gameState.canPlanExtractor()) {
            plans.add(planExtractor(gameState));
            return plans;
        }

        if (gameState.canPlanUpgrade(UpgradeType.Metabolic_Boost)) {
            plans.add(planUpgrade(gameState, UpgradeType.Metabolic_Boost));
            return plans;
        }

        return plans;
    }

    @Override
    public boolean playsRace(Race race) {
        return true;
    }

    @Override
    public boolean isOpener() { return true; }
}
