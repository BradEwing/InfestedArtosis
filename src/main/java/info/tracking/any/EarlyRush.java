package info.tracking.any;

import bwapi.TilePosition;
import bwapi.UnitType;
import info.tracking.ObservedStrategy;
import info.tracking.ObservedUnitTracker;
import info.tracking.StrategyDetectionContext;
import util.Filter;
import util.Time;

import java.util.Set;

/**
 * Race-agnostic early rush detector. Fires on arrival of enemy combat units at our
 * main or natural before the rush window closes, or on a scouted rush-oriented
 * production setup with no gas. Arrival-based detection is immune to scouting failure
 * and works while the opponent race is still Unknown.
 */
public class EarlyRush extends ObservedStrategy {

    private static final Time ARRIVAL_DEADLINE = new Time(4, 30);
    private static final Time SCOUT_DEADLINE = new Time(2, 30);
    private static final int MIN_ARRIVING_COMBAT_UNITS = 2;
    private static final int MIN_PRODUCTION_BUILDINGS = 2;

    public EarlyRush() {
        super("EarlyRush");
    }

    @Override
    public boolean isDetected(StrategyDetectionContext context) {
        Time time = context.getTime();
        if (time.greaterThan(ARRIVAL_DEADLINE)) {
            return false;
        }
        return combatUnitsArrived(context) || rushProductionScouted(context, time);
    }

    private boolean combatUnitsArrived(StrategyDetectionContext context) {
        Set<TilePosition> tiles = ourBaseTiles(context);
        if (tiles.isEmpty()) {
            return false;
        }
        int arrived = context.getTracker().getCountOfLivingUnitsOnTiles(Filter::isMobileGroundCombatUnit, tiles);
        return arrived >= MIN_ARRIVING_COMBAT_UNITS;
    }

    private boolean rushProductionScouted(StrategyDetectionContext context, Time time) {
        if (time.greaterThan(SCOUT_DEADLINE)) {
            return false;
        }
        ObservedUnitTracker tracker = context.getTracker();
        if (tracker.hasLivingGasBuilding()) {
            return false;
        }
        int productionBuildings = tracker.getUnitTypeCountBeforeTime(UnitType.Protoss_Gateway, SCOUT_DEADLINE)
                + tracker.getUnitTypeCountBeforeTime(UnitType.Terran_Barracks, SCOUT_DEADLINE);
        if (productionBuildings >= MIN_PRODUCTION_BUILDINGS) {
            return true;
        }
        return tracker.getUnitTypeCountBeforeTime(UnitType.Zerg_Spawning_Pool, SCOUT_DEADLINE) > 0;
    }
}
