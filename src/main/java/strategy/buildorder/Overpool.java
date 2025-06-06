package strategy.buildorder;

import bwapi.Race;
import bwapi.UnitType;
import info.GameState;
import info.TechProgression;
import macro.plan.Plan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Overpool extends BuildOrder {
    public Overpool() {
        super("Overpool");
    }

    @Override
    public boolean shouldTransition(GameState gameState) {
        return gameState.getTechProgression().isSpawningPool();
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

        // Count existing units/buildings
        int droneCount    = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int overlordCount = gameState.ourUnitCount(UnitType.Zerg_Overlord);
        int zerglingCount     = gameState.ourUnitCount(UnitType.Zerg_Zergling);

        if (droneCount < 8 && gameState.canPlanDrone()) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (droneCount > 7 && overlordCount < 2) {
            plans.add(planUnit(gameState, UnitType.Zerg_Overlord));
            return plans;
        }

        if (overlordCount > 1 && techProgression.canPlanPool()) {
            plans.add(planSpawningPool(gameState));
            return plans;
        }


        if (zerglingCount < 2 && gameState.canPlanUnit(UnitType.Zerg_Zergling)) {
            plans.add(planUnit(gameState, UnitType.Zerg_Zergling));
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
