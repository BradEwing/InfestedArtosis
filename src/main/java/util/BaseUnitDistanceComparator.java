package util;

import bwapi.Unit;
import bwem.Base;

import java.util.Comparator;

/**
 * BaseUnitDistanceComparator sorts a list of units by distance to the target unit
 */
public class BaseUnitDistanceComparator implements Comparator<Base> {

    private Unit target;

    public BaseUnitDistanceComparator(Unit target) {
        this.target = target;
    }

    @Override
    public int compare(Base x, Base y) {
        double distanceX = target.getDistance(x.getLocation().toPosition());
        double distanceY = target.getDistance(y.getLocation().toPosition());
        if (distanceX < distanceY) {
            return -1;
        }
        if (distanceX > distanceY) {
            return 1;
        }
        return 0;
    }
}
