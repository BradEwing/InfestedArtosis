package map;

import java.util.Comparator;

public class TileComparator implements Comparator<TileInfo> {
    @Override
    public int compare(TileInfo x, TileInfo y) {
        if (x.getImportance() > y.getImportance()) {
            return -1;
        } else if (x.getImportance() < y.getImportance()) {
            return 1;
        }
        return 0;
    }
}
