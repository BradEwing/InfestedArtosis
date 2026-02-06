package unit.managed;

import bwapi.Position;

import java.util.Comparator;

/**
 * ManagedUnitToPositionComparator sorts a list of managed units by distance to the target position.
 */
public class ManagedUnitToPositionComparator implements Comparator<ManagedUnit> {

    private Position target;

    public ManagedUnitToPositionComparator(Position target) {
        this.target = target;
    }

    @Override
    public int compare(ManagedUnit x, ManagedUnit y) {
        double distanceX = target.getDistance(x.getUnit().getPosition());
        double distanceY = target.getDistance(y.getUnit().getPosition());
        return Double.compare(distanceX, distanceY);
    }
}