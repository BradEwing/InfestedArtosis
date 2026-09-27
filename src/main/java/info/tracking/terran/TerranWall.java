package info.tracking.terran;

import bwapi.Race;
import bwapi.TilePosition;
import bwapi.UnitType;
import info.BaseData;
import info.map.ChokeWall;
import info.tracking.ObservedUnitTracker;
import util.TileFootprint;
import util.Time;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * What the Terran wall detectors share. They read the footprints of Terran buildings first observed within
 * {@link #CUTOFF} and still grounded where they were first seen, so a lifted or relocated building is never part of
 * a wall, and a wall seen early is still read once the enemy main or natural is found later. A wall is either every
 * spot of a {@link ChokeWall} covered, or a Barracks and a wall partner with at most {@link #MAX_TILE_GAP} tiles
 * between their footprints where the detector accepts the pair. A wall partner is a Supply Depot or a Terran
 * building of BaseData's natural wall types.
 */
public final class TerranWall {

    /**
     * Walls go up with the first Barracks. GrimHammer's walled Barracks, Depot and Bunker were all seen by 4:00 in
     * most games, so a building first observed by 6:00 leaves room for a late scout while keeping a Barracks and
     * Depot built side by side for later tech out of the reading.
     */
    static final Time CUTOFF = new Time(6, 0);

    /**
     * Footprints that touch, or have a single row or column of tiles between them.
     */
    static final int MAX_TILE_GAP = 1;

    /**
     * How many of the most recent games the next-game bar looks back over.
     */
    public static final int RECENT_GAMES = 3;

    /**
     * How many of the {@link #RECENT_GAMES} must have detected a wall for it to count as the opponent's habit. A
     * GrimHammer wall recurs in the next game about 4 times in 5, while insanitybot's Barracks and Bunker pairs
     * recur about as often as they appear at all.
     */
    static final int PERSISTENT_WALL_GAMES = 2;

    /**
     * What a detector read the wall from, recorded in its detection label.
     */
    enum Evidence {
        SEALED,
        CHOKE_PAIR,
        EXIT_PAIR,
        AREA_PAIR
    }

    private TerranWall() {
    }

    /**
     * Footprints of the Terran buildings first observed within {@link #CUTOFF} that still stand where first seen.
     */
    static List<TileFootprint> footprints(ObservedUnitTracker tracker) {
        return tracker.getGroundedFootprints(TerranWall::isTerranBuilding, CUTOFF);
    }

    /**
     * Whether the footprints cover every spot of one of the choke walls.
     */
    static boolean isSealed(Collection<TileFootprint> footprints, Collection<ChokeWall> chokeWalls) {
        return chokeWalls.stream().anyMatch(chokeWall -> chokeWall.isSealedBy(footprints));
    }

    /**
     * Whether some Barracks and wall partner among the footprints are within {@link #MAX_TILE_GAP} of each other
     * at a placement the detector accepts. The placement receives the Barracks first.
     */
    static boolean hasPair(Collection<TileFootprint> footprints, BiPredicate<TileFootprint, TileFootprint> placement) {
        return ObservedUnitTracker.hasPairWithinTileGap(footprints, TerranWall::isBarracks, TerranWall::isWallPartner,
                MAX_TILE_GAP, placement);
    }

    static boolean isTerranBuilding(UnitType type) {
        return type.isBuilding() && type.getRace() == Race.Terran;
    }

    /**
     * Whether the centre tile of either footprint passes the tile test.
     */
    static boolean eitherStandsAt(TileFootprint barracks, TileFootprint partner, Predicate<TilePosition> tileTest) {
        return tileTest.test(barracks.centreTile()) || tileTest.test(partner.centreTile());
    }

    /**
     * Whether any tile of either footprint passes the tile test.
     */
    static boolean eitherTouches(TileFootprint barracks, TileFootprint partner, Predicate<TilePosition> tileTest) {
        return barracks.anyTile(tileTest) || partner.anyTile(tileTest);
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

    /**
     * Whether a wall was detected in at least {@link #PERSISTENT_WALL_GAMES} of the last {@link #RECENT_GAMES}
     * games, given each game's detected strategies oldest first.
     */
    public static boolean isPersistent(List<String> gamesDetectedStrategies) {
        long walledGames = gamesDetectedStrategies
                .subList(Math.max(0, gamesDetectedStrategies.size() - RECENT_GAMES), gamesDetectedStrategies.size())
                .stream()
                .filter(TerranWall::isWallIn)
                .count();
        return walledGames >= PERSISTENT_WALL_GAMES;
    }

    private static boolean isBarracks(UnitType type) {
        return type == UnitType.Terran_Barracks;
    }

    private static boolean isWallPartner(UnitType type) {
        return type == UnitType.Terran_Supply_Depot
                || type.getRace() == Race.Terran && BaseData.isNaturalWallType(type);
    }
}
