package info.tracking.terran;

import bwapi.TilePosition;
import bwem.Base;
import info.map.ChokeWall;
import info.tracking.StrategyDetectionContext;
import util.Distance;
import util.TileFootprint;

import java.util.Collection;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Detects a Terran wall across the way out of the enemy main, from the {@link TerranWall} footprints: every spot of
 * a wall across a chokepoint of the enemy main's BWEM Area covered, or a {@link TerranWall} pair whose Barracks
 * stands at least {@link #MIN_BARRACKS_TILES_FROM_DEPOT} from the centre of the enemy main's depot with either
 * building within {@link #CHOKE_TILE_RADIUS} of such a chokepoint or touching the ground within
 * {@link #EXIT_PATH_TILE_RADIUS} of the ground path from the enemy main to its natural.
 */
public class TerranWallMain extends TerranBaseStrategy {

    public static final String NAME = "TerranWallMain";

    /**
     * Matches the chokepoint window FFE's natural area uses.
     */
    static final int CHOKE_TILE_RADIUS = 8;

    /**
     * Manhattan tiles from the path out of the main to the nearest tile of a wall building. On NeoMoonGlaive's top
     * start the wall stands in the plateau's entrance, far from any chokepoint of the main's Area, and its Depot's
     * nearest tile is 2 from the path.
     */
    static final int EXIT_PATH_TILE_RADIUS = 2;

    /**
     * Manhattan tiles. An unwalled GrimHammer Barracks stood 4-6 tiles in a straight line from its main Command
     * Center, at most about 9 manhattan, and a walled one 13-36, so a Barracks and Depot built beside the depot are
     * a production block, not a wall.
     */
    static final int MIN_BARRACKS_TILES_FROM_DEPOT = 10;

    private TerranWall.Evidence evidence;

    public TerranWallMain() {
        super(NAME);
    }

    @Override
    public boolean isDetected(StrategyDetectionContext context) {
        Base enemyMain = context.getBaseData().getMainEnemyBase();
        if (enemyMain == null) {
            return false;
        }
        Collection<TileFootprint> footprints = TerranWall.footprints(context.getTracker());
        if (footprints.isEmpty()) {
            return false;
        }
        evidence = evidence(footprints, context.enemyMainChokeWalls(), atMainChoke(context),
                context.enemyMainExitPath(EXIT_PATH_TILE_RADIUS)::contains, enemyMain.getCenter().toTilePosition());
        return evidence != null;
    }

    @Override
    public String getDetectionLabel() {
        return evidence == null ? getName() : getName() + ":" + evidence;
    }

    /**
     * What the footprints show a main wall on, sealing first, or null when they show none.
     */
    static TerranWall.Evidence evidence(Collection<TileFootprint> footprints, Collection<ChokeWall> chokeWalls,
                                        Predicate<TilePosition> atMainChoke, Predicate<TilePosition> onExitPath,
                                        TilePosition mainDepotCentre) {
        if (TerranWall.isSealed(footprints, chokeWalls)) {
            return TerranWall.Evidence.SEALED;
        }
        if (TerranWall.hasPair(footprints, chokePlacement(atMainChoke, mainDepotCentre))) {
            return TerranWall.Evidence.CHOKE_PAIR;
        }
        if (TerranWall.hasPair(footprints, exitPlacement(onExitPath, mainDepotCentre))) {
            return TerranWall.Evidence.EXIT_PAIR;
        }
        return null;
    }

    /**
     * Whether a Barracks and partner stand where this detector reads a main wall pair.
     */
    static BiPredicate<TileFootprint, TileFootprint> placement(Predicate<TilePosition> atMainChoke,
                                                               Predicate<TilePosition> onExitPath,
                                                               TilePosition mainDepotCentre) {
        return chokePlacement(atMainChoke, mainDepotCentre).or(exitPlacement(onExitPath, mainDepotCentre));
    }

    static Predicate<TilePosition> atMainChoke(StrategyDetectionContext context) {
        Predicate<TilePosition> atMainChoke = context.enemyMainChokeArea(CHOKE_TILE_RADIUS);
        return atMainChoke == null ? tile -> false : atMainChoke;
    }

    private static BiPredicate<TileFootprint, TileFootprint> chokePlacement(Predicate<TilePosition> atMainChoke,
                                                                            TilePosition mainDepotCentre) {
        return (barracks, partner) -> isOutOfTheProductionBlock(barracks, mainDepotCentre)
                && TerranWall.eitherStandsAt(barracks, partner, atMainChoke);
    }

    private static BiPredicate<TileFootprint, TileFootprint> exitPlacement(Predicate<TilePosition> onExitPath,
                                                                           TilePosition mainDepotCentre) {
        return (barracks, partner) -> isOutOfTheProductionBlock(barracks, mainDepotCentre)
                && TerranWall.eitherTouches(barracks, partner, onExitPath);
    }

    private static boolean isOutOfTheProductionBlock(TileFootprint barracks, TilePosition mainDepotCentre) {
        return Distance.manhattanTileDistance(barracks.centreTile(), mainDepotCentre) >= MIN_BARRACKS_TILES_FROM_DEPOT;
    }
}
