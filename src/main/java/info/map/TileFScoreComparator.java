package info.map;

import java.util.Comparator;
import java.util.Map;

/**
 * Used for A* search.
 */
public class TileFScoreComparator implements Comparator<TileInfo> {

    private Map<TileInfo, Integer> fScoreMap;

    public TileFScoreComparator(Map<TileInfo, Integer> fScoreMap) {
        this.fScoreMap = fScoreMap;
    }

    @Override
    public int compare(TileInfo x, TileInfo y) {
        final int fX = fScoreMap.get(x);
        final int fY = fScoreMap.get(y);
        if (fX > fY) {
            return -1;
        } else if (fX < fY) {
            return 1;
        }
        return 0;
    }
}
