package info;

import bwapi.Player;
import bwapi.Unit;
import bwapi.UnitType;
import bwem.Base;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import planner.PlannedItem;
import strategy.openers.Opener;
import strategy.strategies.Strategy;
import strategy.strategies.UnitWeights;
import unit.managed.ManagedUnit;

import java.util.HashMap;
import java.util.HashSet;

/**
 * Class to handle global state that is shared among various managers.
 *
 * Break this into subclasses if too big or need util functions around subsets.
 */
@Data
public class GameState {
    private Player self;

    private int mineralWorkers;
    private int geyserWorkers;
    private int plannedSupply;
    private int larvaDeadlockDetectedFrame;

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

    private HashSet<PlannedItem> plansScheduled = new HashSet<>();
    private HashSet<PlannedItem> plansBuilding = new HashSet<>();
    private HashSet<PlannedItem> plansMorphing = new HashSet<>();
    private HashSet<PlannedItem> plansComplete = new HashSet<>();
    private HashMap<Unit, PlannedItem> assignedPlannedItems = new HashMap<>();

    private HashMap<Base, HashSet<ManagedUnit>> gatherersAssignedToBase = new HashMap<>();

    private HashMap<Base, HashSet<Unit>> baseToThreatLookup = new HashMap<>();

    private Opener activeOpener;
    @Setter(AccessLevel.NONE)
    private Strategy activeStrategy;
    private UnitWeights unitWeights;

    private UnitTypeCount unitTypeCount = new UnitTypeCount();

    private TechProgression techProgression = new TechProgression();

    private ResourceCount resourceCount;

    public GameState(Player self) {
        this.self = self;
        this.resourceCount = new ResourceCount(self);
    }

    public void setActiveStrategy(Strategy activeStrategy) {
        this.activeStrategy = activeStrategy;
        this.unitWeights = activeStrategy.getUnitWeights();
    }

    public int numGatherers() {
        return gatherers.size();
    }

    public int numLarva() { return larva.size(); }

    public int frameCanAffordUnit(UnitType unit, int currentFrame) {
        return this.resourceCount.frameCanAffordUnit(unit, currentFrame, mineralGatherers.size(), gasGatherers.size());
    }
}
