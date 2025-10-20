package strategy.buildorder;

import bwapi.Race;
import bwapi.TechType;
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
import lombok.Getter;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.TechPlan;
import macro.plan.UnitPlan;
import macro.plan.UpgradePlan;
import util.Time;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Objects;

public abstract class BuildOrder {
    @Getter
    private final String name;
    protected Time activatedAt;

    protected BuildOrder(String name) {
        this.name = name;
    }

    public boolean shouldTransition(GameState gameState) {
        return false;
    }

    public Set<BuildOrder> transition(GameState gameState) {
        return new HashSet<>();
    }

    public abstract List<Plan> plan(GameState gameState);

    public abstract boolean playsRace(Race race);

    public boolean isOpener() { return false; }

    public boolean needLair() { return false; }

    /**
     * Returns true if Overlord Speed should be researched, based on Lair, game time and unit triggers.
     */
    public boolean needOverlordSpeed(GameState gameState) {
        if (gameState.ourUnitCount(bwapi.UnitType.Zerg_Lair) < 1) {
            return false;
        }
        if (gameState.getGameTime().greaterThan(new util.Time(12, 0))) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Zerg_Lurker) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Protoss_Templar_Archives) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Protoss_Fleet_Beacon) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Protoss_Dark_Templar) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Protoss_Observer) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Terran_Vulture_Spider_Mine) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Terran_Science_Vessel) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Protoss_Stargate) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Terran_Starport) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Terran_Valkyrie) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Terran_Wraith) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Protoss_Scout) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Protoss_Corsair) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Zerg_Devourer) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Zerg_Greater_Spire) > 0) {
            return true;
        }
        if (gameState.enemyUnitCount(bwapi.UnitType.Zerg_Hive) > 0) {
            return true;
        }
        return false;
    }

    protected int requiredSunkens(GameState gameState) {
        return 0;
    }

    protected int zerglingsNeeded(GameState gameState) { return 6; }

    protected Plan planNewBase(GameState gameState) {
        Base base = gameState.reserveBase();
        // all possible bases are taken!
        if (base == null) {
            return null;
        }

        gameState.addPlannedHatchery(1);
        return new BuildingPlan(UnitType.Zerg_Hatchery, gameState.getGameTime().getFrames(), true, base.getLocation());
    }

    protected Plan planLair(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        techProgression.setPlannedLair(true);
        return new BuildingPlan(UnitType.Zerg_Lair, 3, true);
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
        Plan plan = new BuildingPlan(UnitType.Zerg_Spire, 4, true);
        TilePosition buildPosition = gameState.getTechBuildingLocation(UnitType.Zerg_Spire);
        plan.setBuildPosition(buildPosition);
        return plan;
    }

    protected Plan planExtractor(GameState gameState) {
        BaseData baseData = gameState.getBaseData();
        Plan plan = new BuildingPlan(UnitType.Zerg_Extractor, 50, true);
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
        TilePosition location = buildingPlanner.getLocationForCreepColony(eligibleBase.get(), gameState.getOpponentRace());
        buildingPlanner.reservePlannedBuildingTiles(location, UnitType.Zerg_Creep_Colony);
        Plan creepColonyPlan = new BuildingPlan(UnitType.Zerg_Creep_Colony, 5, true, location);
        Plan sunkenColonyPlan = new BuildingPlan(UnitType.Zerg_Sunken_Colony, 5, true, location);
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
        if (unitType == UnitType.Zerg_Overlord) {
            int plannedSupply = gameState.getResourceCount().getPlannedSupply();
            gameState.getResourceCount().setPlannedSupply(plannedSupply+16);
        }
        return new UnitPlan(unitType, gameState.getGameTime().getFrames(), true);
    }

    protected Plan planUpgrade(GameState gameState, UpgradeType upgradeType) {
        TechProgression techProgression = gameState.getTechProgression();
        int priority = gameState.getGameTime().getFrames();
        switch (upgradeType) {
            case Metabolic_Boost:
                techProgression.setPlannedMetabolicBoost(true);
                break;
            case Muscular_Augments:
                techProgression.setPlannedMuscularAugments(true);
                break;
            case Grooved_Spines:
                techProgression.setPlannedGroovedSpines(true);
                break;
            case Zerg_Melee_Attacks:
                int meleeAttacks = techProgression.getMeleeUpgrades();
                techProgression.setMeleeUpgrades(meleeAttacks+1);
                break;
            case Zerg_Missile_Attacks:
                int missileAttacks = techProgression.getRangedUpgrades();
                techProgression.setRangedUpgrades(missileAttacks+1);
                break;
            case Zerg_Flyer_Carapace:
                int flyerCarapace = techProgression.getFlyerDefense();
                techProgression.setFlyerDefense(flyerCarapace+1);
                break;
            case Zerg_Carapace:
                int carapace = techProgression.getCarapaceUpgrades();
                techProgression.setCarapaceUpgrades(carapace+1);
                break;
            case Pneumatized_Carapace:
                techProgression.setPlannedOverlordSpeed(true);
                priority = 100;
                break;
        }

        return new UpgradePlan(upgradeType, priority, false);
    }

    protected Plan planHydraliskDen(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        techProgression.setPlannedDen(true);
        Plan plan = new BuildingPlan(UnitType.Zerg_Hydralisk_Den, gameState.getGameTime().getFrames(), true);
        TilePosition buildPosition = gameState.getTechBuildingLocation(UnitType.Zerg_Hydralisk_Den);
        plan.setBuildPosition(buildPosition);
        return plan;
    }

    protected Plan planEvolutionChamber(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        techProgression.setPlannedEvolutionChambers(techProgression.getPlannedEvolutionChambers() + 1);
        Plan plan = new BuildingPlan(UnitType.Zerg_Evolution_Chamber, gameState.getGameTime().getFrames(), true);
        TilePosition buildPosition = gameState.getTechBuildingLocation(UnitType.Zerg_Evolution_Chamber);
        plan.setBuildPosition(buildPosition);
        return plan;
    }

    protected Plan planTech(GameState gameState, TechType techType) {
        TechProgression techProgression = gameState.getTechProgression();
        int priority = gameState.getGameTime().getFrames();
        
        if (techType == TechType.Lurker_Aspect) {
            techProgression.setPlannedLurker(true);
            priority = 100;
        }
        
        return new TechPlan(techType, priority, true);
    }

    protected Plan planMacroHatchery(GameState gameState) {
        BuildingPlanner buildingPlanner = gameState.getBuildingPlanner();
        BaseData baseData = gameState.getBaseData();
        TilePosition location = buildingPlanner.getLocationForMacroHatchery(gameState.getOpponentRace(), baseData);

        if (location == null) {
            return null;
        }

        buildingPlanner.reservePlannedBuildingTiles(location, UnitType.Zerg_Hatchery);
        gameState.addPlannedHatchery(1);
        return new BuildingPlan(UnitType.Zerg_Hatchery, gameState.getGameTime().getFrames(), true, location);
    }

    /**
     * Determines if we are behind on resource hatcheries compared to the enemy.
     * Only applies to ZvZ matchups to maintain hatchery parity.
     *
     * @param gameState The current game state
     * @return true if enemy has more resource depots than us, false otherwise
     */
    protected boolean behindOnHatchery(GameState gameState) {
        // Only applies to ZvZ
        if (gameState.getOpponentRace() != Race.Zerg) {
            return false;
        }

        int ourHatcheries = gameState.ourUnitCount(UnitType.Zerg_Hatchery);
        int ourLairs = gameState.ourUnitCount(UnitType.Zerg_Lair);
        int ourHives = gameState.ourUnitCount(UnitType.Zerg_Hive);
        int ourPlanned = gameState.getPlannedHatcheries();
        int morphingLairs = (int) gameState.ourMorphingCount(UnitType.Zerg_Lair);
        
        int ourTotal = ourHatcheries + ourLairs + ourHives + ourPlanned + morphingLairs;

        int enemyHatcheries = gameState.enemyUnitCount(UnitType.Zerg_Hatchery);
        int enemyLairs = gameState.enemyUnitCount(UnitType.Zerg_Lair);
        int enemyHives = gameState.enemyUnitCount(UnitType.Zerg_Hive);
        int enemyTotal = enemyHatcheries + enemyLairs + enemyHives;

        return enemyTotal > ourTotal;
    }

    protected boolean behindOnBases(GameState gameState) {
        BaseData baseData = gameState.getBaseData();

        // Only apply after we have our natural expansion
        if (!baseData.hasNaturalExpansion()) {
            return false;
        }
        
        int ourBaseCount = baseData.currentAndReservedCount();
        int enemyTotal = gameState.enemyResourceDepotCount();
        return ourBaseCount <= enemyTotal;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BuildOrder that = (BuildOrder) o;
        return Objects.equals(this.name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), name);
    }
}
