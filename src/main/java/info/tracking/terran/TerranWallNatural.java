package info.tracking.terran;

import bwapi.TilePosition;
import bwem.Base;
import info.map.BaseArea;
import info.tracking.StrategyDetectionContext;
import util.TileFootprint;
import util.Time;

import java.util.Collection;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Detects a Terran wall at the enemy natural: the {@link TerranWall} pair with either building in the enemy
 * natural's area, read as FFE reads a Forge wall, unless the pair is one {@link TerranWallMain} reads. The natural's
 * area takes in the ground around its chokepoints, the main's ramp among them, so a wall across the ramp is a main
 * wall and not this one.
 */
public class TerranWallNatural extends TerranBaseStrategy {

    public static final String NAME = "TerranWallNatural";

    private static final int PROXIMITY_TILE_RADIUS = 8;
    private static final int AREA_TILE_RADIUS = 20;

    public TerranWallNatural() {
        super(NAME);
    }

    @Override
    public boolean isDetected(StrategyDetectionContext context) {
        if (context.getTime().greaterThan(TerranWall.CUTOFF)) {
            return false;
        }
        BaseArea enemyNatural = context.enemyNaturalArea(PROXIMITY_TILE_RADIUS, AREA_TILE_RADIUS);
        if (enemyNatural == null) {
            return false;
        }
        BiPredicate<TileFootprint, TileFootprint> mainWall = mainWall(context);
        return isDetected(context.getTime(), context.getTracker().getLivingFootprints(TerranWall::isWallBuilding),
                enemyNatural::contains, mainWall);
    }

    static boolean isDetected(Time now, Collection<TileFootprint> footprints, Predicate<TilePosition> inNatural,
                              BiPredicate<TileFootprint, TileFootprint> mainWall) {
        return TerranWall.isDetected(now, footprints, (barracks, partner) ->
                TerranWall.eitherStandsAt(barracks, partner, inNatural) && !mainWall.test(barracks, partner));
    }

    private static BiPredicate<TileFootprint, TileFootprint> mainWall(StrategyDetectionContext context) {
        Base enemyMain = context.getBaseData().getMainEnemyBase();
        Predicate<TilePosition> atMainChoke = context.enemyMainChokeArea(TerranWallMain.CHOKE_TILE_RADIUS);
        if (enemyMain == null || atMainChoke == null) {
            return (barracks, partner) -> false;
        }
        return TerranWallMain.placement(atMainChoke, enemyMain.getCenter().toTilePosition());
    }
}
