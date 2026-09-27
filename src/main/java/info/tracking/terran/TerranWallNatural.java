package info.tracking.terran;

import bwapi.TilePosition;
import bwem.Base;
import info.map.BaseArea;
import info.map.ChokeWall;
import info.tracking.StrategyDetectionContext;
import util.TileFootprint;

import java.util.Collection;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Detects a Terran wall at the enemy natural, from the {@link TerranWall} footprints: every spot of a wall across a
 * chokepoint of the enemy natural's BWEM Area, other than the one it shares with the main, covered; or a
 * {@link TerranWall} pair with either building in the enemy natural's area, read as FFE reads a Forge wall, unless
 * the pair is one {@link TerranWallMain} reads. The natural's area takes in the ground around its chokepoints, the
 * main's ramp among them, so a wall across the ramp is a main wall and not this one.
 */
public class TerranWallNatural extends TerranBaseStrategy {

    public static final String NAME = "TerranWallNatural";

    private static final int PROXIMITY_TILE_RADIUS = 8;
    private static final int AREA_TILE_RADIUS = 20;

    private TerranWall.Evidence evidence;

    public TerranWallNatural() {
        super(NAME);
    }

    @Override
    public boolean isDetected(StrategyDetectionContext context) {
        BaseArea enemyNatural = context.enemyNaturalArea(PROXIMITY_TILE_RADIUS, AREA_TILE_RADIUS);
        if (enemyNatural == null) {
            return false;
        }
        Collection<TileFootprint> footprints = TerranWall.footprints(context.getTracker());
        if (footprints.isEmpty()) {
            return false;
        }
        evidence = evidence(footprints, context.enemyNaturalChokeWalls(), enemyNatural::contains, mainWall(context));
        return evidence != null;
    }

    @Override
    public String getDetectionLabel() {
        return evidence == null ? getName() : getName() + ":" + evidence;
    }

    /**
     * What the footprints show a natural wall on, sealing first, or null when they show none.
     */
    static TerranWall.Evidence evidence(Collection<TileFootprint> footprints, Collection<ChokeWall> chokeWalls,
                                        Predicate<TilePosition> inNatural,
                                        BiPredicate<TileFootprint, TileFootprint> mainWall) {
        if (TerranWall.isSealed(footprints, chokeWalls)) {
            return TerranWall.Evidence.SEALED;
        }
        boolean pair = TerranWall.hasPair(footprints, (barracks, partner) ->
                TerranWall.eitherStandsAt(barracks, partner, inNatural) && !mainWall.test(barracks, partner));
        return pair ? TerranWall.Evidence.AREA_PAIR : null;
    }

    private static BiPredicate<TileFootprint, TileFootprint> mainWall(StrategyDetectionContext context) {
        Base enemyMain = context.getBaseData().getMainEnemyBase();
        if (enemyMain == null) {
            return (barracks, partner) -> false;
        }
        return TerranWallMain.placement(TerranWallMain.atMainChoke(context), TerranWallMain.onExitPath(context),
                enemyMain.getCenter().toTilePosition(), TerranWallMain.naturalDepotCentre(context));
    }
}
