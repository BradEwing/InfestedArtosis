package info.tracking.terran;

import bwapi.TilePosition;
import bwem.Base;
import info.tracking.StrategyDetectionContext;
import util.Distance;
import util.TileFootprint;
import util.Time;

import java.util.Collection;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Detects a Terran wall across the enemy main's ramp or entrance: the {@link TerranWall} pair with either building
 * within {@link #CHOKE_TILE_RADIUS} of a chokepoint of the enemy main's BWEM Area, and the Barracks at least
 * {@link #MIN_BARRACKS_TILES_FROM_DEPOT} from the centre of the enemy main's depot.
 */
public class TerranWallMain extends TerranBaseStrategy {

    public static final String NAME = "TerranWallMain";

    /**
     * Matches the chokepoint window FFE's natural area uses.
     */
    static final int CHOKE_TILE_RADIUS = 8;

    /**
     * Manhattan tiles. An unwalled GrimHammer Barracks stood 4-6 tiles in a straight line from its main Command
     * Center, at most about 9 manhattan, and a walled one 13-36, so a Barracks and Depot built beside the depot are
     * a production block, not a wall.
     */
    static final int MIN_BARRACKS_TILES_FROM_DEPOT = 10;

    public TerranWallMain() {
        super(NAME);
    }

    @Override
    public boolean isDetected(StrategyDetectionContext context) {
        if (context.getTime().greaterThan(TerranWall.CUTOFF)) {
            return false;
        }
        Base enemyMain = context.getBaseData().getMainEnemyBase();
        Predicate<TilePosition> atMainChoke = context.enemyMainChokeArea(CHOKE_TILE_RADIUS);
        if (enemyMain == null || atMainChoke == null) {
            return false;
        }
        return isDetected(context.getTime(), context.getTracker().getLivingFootprints(TerranWall::isWallBuilding),
                atMainChoke, enemyMain.getCenter().toTilePosition());
    }

    static boolean isDetected(Time now, Collection<TileFootprint> footprints, Predicate<TilePosition> atMainChoke,
                              TilePosition mainDepotCentre) {
        return TerranWall.isDetected(now, footprints, placement(atMainChoke, mainDepotCentre));
    }

    /**
     * Whether a Barracks and partner stand where this detector reads a main wall.
     */
    static BiPredicate<TileFootprint, TileFootprint> placement(Predicate<TilePosition> atMainChoke,
                                                               TilePosition mainDepotCentre) {
        return (barracks, partner) -> TerranWall.eitherStandsAt(barracks, partner, atMainChoke)
                && Distance.manhattanTileDistance(barracks.centreTile(), mainDepotCentre) >= MIN_BARRACKS_TILES_FROM_DEPOT;
    }
}
