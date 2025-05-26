package strategy.buildorder;

import bwapi.Race;
import bwapi.UnitType;
import info.GameState;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.UnitPlan;

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
        List<Plan> list = new ArrayList<>();
        list.add(new UnitPlan(UnitType.Zerg_Drone, 1, false));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 1, false));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 1, false));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 1, false));
        list.add(new UnitPlan(UnitType.Zerg_Overlord, 2,  true));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 3, false));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 3, true));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 3, true));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 3, true));
        list.add(new BuildingPlan(UnitType.Zerg_Spawning_Pool, 4, true));
        list.add(new BuildingPlan(UnitType.Zerg_Extractor, 5, true));
        list.add(new BuildingPlan(UnitType.Zerg_Hatchery, 6, true));
        list.add(new UnitPlan(UnitType.Zerg_Zergling, 7, true));
        list.add(new UnitPlan(UnitType.Zerg_Zergling, 7, true));
        list.add(new UnitPlan(UnitType.Zerg_Zergling, 7, true));
        return list;
    }

    @Override
    public boolean playsRace(Race race) {
        return true;
    }
}
