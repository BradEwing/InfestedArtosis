package info;

import bwapi.Game;
import bwapi.Player;
import bwapi.Race;
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
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.Math.min;

/**
 * Class to handle global state that is shared among various managers.
 *
 * Break this into subclasses if too big or need util functions around subsets.
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
    @Deprecated
    private int macroHatchMod = 0; // Used for 9HatchInBase Opener

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

    // TODO: Lookup base from reserved
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

    // TODO: Refactor base lookups into baseData?
    // TODO: Reassign gatherers
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

        if (plan.getType() == PlanType.BUILDING) {
            UnitType type = plan.getPlannedUnit();
            resourceCount.unreserveUnit(type);

            TilePosition tp = plan.getBuildPosition();
            if (tp != null && baseData.isBaseTilePosition(tp)) {
                Base base = baseData.baseAtTilePosition(tp);
                baseData.cancelReserveBase(base);
            }
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
    }

    /**
     * Checks tech progression, strategy and base data to determine if a lair can be planned.
     *
     * // TODO: Determine more reactively:
     *     - Need speed overlords for detection or scouting
     *     - Need hive
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
        return plannedWorkers < 3 && numWorkers() < 80 && numWorkers() < expectedWorkers;
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
                return min(expectedMineralWorkers, 7) + expectedGasWorkers;
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

    // Determine by current strategy and reactions
    @Deprecated
    public boolean canPlanEvolutionChamber() {
        if (!techProgression.canPlanEvolutionChamber()) {
            return false;
        }

        final int numEvolutionChambers = techProgression.evolutionChambers();
        final int groundCount = unitTypeCount.groundCount();
        if (numEvolutionChambers == 0) {
            return groundCount > 24;
        } else {
            return groundCount > 48;
        }
    }

    public int ourUnitCount(UnitType unitType) {
        return unitTypeCount.get(unitType);
    }

    public int enemyUnitCount(UnitType unitType) {
        return observedUnitTracker.getCountOfLivingUnits(unitType);
    }

    public int getSupply() {
        return game.self().supplyUsed();
    }

    public TilePosition getTechBuildingLocation(UnitType unitType) {
        Base main = baseData.getMainBase();
        return buildingPlanner.getLocationForTechBuilding(main, unitType);
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
}
