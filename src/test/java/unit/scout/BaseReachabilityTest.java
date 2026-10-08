package unit.scout;

import bwapi.TilePosition;
import bwapi.UnitType;
import info.map.GameMap;
import info.map.MapTile;
import info.map.MapTileType;
import org.junit.jupiter.api.Test;
import util.TileFootprint;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseReachabilityTest {

    private static final TilePosition SOURCE = new TilePosition(2, 10);
    private static final TilePosition BASE = new TilePosition(18, 10);

    @Test
    void blockedTilesCoverEveryTileOfEachFootprint() {
        Set<TilePosition> tiles = BaseReachability.blockedTiles(
                Collections.singletonList(footprint(UnitType.Terran_Bunker, 10, 10)));

        assertEquals(6, tiles.size());
        assertTrue(tiles.contains(new TilePosition(12, 11)));
        assertFalse(tiles.contains(new TilePosition(13, 11)));
    }

    @Test
    void aBaseBehindAGapIsReachableWhileTheGapIsOpen() {
        GameMap map = gapMap();

        int[][] distances = map.groundStepDistances(SOURCE, tile -> false);

        assertFalse(BaseReachability.isWalledOff(distances, distances, SOURCE, BASE));
    }

    @Test
    void buildingsFillingTheGapWallTheBaseOff() {
        GameMap map = gapMap();
        List<TileFootprint> wall = Arrays.asList(
                footprint(UnitType.Terran_Bunker, 9, 10),
                footprint(UnitType.Terran_Supply_Depot, 10, 12));
        Set<TilePosition> blocked = BaseReachability.blockedTiles(wall);

        int[][] distances = map.groundStepDistances(SOURCE, blocked::contains);

        assertTrue(BaseReachability.isWalledOff(open(map), distances, SOURCE, BASE));
    }

    @Test
    void aWallLeavingOneTileOpenDoesNotWallTheBaseOff() {
        GameMap map = gapMap();
        Set<TilePosition> blocked = BaseReachability.blockedTiles(
                Collections.singletonList(footprint(UnitType.Terran_Supply_Depot, 10, 10)));

        int[][] distances = map.groundStepDistances(SOURCE, blocked::contains);

        assertFalse(BaseReachability.isWalledOff(open(map), distances, SOURCE, BASE));
    }

    @Test
    void aWalkThatNeverStartedIsNotAWall() {
        GameMap map = gapMap();
        TilePosition inRock = new TilePosition(10, 2);

        int[][] distances = map.groundStepDistances(inRock, tile -> false);

        assertFalse(BaseReachability.isWalledOff(distances, distances, inRock, BASE));
    }

    @Test
    void aBaseTerrainAloneCutsOffIsNotWalled() {
        GameMap map = gapMap();
        Set<TilePosition> blocked = BaseReachability.blockedTiles(Collections.singletonList(
                footprint(UnitType.Terran_Supply_Depot, 10, 12)));
        int[][] open = map.groundStepDistances(SOURCE, tile -> tile.getX() == 10);
        int[][] withBuildings = map.groundStepDistances(SOURCE, blocked::contains);

        assertFalse(BaseReachability.isWalledOff(open, withBuildings, SOURCE, BASE));
    }

    @Test
    void aBaseOffTheEdgeOfTheDistancesIsNotAWall() {
        int[][] distances = new int[4][4];
        for (int[] column : distances) {
            Arrays.fill(column, -1);
        }
        distances[0][0] = 0;

        assertFalse(BaseReachability.isWalledOff(distances, distances, new TilePosition(0, 0), new TilePosition(40, 40)));
    }

    @Test
    void aScoutBesideReachedGroundIsOnOurSideOfTheWall() {
        int[][] distances = new int[8][8];
        for (int[] column : distances) {
            Arrays.fill(column, -1);
        }
        distances[3][3] = 4;

        assertTrue(BaseReachability.reachesNear(distances, new TilePosition(4, 4)));
        assertFalse(BaseReachability.reachesNear(distances, new TilePosition(6, 6)));
    }

    private static int[][] open(GameMap map) {
        return map.groundStepDistances(SOURCE, tile -> false);
    }

    private static GameMap gapMap() {
        GameMap map = new GameMap(24, 24);
        for (int x = 0; x < 24; x++) {
            for (int y = 0; y < 24; y++) {
                boolean rock = x == 10 && !(y >= 10 && y <= 12) || y < 4 && x >= 9 && x <= 11;
                map.addTile(new MapTile(new TilePosition(x, y), 0, true, !rock, MapTileType.NORMAL), x, y);
            }
        }
        return map;
    }

    private static TileFootprint footprint(UnitType type, int left, int top) {
        return new TileFootprint(type, new TilePosition(left, top));
    }
}
