package info.map;

import java.util.Comparator;

/**
 * Orders tiles by scout importance, highest first; among equals a start location never seen comes first.
 */
public class MapTileScoutImportanceComparator implements Comparator<MapTile> {
    @Override
    public int compare(MapTile x, MapTile y) {
        if (x.getScoutImportance() > y.getScoutImportance()) {
            return -1;
        } else if (x.getScoutImportance() < y.getScoutImportance()) {
            return 1;
        }
        return Boolean.compare(isUnscoutedStart(y), isUnscoutedStart(x));
    }

    private static boolean isUnscoutedStart(MapTile tile) {
        return tile.getType() == MapTileType.BASE_START && !tile.isScouted();
    }
}
