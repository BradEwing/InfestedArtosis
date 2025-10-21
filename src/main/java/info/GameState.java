package info;

import bwapi.Game;
import bwapi.Player;
import bwapi.Position;
import bwapi.Race;
import bwapi.TechType;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.UpgradeType;
import bwem.BWEM;
import bwem.Base;
import bwem.Mineral;
import config.Config;
import info.map.BuildingPlanner;
import info.map.GameMap;
import info.tracking.ObservedUnitTracker;
import info.tracking.StrategyTracker;
import learning.Decisions;
import lombok.Data;
import macro.plan.Plan;
import macro.plan.PlanState;
import macro.plan.PlanType;
import strategy.buildorder.BuildOrder;
import unit.managed.ManagedUnit;
import util.Time;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * GameState tracks global state that is shared among agents and managers.
 */
@Data
public class GameState {
    private Game game;
    private Config config;
    private Player self;
    private BWEM bwem;

    private Race opponentRace;

    private int mineralWorkers;
    private int geyserWorkers;
    private int larvaDeadlockDetectedFrame;

    private HashMap<Unit, ManagedUnit> managedUnitLookup = new HashMap<>();
    private HashSet<ManagedUnit> managedUnits = new HashSet<>();
    private HashSet<ManagedUnit> assignedManagedWorkers = new HashSet<>();
    private HashSet<ManagedUnit> gatherers = new HashSet<>();
    private HashSet<ManagedUnit> mineralGatherers = new HashSet<>();
    private HashSet<ManagedUnit> gasGatherers = new HashSet<>();

    private HashMap<Unit, HashSet<ManagedUnit>> geyserAssignments = new HashMap<>();
    private HashMap<Unit, HashSet<ManagedUnit>> mineralAssignments = new HashMap<>();

    private HashSet<ManagedUnit> larva = new HashSet<>();

    private boolean enemyHasCloakedUnits = false;
    private boolean enemyHasHostileFlyers = false;
    private boolean isLarvaDeadlocked = false;
    private boolean isAllIn = false;

    // TODO: refactor into common data structure, address access throughout bot
    private HashSet<Plan> plansScheduled = new HashSet<>();
    private HashSet<Plan> plansBuilding = new HashSet<>();
    private HashSet<Plan> plansMorphing = new HashSet<>();
    private HashSet<Plan> plansComplete = new HashSet<>();
    private HashSet<Plan> plansImpossible = new HashSet<>(); // If ProductionManager determines impossible, cancel them in WorkerManager
    private HashMap<Unit, Plan> assignedPlannedItems = new HashMap<>();
    private int plannedWorkers;
    private int plannedHatcheries = 1; // Start with 1 because we decrement with initial hatch

    private HashMap<Base, HashSet<ManagedUnit>> gatherersAssignedToBase = new HashMap<>();

    private HashMap<Base, HashSet<Unit>> baseToThreatLookup = new HashMap<>();

    private boolean defensiveSunk = false;
    private BuildOrder activeBuildOrder;

    private boolean transitionBuildOrder = false;

    private UnitTypeCount unitTypeCount = new UnitTypeCount();

    private TechProgression techProgression = new TechProgression();

    private ResourceCount resourceCount;

    private BaseData baseData;
    private ScoutData scoutData;
    private ObservedUnitTracker observedUnitTracker = new ObservedUnitTracker();
    private StrategyTracker strategyTracker;

    // Initialized in InformationManager
    private GameMap gameMap;
    private BuildingPlanner buildingPlanner;

    public GameState(Game game, BWEM bwem) {
        this.game = game;
        this.self = game.self();
        this.bwem = bwem;
        this.resourceCount = new ResourceCount(self);
        this.baseData = new BaseData(bwem.getMap().getBases());
        this.scoutData = new ScoutData();
        this.config = new Config();
    }

    public void onStart(Decisions decisions, Race opponentRace) {
        this.activeBuildOrder = decisions.getOpener();
        this.opponentRace = opponentRace;
        this.strategyTracker = new StrategyTracker(game, opponentRace, this.observedUnitTracker);
    }

    public void onFrame() {
        strategyTracker.onFrame();
        clearVisibleEnemyWorkerLocations();
    }

    private void clearVisibleEnemyWorkerLocations() {
        Set<Position> lastKnownWorkerPositions = observedUnitTracker.getLastKnownPositionsOfLivingUnits(
            UnitType.Terran_SCV,
            UnitType.Protoss_Probe,
            UnitType.Zerg_Drone
        );

        Set<Position> visibleWorkerPositions = lastKnownWorkerPositions.stream()
            .filter(p -> p != null)
            .filter(p -> game.isVisible(p.toTilePosition()))
            .collect(Collectors.toSet());

        if (!visibleWorkerPositions.isEmpty()) {
            observedUnitTracker.clearLastKnownLocationsAt(visibleWorkerPositions);
        }
    }

    public int numGatherers() {
        return gatherers.size();
    }

    public int numLarva() { return larva.size(); }

    public int frameCanAffordUnit(UnitType unit, int currentFrame) {
        return this.resourceCount.frameCanAffordUnit(unit, currentFrame, mineralGatherers.size(), gasGatherers.size());
    }

    public Base reserveBase() {
        return baseData.reserveBase();
    }

    public void claimBase(Unit hatchery) {
        if (this.baseData.isBase(hatchery)) {
            return;
        }
        final Base newBase = baseData.claimBase(hatchery);
        addBaseToGameState(hatchery, newBase);
    }

    public void addBaseToGameState(Unit hatchery, Base base) {
        if (base == null) { return; }
        gatherersAssignedToBase.put(base, new HashSet<>());
        this.baseData.addBase(hatchery, base);

        for (Mineral mineral: base.getMinerals()) {
            mineralAssignments.put(mineral.getUnit(), new HashSet<>());
        }
    }

    public void addMainBase(Unit hatchery, Base base) {
        this.baseData.initializeMainBase(base, this.gameMap);
        addBaseToGameState(hatchery, base);
    }

    public void addMacroHatchery(Unit hatchery) {
        this.baseData.addMacroHatchery(hatchery);
    }

    public void removeHatchery(Unit hatchery) {
        if (this.baseData.isBase(hatchery)) {
            Base base = this.baseData.get(hatchery);
            gatherersAssignedToBase.remove(base);
            baseToThreatLookup.remove(base);
        }
        this.baseData.removeHatchery(hatchery);
    }

    public void cancelPlan(Unit unit, Plan plan) {
        plansBuilding.remove(plan);
        plansMorphing.remove(plan);
        plansImpossible.remove(plan);
        plan.setState(PlanState.CANCELLED);
        assignedPlannedItems.remove(unit);

        switch (plan.getType()) {
            case UNIT:
                unitTypeCount.unplanUnit(plan.getPlannedUnit());
                resourceCount.unreserveUnit(plan.getPlannedUnit());
                break;
            case BUILDING:
                UnitType buildingType = plan.getPlannedUnit();
                unitTypeCount.unplanUnit(buildingType);
                resourceCount.unreserveUnit(buildingType);

                if (plan.getBuildPosition() != null) {
                    buildingPlanner.unreservePlannedBuildingTiles(plan.getBuildPosition(), buildingType);
                }

                TilePosition tp = plan.getBuildPosition();
                if (tp != null && baseData.isBaseTilePosition(tp)) {
                    Base base = baseData.baseAtTilePosition(tp);
                    baseData.cancelReserveBase(base);
                }
                
                clearPlannedTechFlags(buildingType);
                break;
            case UPGRADE:
                resourceCount.unreserveUpgrade(plan.getPlannedUpgrade());
                clearPlannedUpgradeFlags(plan.getPlannedUpgrade());
                break;
            case TECH:
                resourceCount.unreserveTechResearch(plan.getPlannedTechType());
                clearPlannedTechResearchFlags(plan.getPlannedTechType());
                break;
        }
    }

    private void clearPlannedTechFlags(UnitType buildingType) {
        switch (buildingType) {
            case Zerg_Spawning_Pool:
                techProgression.setPlannedSpawningPool(false);
                break;
            case Zerg_Hydralisk_Den:
                techProgression.setPlannedDen(false);
                break;
            case Zerg_Lair:
                techProgression.setPlannedLair(false);
                break;
            case Zerg_Spire:
                techProgression.setPlannedSpire(false);
                break;
            case Zerg_Queens_Nest:
                techProgression.setPlannedQueensNest(false);
                break;
            case Zerg_Hive:
                techProgression.setPlannedHive(false);
                break;
            case Zerg_Evolution_Chamber:
                techProgression.setPlannedEvolutionChambers(techProgression.getPlannedEvolutionChambers() - 1);
                break;
        }
    }

    private void clearPlannedUpgradeFlags(UpgradeType upgradeType) {
        switch (upgradeType) {
            case Metabolic_Boost:
                techProgression.setPlannedMetabolicBoost(false);
                break;
            case Muscular_Augments:
                techProgression.setPlannedMuscularAugments(false);
                break;
            case Grooved_Spines:
                techProgression.setPlannedGroovedSpines(false);
                break;
            case Zerg_Carapace:
                techProgression.setPlannedCarapaceUpgrades(false);
                break;
            case Zerg_Missile_Attacks:
                techProgression.setPlannedRangedUpgrades(false);
                break;
            case Zerg_Melee_Attacks:
                techProgression.setPlannedMeleeUpgrades(false);
                break;
            case Zerg_Flyer_Attacks:
                techProgression.setPlannedFlyerAttack(false);
                break;
            case Zerg_Flyer_Carapace:
                techProgression.setPlannedFlyerDefense(false);
                break;
            case Pneumatized_Carapace:
                techProgression.setPlannedOverlordSpeed(false);
                break;
        }
    }

    private void clearPlannedTechResearchFlags(TechType techType) {
        switch (techType) {
            case Lurker_Aspect:
                techProgression.setPlannedLurker(false);
                break;
        }
    }

    public void completePlan(Unit unit, Plan plan) {
        plansBuilding.remove(plan);
        plansMorphing.remove(plan);
        plan.setState(PlanState.COMPLETE);
        plansComplete.add(plan);
        assignedPlannedItems.remove(unit);
    }

    public void setImpossiblePlan(Plan plan) {
        plansImpossible.add(plan);
        
        // Unplan the unit when marking plan as impossible
        switch (plan.getType()) {
            case UNIT:
                unitTypeCount.unplanUnit(plan.getPlannedUnit());
                resourceCount.unreserveUnit(plan.getPlannedUnit());
                break;
            case BUILDING:
                unitTypeCount.unplanUnit(plan.getPlannedUnit());
                resourceCount.unreserveUnit(plan.getPlannedUnit());
                
                UnitType buildingType = plan.getPlannedUnit();
                if (buildingType == UnitType.Zerg_Hatchery) {
                    removePlannedHatchery(1);
                }
                break;
        }
    }

    /**
     * Checks tech progression, build order and base data to determine if a lair can be planned.
     *
     * @return boolean
     */
    public boolean canPlanLair() {
        return needLair() && techProgression.canPlanLair() && hasMinHatchForLair() && ourUnitCount(UnitType.Zerg_Extractor) > 0;
    }

    public boolean canPlanHive() {
        return needHive() && techProgression.canPlanHive();
    }

    public boolean canPlanQueensNest() {
        return needHive() && techProgression.canPlanQueensNest();
    }

    private boolean needLair() {
        return activeBuildOrder.needLair() || techProgression.needLairForNextEvolutionChamberUpgrades() || needHive();
    }

    private boolean needHive() {
        return techProgression.needHiveForUpgrades();
    }

    // Only take 1 hatch -> lair against zerg
    private boolean hasMinHatchForLair() {
        final int numHatch = baseData.numHatcheries();

        if (opponentRace == Race.Zerg) {
            return numHatch > 0;
        } else {
            return numHatch > 1;
        }
    }

    public boolean needGeyserWorkers() { return this.getGeyserWorkers() < (3 * this.getGeyserAssignments().size()); }

    public int needGeyserWorkersAmount() { return (3 * this.getGeyserAssignments().size()) - this.getGeyserWorkers(); }

    /**
     * Removes managed unit from all data structures.
     *
     * This is typically done before assigning it to a new role. This code was
     * initially lifted from WorkerManager.
     * @param managedUnit to wipe clean
     */
    public void clearAssignments(ManagedUnit managedUnit) {
        if (assignedManagedWorkers.contains(managedUnit)) {
            for (HashSet<ManagedUnit> mineralWorkers: mineralAssignments.values()) {
                if (mineralWorkers.contains(managedUnit)) {
                    this.mineralWorkers -= 1;
                    mineralWorkers.remove(managedUnit);
                }
            }
            for (HashSet<ManagedUnit> geyserWorkers: geyserAssignments.values()) {
                if (geyserWorkers.contains(managedUnit)) {
                    this.geyserWorkers -= 1;
                    geyserWorkers.remove(managedUnit);
                }
            }
        }

        larva.remove(managedUnit);
        gatherers.remove(managedUnit);
        mineralGatherers.remove(managedUnit);
        gasGatherers.remove(managedUnit);
        assignedManagedWorkers.remove(managedUnit);

        for (HashSet<ManagedUnit> managedUnitAssignments: gatherersAssignedToBase.values()) {
            managedUnitAssignments.remove(managedUnit);
        }
    }

    public List<ManagedUnit> getManagedUnitsByType(UnitType type) {
        return managedUnits.stream()
                .filter(m -> m.getUnitType() == type)
                .collect(Collectors.toList());
    }

    public void updateRace(Race race) {
        opponentRace = race;
        strategyTracker.updateRace(race);
    }

    public Time getGameTime() {
        return new Time(game.getFrameCount());
    }

    public void addPlannedWorker(int numWorkers) {
        plannedWorkers += numWorkers;
    }

    public void removePlannedWorker(int numWorkers) {
        plannedWorkers -= numWorkers;
    }

    public void addPlannedHatchery(int numHatcheries) {
        plannedHatcheries += numHatcheries;
    }

    public void removePlannedHatchery(int numHatcheries) {
        plannedHatcheries -= numHatcheries;
    }

    public boolean canPlanDrone() {
        final int expectedWorkers = expectedWorkers();
        int hatchCount = ourUnitCount(UnitType.Zerg_Hatchery, UnitType.Zerg_Lair, UnitType.Zerg_Hive);
        int plannedWorkerConstraint = hatchCount * 3;
        return plannedWorkers < plannedWorkerConstraint && numWorkers() < 80 && numWorkers() < expectedWorkers;
    }

    public int numWorkers() {
        return mineralWorkers + geyserWorkers;
    }

    // How many workers we want.
    // This is pulled from the old ProductionManager. Ideally this is something set more so
    // by the active strategies.
    private int expectedWorkers() {
        final int base = 5;
        final int expectedMineralWorkers = baseData.currentBaseCount() * 7;
        final int expectedGasWorkers = geyserAssignments.size() * 3;

        Race race = opponentRace;
        switch (race) {
            case Zerg:
                return expectedMineralWorkers + expectedGasWorkers;
            default:
                return base + expectedMineralWorkers + expectedGasWorkers;
        }
    }

    public boolean canPlanExtractor() {
        return !isAllIn &&
                techProgression.canPlanExtractor() &&
                baseData.canReserveExtractor() &&
                (baseData.numExtractor() < 1 || needExtractor());
    }

    private boolean needExtractor() {
        return baseData.numExtractor() < 1 || resourceCount.needExtractor();
    }

    public int ourUnitCount(UnitType unitType) {
        return unitTypeCount.get(unitType);
    }

    public int ourMorphingCount(UnitType unitType) {
        return (int) plansMorphing.stream()
            .filter(plan -> plan.getPlannedUnit() == unitType)
            .count();
    }

    public int ourLivingUnitCount(UnitType unitType) {
        return unitTypeCount.livingCount(unitType);
    }

    public int ourUnitCount(UnitType... unitTypes) {
        int i = 0;
        for (UnitType unitType: unitTypes) {
            i += ourUnitCount(unitType);
        }
        return i;
    }

    public int enemyUnitCount(UnitType unitType) {
        return observedUnitTracker.getCountOfLivingUnits(unitType);
    }

    public int getSupply() {
        return game.self().supplyUsed();
    }

    public TilePosition getTechBuildingLocation(UnitType unitType) {
        Base main = baseData.getMainBase();
        TilePosition position = buildingPlanner.getLocationForTechBuilding(main, unitType);
        buildingPlanner.reservePlannedBuildingTiles(position, unitType);
        return position;
    }

    public Set<Base> basesNeedingSunken(int target) {
        Set<Base> neededBases = new HashSet<>();
        for (Base base: baseData.getMyBases()) {
            if (baseData.isEligibleForSunkenColony(base) && baseData.sunkensPerBase(base) < target) {
                neededBases.add(base);
            }
        }

        return neededBases;
    }

    public boolean canPlanUnit(UnitType unitType) {
        switch(unitType) {
            case Zerg_Zergling:
                return techProgression.isSpawningPool();
            default:
                return false;
        }
    }

    public boolean canPlanUpgrade(UpgradeType upgradeType) {
        switch (upgradeType) {
            case Metabolic_Boost:
                return ourUnitCount(UnitType.Zerg_Extractor) > 0 && techProgression.isSpawningPool() && techProgression.canPlanMetabolicBoost();
            default:
                return false;
        }
    }

    public Set<Position> getAerielStaticDefenseCoverage() {
        Set<Position> coveredPositions = new HashSet<>();

        Map<UnitType, Integer> staticDefenseRanges = new HashMap<>();
        switch (opponentRace) {
            case Terran:
                staticDefenseRanges.put(UnitType.Terran_Missile_Turret, UnitType.Terran_Missile_Turret.airWeapon().maxRange() + 32);
                staticDefenseRanges.put(UnitType.Terran_Bunker, UnitType.Terran_Marine.groundWeapon().maxRange() + 64);
                break;
            case Protoss:
                staticDefenseRanges.put(UnitType.Protoss_Photon_Cannon, UnitType.Protoss_Photon_Cannon.groundWeapon().maxRange() + 32);
                break;
            case Zerg:
                staticDefenseRanges.put(UnitType.Zerg_Spore_Colony, UnitType.Zerg_Spore_Colony.airWeapon().maxRange() + 32);
                break;
            default:
                return coveredPositions;
        }

        for (Map.Entry<UnitType, Integer> entry : staticDefenseRanges.entrySet()) {
            UnitType defenseType = entry.getKey();
            int range = entry.getValue();

            Set<Position> defensePositions = observedUnitTracker.getLastKnownPositionsOfLivingUnits(defenseType);

            for (Position defensePos : defensePositions) {
                if (defensePos != null) {
                    for (int x = defensePos.getX() - range; x <= defensePos.getX() + range; x += 8) {
                        for (int y = defensePos.getY() - range; y <= defensePos.getY() + range; y += 8) {
                            Position testPos = new Position(x, y);

                            double distance = Math.sqrt(Math.pow(x - defensePos.getX(), 2) + Math.pow(y - defensePos.getY(), 2));
                            if (distance <= range) {
                                coveredPositions.add(testPos);
                            }
                        }
                    }
                }
            }
        }

        return coveredPositions;
    }

    /**
     * Gets all positions that are within range of enemy static defense structures.
     *
     * @return Set of positions that are covered by static defense
     */
    public Set<Position> getStaticDefenseCoverage() {
        Set<Position> coveredPositions = new HashSet<>();

        Map<UnitType, Integer> staticDefenseRanges = new HashMap<>();
        switch (opponentRace) {
            case Terran:
                staticDefenseRanges.put(UnitType.Terran_Missile_Turret, UnitType.Terran_Missile_Turret.airWeapon().maxRange());
                staticDefenseRanges.put(UnitType.Terran_Bunker, UnitType.Terran_Marine.groundWeapon().maxRange() + 32);
                break;
            case Protoss:
                staticDefenseRanges.put(UnitType.Protoss_Photon_Cannon, UnitType.Protoss_Photon_Cannon.groundWeapon().maxRange());
                break;
            case Zerg:
                staticDefenseRanges.put(UnitType.Zerg_Spore_Colony, UnitType.Zerg_Spore_Colony.airWeapon().maxRange());
                staticDefenseRanges.put(UnitType.Zerg_Sunken_Colony, UnitType.Zerg_Sunken_Colony.groundWeapon().maxRange());
                break;
            default:
                return coveredPositions;
        }

        for (Map.Entry<UnitType, Integer> entry : staticDefenseRanges.entrySet()) {
            UnitType defenseType = entry.getKey();
            int range = entry.getValue();

            Set<Position> defensePositions = observedUnitTracker.getLastKnownPositionsOfLivingUnits(defenseType);

            for (Position defensePos : defensePositions) {
                if (defensePos != null) {
                    for (int x = defensePos.getX() - range; x <= defensePos.getX() + range; x += 8) {
                        for (int y = defensePos.getY() - range; y <= defensePos.getY() + range; y += 8) {
                            Position testPos = new Position(x, y);

                            double distance = Math.sqrt(Math.pow(x - defensePos.getX(), 2) + Math.pow(y - defensePos.getY(), 2));
                            if (distance <= range) {
                                coveredPositions.add(testPos);
                            }
                        }
                    }
                }
            }
        }

        return coveredPositions;
    }

    public Set<Position> getLastKnownLocationOfEnemyWorkers() {
        switch (opponentRace) {
            case Terran:
                return observedUnitTracker.getLastKnownPositionsOfLivingUnits(UnitType.Terran_SCV);
            case Zerg:
                return observedUnitTracker.getLastKnownPositionsOfLivingUnits(UnitType.Zerg_Drone);
            case Protoss:
                return observedUnitTracker.getLastKnownPositionsOfLivingUnits(UnitType.Protoss_Probe);
            default:
                return new HashSet<>();
        }
    }

    /**
     * Gets all visible enemy units for squad combat evaluation.
     * @return HashSet of visible enemy units
     */
    public Set<Unit> getDetectedEnemyUnits() {
        return observedUnitTracker.getDetectedUnits();
    }

    /**
     * Gets all known enemy buildings for combat evaluation.
     * @return HashSet of enemy buildings
     */
    public Set<Unit> getEnemyBuildings() {
        return observedUnitTracker.getBuilding();
    }

    /**
     * Gets the rally point for squads that need to regroup.
     * @return Position for rally point
     */
    public Position getSquadRallyPoint() {
        if (baseData.hasNaturalExpansion()) {
            return baseData.naturalExpansionPosition().toPosition();
        } else {
            return baseData.mainBasePosition().toPosition();
        }
    }

    public int enemyResourceDepotCount() {
        switch (opponentRace) {
            case Terran:
                return enemyUnitCount(UnitType.Terran_Command_Center);

            case Protoss:
                return enemyUnitCount(UnitType.Protoss_Nexus);

            case Zerg:
                return enemyUnitCount(UnitType.Zerg_Hatchery) +
                        enemyUnitCount(UnitType.Zerg_Lair) +
                        enemyUnitCount(UnitType.Zerg_Hive);
            default:
                return 0;
        }
    }
}
