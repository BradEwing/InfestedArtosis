package info.map;

import bwapi.TilePosition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoutHeatMapTest {

    private static MapTile tile(int x, MapTileType type, int importance, boolean scouted) {
        MapTile tile = new MapTile(new TilePosition(x, 0), importance, true, true, type);
        tile.setScouted(scouted);
        return tile;
    }

    private static GameMap mapOf(MapTile... tiles) {
        GameMap map = new GameMap(tiles.length, 1);
        for (int i = 0; i < tiles.length; i++) {
            map.addTile(tiles[i], i, 0);
        }
        return map;
    }

    @Test
    void anUnscoutedStartLocationGainsMoreThanAScoutedOne() {
        assertTrue(GameMap.scoutWeight(MapTileType.BASE_START, false)
                > GameMap.scoutWeight(MapTileType.BASE_START, true));
    }

    @Test
    void aScoutedStartLocationStillOutweighsAnExpansionAndAnExpansionOutweighsANormalTile() {
        assertTrue(GameMap.scoutWeight(MapTileType.BASE_START, true)
                > GameMap.scoutWeight(MapTileType.BASE_EXPANSION, true));
        assertTrue(GameMap.scoutWeight(MapTileType.BASE_EXPANSION, false)
                > GameMap.scoutWeight(MapTileType.NORMAL, false));
    }

    @Test
    void scoutedStatusDoesNotChangeExpansionOrNormalWeights() {
        assertEquals(GameMap.scoutWeight(MapTileType.BASE_EXPANSION, false),
                GameMap.scoutWeight(MapTileType.BASE_EXPANSION, true));
        assertEquals(GameMap.scoutWeight(MapTileType.NORMAL, false),
                GameMap.scoutWeight(MapTileType.NORMAL, true));
    }

    @Test
    void ageingPutsAnUnscoutedStartLocationAheadOfAScoutedOneAtEqualImportance() {
        MapTile scouted = tile(0, MapTileType.BASE_START, 0, true);
        MapTile unscouted = tile(1, MapTileType.BASE_START, 0, false);
        MapTile expansion = tile(2, MapTileType.BASE_EXPANSION, 0, false);
        GameMap map = mapOf(scouted, expansion, unscouted);
        map.ageHeatMap();
        assertSame(unscouted, map.getHeatMap().get(0));
        assertSame(scouted, map.getHeatMap().get(1));
        assertSame(expansion, map.getHeatMap().get(2));
    }

    @Test
    void anUnscoutedStartLocationOvertakesAScoutedOneUnseenForTheSameFrames() {
        MapTile scouted = tile(0, MapTileType.BASE_START, 0, true);
        MapTile unscouted = tile(1, MapTileType.BASE_START, 0, false);
        GameMap map = mapOf(scouted, unscouted);
        for (int frame = 0; frame < 100; frame++) {
            map.ageHeatMap();
        }
        assertSame(unscouted, map.getHeatMap().get(0));
        assertTrue(unscouted.getScoutImportance() > scouted.getScoutImportance());
    }

    @Test
    void theComparatorBreaksAnImportanceTieInFavourOfTheUnscoutedStart() {
        MapTile scouted = tile(0, MapTileType.BASE_START, 10, true);
        MapTile unscouted = tile(1, MapTileType.BASE_START, 10, false);
        List<MapTile> tiles = new ArrayList<>();
        tiles.add(scouted);
        tiles.add(unscouted);
        Collections.sort(tiles, new MapTileScoutImportanceComparator());
        assertSame(unscouted, tiles.get(0));
    }

    @Test
    void theComparatorStillOrdersByImportanceFirst() {
        MapTile hot = tile(0, MapTileType.NORMAL, 50, true);
        MapTile unscouted = tile(1, MapTileType.BASE_START, 10, false);
        List<MapTile> tiles = new ArrayList<>();
        tiles.add(unscouted);
        tiles.add(hot);
        Collections.sort(tiles, new MapTileScoutImportanceComparator());
        assertSame(hot, tiles.get(0));
    }
}
