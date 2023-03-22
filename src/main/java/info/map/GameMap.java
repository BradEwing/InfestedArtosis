package info.map;

import java.util.ArrayList;

/**
 * Bundles up information about the game map.
 */
public class GameMap {
    private ArrayList<TileInfo> heatMap = new ArrayList<>();

    private TileInfo[][] mapTiles;

    public GameMap(int x, int y) {
        mapTiles = new TileInfo[x][y];
    }

    public void addTile(TileInfo tile, int x, int y) {
        mapTiles[x][y] = tile;
        heatMap.add(tile);
    }

    public ArrayList<TileInfo> getHeapMap() {
        return heatMap;
    }
}
