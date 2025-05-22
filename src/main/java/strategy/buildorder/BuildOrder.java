package strategy.buildorder;

import bwapi.Race;
import bwapi.Unit;
import bwapi.UnitType;
import bwem.Base;
import info.BaseData;
import info.GameState;
import info.TechProgression;
import info.UnitTypeCount;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.UnitPlan;
import util.Time;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class BuildOrder {
    private final String name;
    protected Time activatedAt;

    protected BuildOrder(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public boolean shouldTransition(GameState gameState) {
        return false;
    }

    public Set<BuildOrder> transition(GameState gameState) {
        return new HashSet<>();
    }

    public abstract List<Plan> plan(GameState gameState);

    public abstract boolean playsRace(Race race);

    protected Plan planNewBase(GameState gameState) {
        Base base = gameState.reserveBase();
        // all possible bases are taken!
        if (base == null) {
            return null;
        }

        gameState.addPlannedHatchery(1);
        return new BuildingPlan(UnitType.Zerg_Hatchery, 2, true, base.getLocation());
    }

    protected Plan planLair(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        techProgression.setPlannedLair(true);
        return new BuildingPlan(UnitType.Zerg_Lair, gameState.getGameTime(), true);
    }

    protected Plan planSpawningPool(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        techProgression.setPlannedSpawningPool(true);
        return new BuildingPlan(UnitType.Zerg_Spawning_Pool, gameState.getGameTime(), true);
    }

    protected Plan planSpire(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        techProgression.setSpire(true);
        return new BuildingPlan(UnitType.Zerg_Spire, gameState.getGameTime(), true);
    }

    protected Plan planExtractor(GameState gameState) {
        BaseData baseData = gameState.getBaseData();
        Plan plan = new BuildingPlan(UnitType.Zerg_Extractor, gameState.getGameTime().getFrames(), true);
        Unit geyser = baseData.reserveExtractor();
        plan.setBuildPosition(geyser.getTilePosition());
        return plan;
    }

    protected Plan planUnit(GameState gameState, UnitType unitType) {
        UnitTypeCount count = gameState.getUnitTypeCount();
        count.planUnit(unitType);
        if (unitType == UnitType.Zerg_Drone) {
            gameState.addPlannedWorker(1);
        }
        return new UnitPlan(unitType, gameState.getGameTime().getFrames(), true);
    }
}
