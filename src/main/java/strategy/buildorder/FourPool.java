package strategy.buildorder;

import bwapi.Race;
import bwapi.UnitType;
import info.GameState;
import info.TechProgression;
import macro.plan.Plan;

import java.util.ArrayList;
import java.util.List;

public class FourPool extends BuildOrder {
    public FourPool() {
        super("4Pool");
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        List<Plan> list = new ArrayList<>();
        boolean needPool = techProgression.canPlanPool();

        if (needPool) {
            Plan poolPlan = this.planSpawningPool(gameState);
            list.add(poolPlan);
            return list;
        }

        final int neededDrones = 4 - gameState.ourUnitCount(UnitType.Zerg_Drone);
        if (neededDrones > 0) {
            for (int i = 0; i < neededDrones; i++) {
                Plan dronePlan = this.planUnit(gameState, UnitType.Zerg_Drone);
                list.add(dronePlan);
            }
        }

        final int neededZerglings = 24 - gameState.ourUnitCount(UnitType.Zerg_Zergling);
        if (techProgression.isSpawningPool() && neededZerglings > 0) {
            for (int i = 0; i < neededZerglings; i++) {
                Plan zerglingPlan = this.planUnit(gameState, UnitType.Zerg_Zergling);
                list.add(zerglingPlan);
            }
        }

        return list;
    }

    @Override
    public boolean playsRace(Race race) {
        return true;
    }

    @Override
    public boolean isOpener() { return true; }
}
