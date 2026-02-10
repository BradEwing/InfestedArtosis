package strategy.buildorder.opener;

import bwapi.Race;
import bwapi.UnitType;
import info.GameState;
import info.TechProgression;
import macro.plan.Plan;
import strategy.buildorder.BuildOrder;
import util.Time;

import java.util.ArrayList;
import java.util.List;

public class FourPool extends BuildOrder {
    public FourPool() {
        super("4Pool");
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        Time currentTime = gameState.getGameTime();
        TechProgression techProgression = gameState.getTechProgression();
        List<Plan> list = new ArrayList<>();
        boolean needPool = techProgression.canPlanPool();

        // Don't plan on first frame, otherwise the drone assigned to build the spawning pool won't gather minerals
        if (currentTime.lessThanOrEqual(new Time(1))) {
            return list;
        }

        if (needPool) {
            Plan poolPlan = this.planSpawningPool(gameState);
            list.add(poolPlan);
            return list;
        }

        final int neededDrones = 4 - gameState.ourUnitCount(UnitType.Zerg_Drone);
        if (neededDrones > 0) {
            Plan dronePlan = this.planUnit(gameState, UnitType.Zerg_Drone);
            list.add(dronePlan);
            return list;
        }

        final int neededZerglings = 24 - gameState.ourUnitCount(UnitType.Zerg_Zergling);
        if (techProgression.isSpawningPool() && neededZerglings > 0) { 
            Plan zerglingPlan = this.planUnit(gameState, UnitType.Zerg_Zergling);
            list.add(zerglingPlan);   
        }

        return list;
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
