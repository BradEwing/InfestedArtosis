package info;

import bwapi.Game;
import bwapi.Player;
import bwapi.Race;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwem.BWEM;
import bwem.Base;
import bwem.Mineral;
import config.Config;
import info.map.GameMap;
import info.tracking.ObservedUnitTracker;
import info.tracking.StrategyTracker;
import learning.Decisions;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import macro.plan.Plan;
import macro.plan.PlanState;
import macro.plan.PlanType;
import strategy.openers.Opener;
import strategy.strategies.Strategy;
import strategy.strategies.UnitWeights;
import unit.managed.ManagedUnit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

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

    private Race opponentRace; // TODO: Set from InfoManager if opponent is Random

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

    private HashMap<Base, HashSet<ManagedUnit>> gatherersAssignedToBase = new HashMap<>();

    private HashMap<Base, HashSet<Unit>> baseToThreatLookup = new HashMap<>();

    private Opener activeOpener;
    @Setter(AccessLevel.NONE)
    private Strategy activeStrategy;
    private UnitWeights unitWeights;
    private boolean defensiveSunk = false;

    private UnitTypeCount unitTypeCount = new UnitTypeCount();

    private TechProgression techProgression = new TechProgression();

    private ResourceCount resourceCount;

    private BaseData baseData;
    private ScoutData scoutData;
    private ObservedUnitTracker observedUnitTracker = new ObservedUnitTracker();
    private StrategyTracker strategyTracker;

    // Initialized in InformationManager
    private GameMap gameMap;

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
        Opener opener = decisions.getOpener();
        this.activeOpener = opener;
        this.isAllIn = opener.isAllIn();
        this.activeStrategy = decisions.getStrategy();
        this.unitWeights = activeStrategy.getUnitWeights();

        if (config.learnDefensiveSunk) {
            this.defensiveSunk = decisions.isDefensiveSunk();
        }

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
        return needLair() && techProgression.canPlanLair() && hasMinHatchForLair();
    }

    public boolean canPlanHive() {
        return needHive() && techProgression.canPlanHive();
    }

    public boolean canPlanQueensNest() {
        return needHive() && techProgression.canPlanQueensNest();
    }

    public boolean canPlanHydraliskDen() {
        final boolean atLeastTwoHatch = baseData.numHatcheries() > 1;
        return atLeastTwoHatch && techProgression.canPlanHydraliskDen() && unitWeights.hasUnit(UnitType.Zerg_Hydralisk);
    }

    private boolean needLair() {
        final boolean unitsNeedLairTech = unitWeights.hasUnit(UnitType.Zerg_Mutalisk) ||
                unitWeights.hasUnit(UnitType.Zerg_Scourge) ||
                unitWeights.hasUnit(UnitType.Zerg_Lurker);
        return unitsNeedLairTech || techProgression.needLairForNextEvolutionChamberUpgrades() || needHive();
    }

    private boolean needHive() {
        final boolean unitsNeedHiveTech = unitWeights.hasUnit(UnitType.Zerg_Ultralisk) || unitWeights.hasUnit(UnitType.Zerg_Defiler);
        return unitsNeedHiveTech || techProgression.needHiveForUpgrades();
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
}
