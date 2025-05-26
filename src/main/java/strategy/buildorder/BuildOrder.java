package strategy.buildorder;

import bwapi.Race;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.UpgradeType;
import bwem.Base;
import info.BaseData;
import info.GameState;
import info.TechProgression;
import info.UnitTypeCount;
import info.map.BuildingPlanner;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.UnitPlan;
import macro.plan.UpgradePlan;
import util.Time;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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

    protected int requiredSunkens(GameState gameState) {
        return 0;
    }

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
        Plan plan = new BuildingPlan(UnitType.Zerg_Spawning_Pool, gameState.getGameTime(), true);
        TilePosition buildPosition = gameState.getTechBuildingLocation(UnitType.Zerg_Spawning_Pool);
        plan.setBuildPosition(buildPosition);
        return plan;
    }

    protected Plan planSpire(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        techProgression.setSpire(true);
        Plan plan = new BuildingPlan(UnitType.Zerg_Spire, gameState.getGameTime(), true);
        TilePosition buildPosition = gameState.getTechBuildingLocation(UnitType.Zerg_Spire);
        plan.setBuildPosition(buildPosition);
        return plan;
    }

    protected Plan planExtractor(GameState gameState) {
        BaseData baseData = gameState.getBaseData();
        Plan plan = new BuildingPlan(UnitType.Zerg_Extractor, gameState.getGameTime().getFrames(), true);
        Unit geyser = baseData.reserveExtractor();
        plan.setBuildPosition(geyser.getTilePosition());
        return plan;
    }

    // planSunkenColony returns a set of Creep+Sunken plans
    // Subtracks 500 from priority as a stop gap to prioritize over existing items in queue.
    // TODO: Clear queue if defensive structure enters queue?
    protected Set<Plan> planSunkenColony(GameState gameState) {
        Set<Plan> plans = new HashSet<>();
        BaseData baseData = gameState.getBaseData();
        BuildingPlanner buildingPlanner = gameState.getBuildingPlanner();
        Optional<Base> eligibleBase = gameState.basesNeedingSunken(this.requiredSunkens(gameState)).stream().findFirst();
        if (!eligibleBase.isPresent()) {
            return plans;
        }
        baseData.reserveSunkenColony(eligibleBase.get());
        TilePosition location = buildingPlanner.getLocationForCreepColony(eligibleBase.get());
        Plan creepColonyPlan = new BuildingPlan(UnitType.Zerg_Creep_Colony, gameState.getGameTime().getFrames()-500, true, location);
        Plan sunkenColonyPlan = new BuildingPlan(UnitType.Zerg_Sunken_Colony, gameState.getGameTime().getFrames()+UnitType.Zerg_Creep_Colony.buildTime()-500, true, location);
        plans.add(creepColonyPlan);
        plans.add(sunkenColonyPlan);
        return plans;
    }

    protected Plan planUnit(GameState gameState, UnitType unitType) {
        UnitTypeCount count = gameState.getUnitTypeCount();
        count.planUnit(unitType);
        if (unitType == UnitType.Zerg_Drone) {
            gameState.addPlannedWorker(1);
        }
        return new UnitPlan(unitType, gameState.getGameTime().getFrames(), true);
    }

    protected Plan planUpgrade(GameState gameState, UpgradeType upgradeType) {
        TechProgression techProgression = gameState.getTechProgression();
        switch (upgradeType) {
            case Metabolic_Boost:
                techProgression.setPlannedMetabolicBoost(true);

        }

        return new UpgradePlan(upgradeType, gameState.getGameTime().getFrames(), false);
    }
}
