package strategy.buildorder;

import bwapi.Race;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.GameState;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.UnitPlan;
import macro.plan.UpgradePlan;

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
        List<Plan> list = new ArrayList<>();
        list.add(new UnitPlan(UnitType.Zerg_Drone, 0, true));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 0, true));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 0, true));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 0, true));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 0, true));
        list.add(new BuildingPlan(UnitType.Zerg_Spawning_Pool, 1, true));
        list.add(new UnitPlan(UnitType.Zerg_Overlord, 2, false));
        list.add(new UnitPlan(UnitType.Zerg_Drone, 3, false));
        list.add(new UnitPlan(UnitType.Zerg_Zergling, 4, false));
        list.add(new UnitPlan(UnitType.Zerg_Zergling, 4, false));
        list.add(new UnitPlan(UnitType.Zerg_Zergling, 4, false));
        list.add(new BuildingPlan(UnitType.Zerg_Extractor, 2, true));
        list.add(new UpgradePlan(UpgradeType.Metabolic_Boost, 5, true));
        return list;
    }

    @Override
    public boolean playsRace(Race race) {
        return true;
    }
}
