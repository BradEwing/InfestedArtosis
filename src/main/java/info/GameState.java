package info;

import bwapi.Unit;
import lombok.Data;
import planner.PlannedItem;

import java.util.HashMap;
import java.util.HashSet;

/**
 * Class to handle global state that is shared among various managers.
 */
@Data
public class GameState {

    private boolean enemyHasCloakedUnits = false;
    private boolean enemyHasHostileFlyers = false;

    private HashSet<PlannedItem> plansNew = new HashSet<>();
    private HashSet<PlannedItem> plansBuilding = new HashSet<>();
    private HashSet<PlannedItem> plansMorphing = new HashSet<>();
    private HashMap<Unit, PlannedItem> assignedPlannedItems = new HashMap<>();

    public GameState() {

    }
}
