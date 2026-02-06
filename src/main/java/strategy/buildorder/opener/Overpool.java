package strategy.buildorder.opener;

import bwapi.Race;
import bwapi.UnitType;
import info.GameState;
import info.TechProgression;
import macro.plan.Plan;
import strategy.buildorder.BuildOrder;
import strategy.buildorder.protoss.ThreeHatchHydra;
import strategy.buildorder.protoss.ThreeHatchMuta;
import strategy.buildorder.terran.ThreeHatchLurker;
import strategy.buildorder.terran.TwoHatchMuta;
import strategy.buildorder.zerg.OneHatchSpire;

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

        return gameState.getOpponentRace() != Race.Unknown && gameState.ourUnitCount(UnitType.Zerg_Spawning_Pool) > 0;
    }

    @Override
    public Set<BuildOrder> transition(GameState gameState) {
        Set<BuildOrder> next = new HashSet<>();
        Race opponentRace = gameState.getOpponentRace();
        switch (opponentRace) {
            case Protoss:
                next.add(new ThreeHatchMuta());
                next.add(new ThreeHatchHydra());
                return next;
            case Zerg:
                next.add(new OneHatchSpire());
                return next;
            case Terran:
                next.add(new TwoHatchMuta());
                next.add(new ThreeHatchLurker());
                return next;
            default:
                break;
        }
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

        if (droneCount < 9 && gameState.canPlanDrone()) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (droneCount > 8 && overlordCount < 2) {
            plans.add(planUnit(gameState, UnitType.Zerg_Overlord));
            return plans;
        }

        if (overlordCount > 1 && techProgression.canPlanPool()) {
            plans.add(planSpawningPool(gameState));
            return plans;
        }

        boolean pool = techProgression.isPlannedSpawningPool() || techProgression.isSpawningPool();
        if (pool && droneCount < 10) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (zerglingCount < this.zerglingsNeeded(gameState) && gameState.canPlanUnit(UnitType.Zerg_Zergling)) {
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
    public boolean isOpener() { 
        return true; 
    }
}
