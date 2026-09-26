package info.tracking.terran;

import bwapi.Race;
import bwapi.TilePosition;
import bwapi.UnitType;
import info.BaseData;
import info.tracking.ObservedUnitTracker;
import util.TileFootprint;
import util.Time;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * The rule the Terran wall detectors share: while the clock is within {@link #CUTOFF}, a living observed Barracks
 * and a wall partner stand with at most {@link #MAX_TILE_GAP} tiles between their footprints, at their current or
 * last known positions. A wall partner is a Supply Depot or a Terran building of BaseData's natural wall types.
 * Each detector adds where the pair must stand.
 */
public final class TerranWall {

    /**
     * Walls go up with the first Barracks. GrimHammer's walled Barracks, Depot and Bunker were all seen by 4:00 in
     * most games, so 6:00 leaves room for a late scout while keeping a Barracks and Depot built side by side for
     * later tech out of the reading.
     */
    static final Time CUTOFF = new Time(6, 0);

    /**
     * Footprints that touch, or have a single row or column of tiles between them.
     */
    static final int MAX_TILE_GAP = 1;

    private TerranWall() {
    }

    /**
     * Whether now is within {@link #CUTOFF} and some Barracks and wall partner among the footprints are within
     * {@link #MAX_TILE_GAP} of each other at a placement the detector accepts. The placement receives the
     * Barracks first.
     */
    static boolean isDetected(Time now, Collection<TileFootprint> footprints,
                              BiPredicate<TileFootprint, TileFootprint> placement) {
        if (now.greaterThan(CUTOFF)) {
            return false;
        }
        return ObservedUnitTracker.hasPairWithinTileGap(footprints, TerranWall::isBarracks, TerranWall::isWallPartner,
                MAX_TILE_GAP, placement);
    }

    static boolean isWallBuilding(UnitType type) {
        return isBarracks(type) || isWallPartner(type);
    }

    /**
     * Whether the centre tile of either footprint passes the tile test.
     */
    static boolean eitherStandsAt(TileFootprint barracks, TileFootprint partner, Predicate<TilePosition> tileTest) {
        return tileTest.test(barracks.centreTile()) || tileTest.test(partner.centreTile());
    }

    /**
     * Whether the detected strategies, joined by ';' as the learning file records them, name either wall.
     */
    public static boolean isWallIn(String detectedStrategies) {
        if (detectedStrategies == null || detectedStrategies.isEmpty()) {
            return false;
        }
        return Arrays.stream(detectedStrategies.split(";"))
                .anyMatch(name -> name.equals(TerranWallNatural.NAME) || name.equals(TerranWallMain.NAME));
    }

    private static boolean isBarracks(UnitType type) {
        return type == UnitType.Terran_Barracks;
    }

    private static boolean isWallPartner(UnitType type) {
        return type == UnitType.Terran_Supply_Depot
                || type.getRace() == Race.Terran && BaseData.isNaturalWallType(type);
    }
}
