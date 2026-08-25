package info.tracking.any;

import bwapi.TilePosition;
import bwapi.UnitType;
import bwem.Base;
import info.BaseData;
import info.tracking.ObservedStrategy;
import info.tracking.ObservedUnitTracker;
import info.tracking.StrategyDetectionContext;
import util.Filter;
import util.Time;

import java.util.HashSet;
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
        Set<TilePosition> tiles = defendedTiles(context);
        if (tiles.isEmpty()) {
            return false;
        }
        int arrived = context.getTracker().getCountOfLivingUnitsOnTiles(EarlyRush::isMobileGroundCombatUnit, tiles);
        return arrived >= MIN_ARRIVING_COMBAT_UNITS;
    }

    private boolean rushProductionScouted(StrategyDetectionContext context, Time time) {
        if (time.greaterThan(SCOUT_DEADLINE)) {
            return false;
        }
        ObservedUnitTracker tracker = context.getTracker();
        if (hasGasBuilding(tracker)) {
            return false;
        }
        int productionBuildings = tracker.getUnitTypeCountBeforeTime(UnitType.Protoss_Gateway, SCOUT_DEADLINE)
                + tracker.getUnitTypeCountBeforeTime(UnitType.Terran_Barracks, SCOUT_DEADLINE);
        if (productionBuildings >= MIN_PRODUCTION_BUILDINGS) {
            return true;
        }
        return tracker.getUnitTypeCountBeforeTime(UnitType.Zerg_Spawning_Pool, SCOUT_DEADLINE) > 0;
    }

    private static boolean hasGasBuilding(ObservedUnitTracker tracker) {
        return tracker.getCountOfLivingUnits(UnitType.Protoss_Assimilator, UnitType.Terran_Refinery, UnitType.Zerg_Extractor) > 0;
    }

    private static Set<TilePosition> defendedTiles(StrategyDetectionContext context) {
        Set<TilePosition> tiles = new HashSet<>(context.getGameMap().getMainBaseTiles());
        BaseData baseData = context.getBaseData();
        Base natural = baseData.getInferredNaturalBase();
        if (natural != null) {
            tiles.addAll(tilesWithinManhattanDistance(natural.getLocation(), NATURAL_TILE_RADIUS));
        }
        return tiles;
    }

    private static Set<TilePosition> tilesWithinManhattanDistance(TilePosition center, int radius) {
        Set<TilePosition> tiles = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            int remaining = radius - Math.abs(dx);
            for (int dy = -remaining; dy <= remaining; dy++) {
                tiles.add(new TilePosition(center.getX() + dx, center.getY() + dy));
            }
        }
        return tiles;
    }

    private static boolean isMobileGroundCombatUnit(UnitType type) {
        return !type.isBuilding() && !type.isFlyer() && !Filter.isWorkerType(type) && Filter.isGroundThreat(type);
    }
}
