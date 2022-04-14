package state;

import bwapi.Unit;
import lombok.Data;
import planner.PlannedItem;
import unit.ManagedUnit;

import java.util.HashMap;
import java.util.HashSet;

/**
 * Class to handle global state that is shared among various managers.
 *
 * Break this into subclasses if too big or need util functions around subsets.
 */
@Data
public class GameState {
    private int mineralWorkers;
    private int geyserWorkers;

    private HashMap<Unit, HashSet<ManagedUnit>> geyserAssignments = new HashMap<>();
    private HashMap<Unit, HashSet<ManagedUnit>> mineralAssignments = new HashMap<>();

    private boolean enemyHasCloakedUnits = false;
    private boolean enemyHasHostileFlyers = false;

    private HashSet<PlannedItem> plansNew = new HashSet<>();
    private HashSet<PlannedItem> plansScheduled = new HashSet<>();
    private HashSet<PlannedItem> plansBuilding = new HashSet<>();
    private HashSet<PlannedItem> plansMorphing = new HashSet<>();
    private HashSet<PlannedItem> plansComplete = new HashSet<>();
    private HashMap<Unit, PlannedItem> assignedPlannedItems = new HashMap<>();

    public GameState() {

    }
}
