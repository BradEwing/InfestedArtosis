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
import macro.HatcheryCapacity;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.TechPlan;
import macro.plan.UnitPlan;
import macro.plan.UpgradePlan;
import util.Time;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Objects;

public abstract class BuildOrder {
    private static final int EARLY_RUSH_SECOND_SUNKEN_ATTACKERS = 4;
    private static final int EARLY_RUSH_MIN_ZERGLINGS = 6;
    private static final int EMERGENCY_DEFENSE_PRIORITY = 1;
    private static final int DEFAULT_COLONY_PRIORITY = 5;
    private static final int UNKNOWN_RACE_BASE_TARGET = 2;
    private static final int UNKNOWN_RACE_ZERGLING_PLANS = 2;

    @Getter
    private final String name;
    protected Time activatedAt;

    protected BuildOrder(String name) {
        this.name = name;
    }

    public boolean shouldTransition(GameState gameState) {
        return gameState.getOpponentRace() != Race.Unknown && openerComplete(gameState);
    }

    /**
     * True when the opener's scripted build has produced everything it will produce. Build
     * orders that are not openers leave this false.
     */
    protected boolean openerComplete(GameState gameState) {
        return false;
    }

    public Set<BuildOrder> transition(GameState gameState) {
        return new HashSet<>();
    }

    public abstract List<Plan> plan(GameState gameState);

    public abstract boolean playsRace(Race race);

    public boolean isOpener() { 
        return false; 
    }

    public boolean needLair() {
        return false;
    }

    public boolean needHive() {
        return false;
    }

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

    protected int requiredSpores(GameState gameState) {
        return 0;
    }

    protected int zerglingsNeeded(GameState gameState) {
        return 6;
    }

    private int earlyRushSunkens(GameState gameState) {
        int attackers = gameState.visibleEnemyMobileGroundCombatUnitsAtOurBases();
        return attackers >= EARLY_RUSH_SECOND_SUNKEN_ATTACKERS ? 2 : 1;
    }

    private int earlyRushZerglings(GameState gameState) {
        return Math.max(EARLY_RUSH_MIN_ZERGLINGS, 2 * gameState.enemyMobileGroundCombatUnitCount());
    }

    /**
     * Emergency defense reachable from every build order, including openers that
     * never plan static defense. 
     * Returned plans carry reservations (sunken base, build tiles, planned unit counts) and
     * must be added to the production queue by the caller.
     */
    public List<Plan> planEmergencyDefense(GameState gameState) {
        List<Plan> plans = new ArrayList<>();
        if (!gameState.isEarlyRushed()) {
            return plans;
        }
        int sunkenTarget = Math.max(this.requiredSunkens(gameState), earlyRushSunkens(gameState));
        boolean poolComplete = gameState.getTechProgression().isSpawningPool();
        if (poolComplete && !gameState.basesNeedingSunken(sunkenTarget).isEmpty()) {
            plans.addAll(this.planSunkenColony(gameState, EMERGENCY_DEFENSE_PRIORITY, sunkenTarget));
        }
        int zerglingTarget = Math.max(this.zerglingsNeeded(gameState), earlyRushZerglings(gameState));
        int zerglingCount = gameState.ourUnitCount(UnitType.Zerg_Zergling);
        if (zerglingCount < zerglingTarget && gameState.canPlanUnit(UnitType.Zerg_Zergling)) {
            Plan zerglingPlan = this.planUnit(gameState, UnitType.Zerg_Zergling);
            zerglingPlan.setPriority(EMERGENCY_DEFENSE_PRIORITY);
            plans.add(zerglingPlan);
        }
        return plans;
    }

    /**
     * Race agnostic macro continuation for an opener that has finished its build order but cannot
     * transition, because a random opponent's race is still unknown.
     * <p>
     * Ensures defensive zerglings, a natural expansion, drones and finally gas if they were not
     * covered by the initial opener.
     */
    protected List<Plan> planUnknownRaceMacro(GameState gameState) {
        List<Plan> plans = new ArrayList<>();
        TechProgression techProgression = gameState.getTechProgression();
        boolean raceUnknown = gameState.getOpponentRace() == Race.Unknown;
        boolean poolPlanned = techProgression.isPlannedSpawningPool() || techProgression.isSpawningPool();
        if (!raceUnknown || !openerComplete(gameState) || !poolPlanned || gameState.isEarlyRushed()) {
            return plans;
        }

        if (gameState.ourUnitCount(UnitType.Zerg_Zergling) == 0) {
            for (int i = 0; i < UNKNOWN_RACE_ZERGLING_PLANS; i++) {
                plans.add(this.planUnit(gameState, UnitType.Zerg_Zergling));
            }
            return plans;
        }

        int plannedAndCurrentBases = gameState.getPlannedHatcheries() + gameState.getBaseData().currentBaseCount();
        if (plannedAndCurrentBases < UNKNOWN_RACE_BASE_TARGET) {
            Plan hatcheryPlan = this.planNewBase(gameState);
            if (hatcheryPlan != null) {
                plans.add(hatcheryPlan);
                return plans;
            }
        }

        if (gameState.canPlanDrone()) {
            plans.add(this.planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (gameState.canPlanExtractor()) {
            plans.add(this.planExtractor(gameState));
        }

        return plans;
    }

    /**
     * Plans a hatchery that claims a base. Returns null while any rule deletes a queued hatchery
     * this frame, so a cancelled expansion is not re-created on the following frame.
     */
    protected Plan planNewBase(GameState gameState) {
        if (!gameState.mayQueueExpansionHatchery()) {
            return null;
        }

        Base base = gameState.reserveBase();
        if (base == null) {
            return null;
        }

        gameState.addPlannedHatchery(1);
        return new BuildingPlan(UnitType.Zerg_Hatchery, gameState.getGameTime().getFrames(), base.getLocation());
    }

    protected Plan planLair(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        techProgression.setPlannedLair(true);
        return new BuildingPlan(UnitType.Zerg_Lair, 3);
    }

    protected Plan planQueensNest(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        techProgression.setPlannedQueensNest(true);
        Plan plan = new BuildingPlan(UnitType.Zerg_Queens_Nest, 4);
        TilePosition buildPosition = gameState.getTechBuildingLocation(UnitType.Zerg_Queens_Nest);
        plan.setBuildPosition(buildPosition);
        return plan;
    }

    protected Plan planHive(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        techProgression.setPlannedHive(true);
        return new BuildingPlan(UnitType.Zerg_Hive, 3);
    }

    protected Plan planUltraliskCavern(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        techProgression.setPlannedUltraliskCavern(true);
        Plan plan = new BuildingPlan(UnitType.Zerg_Ultralisk_Cavern, 4);
        TilePosition buildPosition = gameState.getTechBuildingLocation(UnitType.Zerg_Ultralisk_Cavern);
        plan.setBuildPosition(buildPosition);
        return plan;
    }

    protected Plan planDefilerMound(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        techProgression.setPlannedDefilerMound(true);
        Plan plan = new BuildingPlan(UnitType.Zerg_Defiler_Mound, 4);
        TilePosition buildPosition = gameState.getTechBuildingLocation(UnitType.Zerg_Defiler_Mound);
        plan.setBuildPosition(buildPosition);
        return plan;
    }

    protected Plan planSpawningPool(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        techProgression.setPlannedSpawningPool(true);
        Plan plan = new BuildingPlan(UnitType.Zerg_Spawning_Pool, gameState.getGameTime());
        TilePosition buildPosition = gameState.getTechBuildingLocation(UnitType.Zerg_Spawning_Pool);
        plan.setBuildPosition(buildPosition);
        return plan;
    }

    protected Plan planSpire(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        techProgression.setPlannedSpire(true);
        Plan plan = new BuildingPlan(UnitType.Zerg_Spire, 4);
        TilePosition buildPosition = gameState.getTechBuildingLocation(UnitType.Zerg_Spire);
        plan.setBuildPosition(buildPosition);
        return plan;
    }

    protected Plan planExtractor(GameState gameState) {
        BaseData baseData = gameState.getBaseData();
        Plan plan = new BuildingPlan(UnitType.Zerg_Extractor, 50);
        Unit geyser = baseData.reserveExtractor();
        plan.setBuildPosition(baseData.getGeyserPosition(geyser));
        return plan;
    }

    /**
     * Returns a set of Creep and Sunken Colony plans.
     */
    protected Set<Plan> planSunkenColony(GameState gameState) {
        return planSunkenColony(gameState, DEFAULT_COLONY_PRIORITY, this.requiredSunkens(gameState));
    }

    protected Set<Plan> planSunkenColony(GameState gameState, int priority, int target) {
        Set<Plan> plans = new HashSet<>();
        BaseData baseData = gameState.getBaseData();
        BuildingPlanner buildingPlanner = gameState.getBuildingPlanner();
        Optional<Base> eligibleBase = gameState.basesNeedingSunken(target).stream().findFirst();
        if (!eligibleBase.isPresent()) {
            return plans;
        }
        TilePosition location = buildingPlanner.getLocationForCreepColony(eligibleBase.get(), gameState.getOpponentRace());
        if (location == null) {
            return plans;
        }
        baseData.reserveSunkenColony(eligibleBase.get());
        buildingPlanner.reservePlannedBuildingTiles(location, UnitType.Zerg_Creep_Colony);
        Plan creepColonyPlan = new BuildingPlan(UnitType.Zerg_Creep_Colony, priority, location);
        Plan sunkenColonyPlan = new BuildingPlan(UnitType.Zerg_Sunken_Colony, priority, location);
        plans.add(creepColonyPlan);
        plans.add(sunkenColonyPlan);
        return plans;
    }

    protected Set<Plan> planSporeColony(GameState gameState) {
        Set<Plan> plans = new HashSet<>();
        BaseData baseData = gameState.getBaseData();
        BuildingPlanner buildingPlanner = gameState.getBuildingPlanner();
        Optional<Base> eligibleBase = gameState.basesNeedingSpore(this.requiredSpores(gameState)).stream().findFirst();
        if (!eligibleBase.isPresent()) {
            return plans;
        }
        TilePosition location = buildingPlanner.getLocationForSporeColony(eligibleBase.get());
        if (location == null) {
            return plans;
        }
        baseData.reserveSporeColony(eligibleBase.get());
        buildingPlanner.reservePlannedBuildingTiles(location, UnitType.Zerg_Creep_Colony);
        Plan creepColonyPlan = new BuildingPlan(UnitType.Zerg_Creep_Colony, 5, location);
        Plan sporeColonyPlan = new BuildingPlan(UnitType.Zerg_Spore_Colony, 5, location);
        plans.add(creepColonyPlan);
        plans.add(sporeColonyPlan);
        return plans;
    }

    protected Plan planUnit(GameState gameState, UnitType unitType) {
        return planUnit(gameState, unitType, gameState.getGameTime().getFrames());
    }

    protected Plan planUnit(GameState gameState, UnitType unitType, int priority) {
        UnitTypeCount count = gameState.getUnitTypeCount();
        count.planUnit(unitType);
        if (unitType == UnitType.Zerg_Drone) {
            gameState.addPlannedWorker(1);
        }
        if (unitType == UnitType.Zerg_Overlord) {
            int plannedSupply = gameState.getResourceCount().getPlannedSupply();
            gameState.getResourceCount().setPlannedSupply(plannedSupply + 16);
        }
        return new UnitPlan(unitType, priority);
    }

    /**
     * Plans a unit that a tech building unlocked, ahead of the backlog. Queues at most one plan
     * per call.
     */
    protected List<Plan> planAdvancedUnit(GameState gameState, UnitType unitType) {
        List<Plan> plans = new ArrayList<>();
        if (gameState.queuedUnitPlanCount(unitType) > 0) {
            return plans;
        }
        plans.add(planUnit(gameState, unitType, UnitPlan.ADVANCED_UNIT_PRIORITY));
        return plans;
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
                techProgression.setPlannedMeleeUpgrades(true);
                break;
            case Zerg_Missile_Attacks:
                techProgression.setPlannedRangedUpgrades(true);
                break;
            case Zerg_Flyer_Attacks:
                techProgression.setPlannedFlyerAttack(true);
                break;
            case Zerg_Flyer_Carapace:
                techProgression.setPlannedFlyerDefense(true);
                break;
            case Zerg_Carapace:
                techProgression.setPlannedCarapaceUpgrades(true);
                break;
            case Pneumatized_Carapace:
                techProgression.setPlannedOverlordSpeed(true);
                priority = 100;
                break;
            case Chitinous_Plating:
                techProgression.setPlannedChitinousPlating(true);
                break;
            case Anabolic_Synthesis:
                techProgression.setPlannedAnabolicSynthesis(true);
                break;
            case Adrenal_Glands:
                techProgression.setPlannedAdrenalGlands(true);
                break;
            default:
                break;
        }

        int currentLevel = getCurrentUpgradeLevel(techProgression, upgradeType);
        return new UpgradePlan(upgradeType, priority, currentLevel);
    }

    private int getCurrentUpgradeLevel(TechProgression tp, UpgradeType type) {
        switch (type) {
            case Zerg_Carapace: return tp.getCarapaceUpgrades();
            case Zerg_Melee_Attacks: return tp.getMeleeUpgrades();
            case Zerg_Missile_Attacks: return tp.getRangedUpgrades();
            case Zerg_Flyer_Attacks: return tp.getFlyerAttack();
            case Zerg_Flyer_Carapace: return tp.getFlyerDefense();
            default: return 0;
        }
    }

    protected Plan planHydraliskDen(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        techProgression.setPlannedDen(true);
        Plan plan = new BuildingPlan(UnitType.Zerg_Hydralisk_Den, gameState.getGameTime().getFrames());
        TilePosition buildPosition = gameState.getTechBuildingLocation(UnitType.Zerg_Hydralisk_Den);
        plan.setBuildPosition(buildPosition);
        return plan;
    }

    protected Plan planEvolutionChamber(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        techProgression.setPlannedEvolutionChambers(techProgression.getPlannedEvolutionChambers() + 1);
        Plan plan = new BuildingPlan(UnitType.Zerg_Evolution_Chamber, gameState.getGameTime().getFrames());
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

        if (techType == TechType.Consume) {
            techProgression.setPlannedConsume(true);
        }

        if (techType == TechType.Plague) {
            techProgression.setPlannedPlague(true);
        }

        return new TechPlan(techType, priority, true);
    }

    protected Plan planMacroHatchery(GameState gameState) {
        BuildingPlanner buildingPlanner = gameState.getBuildingPlanner();
        BaseData baseData = gameState.getBaseData();
        return macroHatcheryAt(gameState, buildingPlanner.getLocationForMacroHatchery(gameState.getOpponentRace(), baseData));
    }

    /**
     * Plans a macro hatchery at an explicitly chosen base rather than the race-keyed rotation.
     */
    protected Plan planMacroHatcheryAt(GameState gameState, Base base) {
        BuildingPlanner buildingPlanner = gameState.getBuildingPlanner();
        return macroHatcheryAt(gameState, buildingPlanner.getLocationForMacroHatchery(base));
    }

    private Plan macroHatcheryAt(GameState gameState, TilePosition location) {
        if (location == null || !gameState.mayQueueMacroHatchery()) {
            return null;
        }

        gameState.getBuildingPlanner().reservePlannedBuildingTiles(location, UnitType.Zerg_Hatchery);
        gameState.addPlannedHatchery(1);
        Plan plan = new BuildingPlan(UnitType.Zerg_Hatchery, gameState.getGameTime().getFrames(), location);
        plan.setMacroHatchery(true);
        return plan;
    }

    /**
     * Reports if we must add a hatchery to keep parity with the enemy resource depot count,
     * whatever the opponent's race.
     *
     * <p>Our total is {@link GameState#hatcheryCount()} plus the hatcheries already queued.
     *
     * @return true when the enemy has more resource depots, our hatcheries are not excess, and
     *     no reaction deletes expansions this frame
     */
    protected boolean behindOnHatchery(GameState gameState) {
        int ourTotal = gameState.hatcheryCount() + Math.max(0, gameState.getPlannedHatcheries());

        int enemyTotal = gameState.enemyResourceDepotCount();

        return HatcheryCapacity.isBehind(ourTotal, enemyTotal, gameState.hasExcessHatchery(),
                gameState.isEarlyRushed());
    }

    protected boolean behindOnBases(GameState gameState) {
        BaseData baseData = gameState.getBaseData();

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
