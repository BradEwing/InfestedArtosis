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
 * build.
 */
public class EarlyRush extends ObservedStrategy {

    private static final Time ARRIVAL_DEADLINE = new Time(4, 30);
    private static final Time SCOUT_DEADLINE = new Time(2, 30);
    private static final Time FAST_POOL_COMPLETED_BY = new Time(1, 52);
    private static final int MIN_ARRIVING_COMBAT_UNITS = 2;
    private static final int MIN_PRODUCTION_BUILDINGS = 2;
    private static final int NATURAL_TILE_RADIUS = 10;

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
        Set<TilePosition> tiles = context.ourBaseTiles(NATURAL_TILE_RADIUS);
        int arrived = context.getTracker().getCountOfVisibleUnitsOnTiles(Filter::isMobileGroundCombatUnit, tiles);
        return arrived >= MIN_ARRIVING_COMBAT_UNITS;
    }

    private boolean rushProductionScouted(StrategyDetectionContext context, Time time) {
        if (time.greaterThan(SCOUT_DEADLINE)) {
            return false;
        }
        ObservedUnitTracker tracker = context.getTracker();
        return fastPoolScouted(tracker) || gaslessProductionScouted(tracker);
    }

    private boolean fastPoolScouted(ObservedUnitTracker tracker) {
        return tracker.getUnitTypeCountCompletedBeforeTime(UnitType.Zerg_Spawning_Pool, FAST_POOL_COMPLETED_BY) > 0;
    }

    private boolean gaslessProductionScouted(ObservedUnitTracker tracker) {
        if (tracker.hasLivingGasBuilding()) {
            return false;
        }
        int productionBuildings = tracker.getCountOfLivingUnits(UnitType.Protoss_Gateway, UnitType.Terran_Barracks);
        return productionBuildings >= MIN_PRODUCTION_BUILDINGS;
    }
}
