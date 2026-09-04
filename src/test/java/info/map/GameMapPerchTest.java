package info.map;

import bwapi.TilePosition;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Perch seeding on a strip map: mainland in columns 0-2, water in columns 3-6, an island in
 * columns 7-9. Only ground a unit can walk to from a starting location counts as ground, so the
 * island and the water beside it measure their distance from the mainland shore.
 */
class GameMapPerchTest {

    private static final int WIDTH = 10;
    private static final int HEIGHT = 5;

    private GameMap stripMap() {
        GameMap gameMap = new GameMap(WIDTH, HEIGHT);
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                boolean land = x <= 2 || x >= 7;
                MapTile tile = new MapTile(new TilePosition(x, y), 0, land, land, MapTileType.NORMAL);
                tile.setGroundOccupiable(land);
                gameMap.addTile(tile, x, y);
            }
        }
        return gameMap;
    }

    @Test
    void islandDoesNotCountAsGroundWhenOnlyTheMainlandHasAStart() {
        GameMap gameMap = stripMap();

        gameMap.computePerches(3, Collections.singletonList(new TilePosition(0, 0)));

        assertEquals(0, gameMap.get(2, 2).getGroundDistance());
        assertEquals(1, gameMap.get(3, 2).getGroundDistance());
        assertEquals(4, gameMap.get(6, 2).getGroundDistance());
        assertEquals(7, gameMap.get(9, 2).getGroundDistance());
        assertTrue(gameMap.getPerchTiles().contains(gameMap.get(5, 2)));
        assertTrue(gameMap.getPerchTiles().contains(gameMap.get(8, 2)));
        assertFalse(gameMap.getPerchTiles().contains(gameMap.get(4, 2)));
        assertFalse(gameMap.getPerchTiles().contains(gameMap.get(1, 2)));
    }

    @Test
    void islandCountsAsGroundWhenAStartIsOnIt() {
        GameMap gameMap = stripMap();

        gameMap.computePerches(3, Arrays.asList(new TilePosition(0, 0), new TilePosition(9, 0)));

        assertEquals(0, gameMap.get(8, 2).getGroundDistance());
        assertEquals(2, gameMap.get(5, 2).getGroundDistance());
        assertTrue(gameMap.getPerchTiles().isEmpty());
    }

    @Test
    void partiallyWalkableShoreTilesBorderingTheMainlandCountAsGround() {
        GameMap gameMap = stripMap();
        gameMap.get(3, 2).setWalkable(false);
        gameMap.get(3, 2).setGroundOccupiable(true);

        gameMap.computePerches(3, Collections.singletonList(new TilePosition(0, 0)));

        assertEquals(0, gameMap.get(3, 2).getGroundDistance());
        assertEquals(1, gameMap.get(4, 2).getGroundDistance());
    }
}
