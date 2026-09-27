package info.map;

import bwapi.TilePosition;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import util.TileFootprint;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A 24 x 24 map of buildable ground split by a cliff along x = 10, crossed by a ramp: walkable, non-buildable tiles
 * at x = 10 for y 8-11. The high ground is x > 10.
 */
class ChokeWallTest {

    private static final Predicate<TilePosition> HIGH_GROUND = tile -> tile.getX() > 10;

    private static final List<TilePosition> RAMP_CHOKE = Arrays.asList(new TilePosition(10, 9),
            new TilePosition(10, 10));

    @Test
    void theSpotsAreTheHighGroundTilesAlongTheRamp() {
        ChokeWall wall = ChokeWall.across(rampMap(), RAMP_CHOKE, HIGH_GROUND);

        assertEquals(tiles(11, 8, 11, 9, 11, 10, 11, 11), wall.getSpots());
    }

    @Test
    void aBarracksAndDepotCoveringEverySpotSealTheRamp() {
        ChokeWall wall = ChokeWall.across(rampMap(), RAMP_CHOKE, HIGH_GROUND);

        assertTrue(wall.isSealedBy(Arrays.asList(footprint(UnitType.Terran_Barracks, 11, 8),
                footprint(UnitType.Terran_Supply_Depot, 11, 11))));
    }

    @Test
    void aSpotLeftOpenIsNoWall() {
        ChokeWall wall = ChokeWall.across(rampMap(), RAMP_CHOKE, HIGH_GROUND);

        assertFalse(wall.isSealedBy(Collections.singletonList(footprint(UnitType.Terran_Barracks, 11, 8))));
        assertFalse(wall.isSealedBy(Arrays.asList(footprint(UnitType.Terran_Barracks, 11, 8),
                footprint(UnitType.Terran_Supply_Depot, 12, 11))));
    }

    @Test
    void buildingsAtTheFootOfTheRampDoNotSealIt() {
        ChokeWall wall = ChokeWall.across(rampMap(), RAMP_CHOKE, HIGH_GROUND);

        assertFalse(wall.isSealedBy(Arrays.asList(footprint(UnitType.Terran_Barracks, 6, 8),
                footprint(UnitType.Terran_Supply_Depot, 7, 11))));
    }

    @Test
    void theRampIsFollowedFromTheChokeTilesItTouches() {
        ChokeWall wall = ChokeWall.across(rampMap(), Collections.singletonList(new TilePosition(10, 8)), HIGH_GROUND);

        assertEquals(tiles(11, 8, 11, 9, 11, 10, 11, 11), wall.getSpots());
    }

    @Test
    void unbuildableGroundBeyondTheGapRadiusIsNotPartOfTheRamp() {
        GameMap map = flatMap();
        for (int y = 0; y <= ChokeWall.GAP_TILE_RADIUS + 1; y++) {
            map.addTile(tile(10, y, false, true), 10, y);
        }

        ChokeWall wall = ChokeWall.across(map, Collections.singletonList(new TilePosition(10, 0)), HIGH_GROUND);

        assertTrue(wall.getSpots().contains(new TilePosition(11, ChokeWall.GAP_TILE_RADIUS)));
        assertFalse(wall.getSpots().contains(new TilePosition(11, ChokeWall.GAP_TILE_RADIUS + 1)));
    }

    @Test
    void aChokeOverBuildableGroundOnlyIsNeverSealed() {
        List<TilePosition> flatChoke = Arrays.asList(new TilePosition(5, 5), new TilePosition(6, 5),
                new TilePosition(7, 5));

        ChokeWall wall = ChokeWall.across(flatMap(), flatChoke, tile -> true);

        assertTrue(wall.getSpots().isEmpty());
        assertFalse(wall.isSealedBy(Collections.singletonList(footprint(UnitType.Terran_Barracks, 4, 4))));
    }

    @Test
    void aChokeWithNoSpotsIsNeverSealed() {
        ChokeWall wall = ChokeWall.across(rampMap(), RAMP_CHOKE, tile -> false);

        assertTrue(wall.getSpots().isEmpty());
        assertFalse(wall.isSealedBy(Collections.singletonList(footprint(UnitType.Terran_Barracks, 11, 8))));
    }

    private static GameMap rampMap() {
        GameMap map = flatMap();
        for (int y = 0; y < 24; y++) {
            boolean ramp = y >= 8 && y <= 11;
            map.addTile(tile(10, y, false, ramp), 10, y);
        }
        return map;
    }

    private static GameMap flatMap() {
        GameMap map = new GameMap(24, 24);
        for (int x = 0; x < 24; x++) {
            for (int y = 0; y < 24; y++) {
                map.addTile(tile(x, y, true, true), x, y);
            }
        }
        return map;
    }

    private static MapTile tile(int x, int y, boolean buildable, boolean walkable) {
        return new MapTile(new TilePosition(x, y), 0, buildable, walkable, MapTileType.NORMAL);
    }

    private static TileFootprint footprint(UnitType type, int left, int top) {
        return new TileFootprint(type, new TilePosition(left, top));
    }

    private static Set<TilePosition> tiles(int... coordinates) {
        Set<TilePosition> tiles = new HashSet<>();
        for (int i = 0; i < coordinates.length; i += 2) {
            tiles.add(new TilePosition(coordinates[i], coordinates[i + 1]));
        }
        return tiles;
    }
}
