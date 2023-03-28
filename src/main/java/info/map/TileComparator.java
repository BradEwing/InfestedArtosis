package info.map;

import java.util.Comparator;

public class TileComparator implements Comparator<TileInfo> {
    @Override
    public int compare(TileInfo x, TileInfo y) {
        if (x.getScoutImportance() > y.getScoutImportance()) {
            return -1;
        } else if (x.getScoutImportance() < y.getScoutImportance()) {
            return 1;
        }
        return 0;
    }
}
