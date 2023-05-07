package info.map;

import java.util.Comparator;
import java.util.Map;

/**
 * Used for A* search.
 */
public class MapTileFScoreComparator implements Comparator<MapTile> {

    private Map<MapTile, Integer> fScoreMap;

    public MapTileFScoreComparator(Map<MapTile, Integer> fScoreMap) {
        this.fScoreMap = fScoreMap;
    }

    @Override
    public int compare(MapTile x, MapTile y) {
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
