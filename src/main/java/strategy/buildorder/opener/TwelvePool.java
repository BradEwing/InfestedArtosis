package strategy.buildorder.opener;

import bwapi.Race;
import bwapi.UnitType;
import info.GameState;
import info.TechProgression;
import macro.plan.Plan;
import strategy.buildorder.BuildOrder;
import strategy.buildorder.protoss.ThreeHatchMuta;
import strategy.buildorder.terran.TwoHatchMuta;
import strategy.buildorder.zerg.OneHatchSpire;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TwelvePool extends BuildOrder {
    public TwelvePool() {
        super("12Pool");
    }

    @Override
    public boolean shouldTransition(GameState gameState) {
        return gameState.ourUnitCount(UnitType.Zerg_Spawning_Pool) > 0 || gameState.ourUnitCount(UnitType.Zerg_Drone) >= 12;
    }

    @Override
    public Set<BuildOrder> transition(GameState gameState) {
        Set<BuildOrder> next = new HashSet<>();
        Race opponentRace = gameState.getOpponentRace();
        switch (opponentRace) {
            case Protoss:
                next.add(new ThreeHatchMuta());
                return next;
            case Zerg:
                next.add(new OneHatchSpire());
                return next;
            case Terran:
                next.add(new TwoHatchMuta());
                return next;
        }
        return next;
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        List<Plan> plans = new ArrayList<>();
        TechProgression techProgression = gameState.getTechProgression();

        int droneCount    = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int overlordCount = gameState.ourUnitCount(UnitType.Zerg_Overlord);

        if (droneCount < 8) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (overlordCount < 2) {
            plans.add(planUnit(gameState, UnitType.Zerg_Overlord));
            return plans;
        }

        if (droneCount < 12) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (techProgression.canPlanPool()) {
            plans.add(planSpawningPool(gameState));
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
