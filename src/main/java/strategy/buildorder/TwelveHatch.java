package strategy.buildorder;

import bwapi.Race;
import bwapi.UnitType;
import info.BaseData;
import info.GameState;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.UnitPlan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TwelveHatch extends BuildOrder {

    public TwelveHatch() {
        super("12Hatch");
    }

    @Override
    public boolean playsRace(Race race) {
        return race != Race.Random;
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        List<Plan> plans = new ArrayList<>();
        plans.add(new UnitPlan(UnitType.Zerg_Drone, 1, false));
        plans.add(new UnitPlan(UnitType.Zerg_Drone, 1, false));
        plans.add(new UnitPlan(UnitType.Zerg_Drone, 1, false));
        plans.add(new UnitPlan(UnitType.Zerg_Drone, 1, false));
        plans.add(new UnitPlan(UnitType.Zerg_Overlord, 2, true));
        plans.add(new UnitPlan(UnitType.Zerg_Drone, 3, false));
        plans.add(new UnitPlan(UnitType.Zerg_Drone, 3, true));
        plans.add(new UnitPlan(UnitType.Zerg_Drone, 3, true));
        plans.add(new UnitPlan(UnitType.Zerg_Drone, 3, true));
        plans.add(new BuildingPlan(UnitType.Zerg_Hatchery, 4, true));
        return plans;
    }

    @Override
    public boolean shouldTransition(GameState gameState) {
        BaseData baseData = gameState.getBaseData();
        return baseData.currentBaseCount() >= 2;
    }

    @Override
    public Set<BuildOrder> transition(GameState gameState) {
        Set<BuildOrder> next = new HashSet<>();
        next.add(new ThreeHatchMuta());
        return next;
    }
}
