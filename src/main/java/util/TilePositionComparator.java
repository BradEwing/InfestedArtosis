package util;

import bwapi.TilePosition;

import java.util.Comparator;

public class TilePositionComparator implements Comparator<TilePosition> {

    private TilePosition target;
    public TilePositionComparator(TilePosition target) {
        this.target = target;
    }

    @Override
    public int compare(TilePosition x, TilePosition y) {
        double distanceX = target.getDistance(x);
        double distanceY = target.getDistance(y);
        if (distanceX < distanceY) {
            return -1;
        }
        if (distanceX > distanceY) {
            return 1;
        }
        return 0;
    }
}
