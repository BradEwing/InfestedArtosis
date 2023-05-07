package info.map;

import java.util.Comparator;

public class MapTileScoutImportanceComparator implements Comparator<MapTile> {
    @Override
    public int compare(MapTile x, MapTile y) {
        if (x.getScoutImportance() > y.getScoutImportance()) {
            return -1;
        } else if (x.getScoutImportance() < y.getScoutImportance()) {
            return 1;
        }
        return 0;
    }
}
