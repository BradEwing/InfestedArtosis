package util;

import bwapi.Unit;

import java.util.Comparator;

/**
 * UnitDistanceComparator sorts a list of units by distance to the target unit
 */
public class UnitDistanceComparator implements Comparator<Unit> {

    private Unit target;

    public UnitDistanceComparator(Unit target) {
        this.target = target;
    }

    @Override
    public int compare(Unit x, Unit y) {
        int distanceX = target.getDistance(x);
        int distanceY = target.getDistance(y);
        if (distanceX < distanceY) {
            return -1;
        }
        if (distanceX > distanceY) {
            return 1;
        }
        return 0;
    }
}
