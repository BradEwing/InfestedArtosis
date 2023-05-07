package info.map;

import java.util.Comparator;

public class GroundPathComparator implements Comparator<GroundPath> {

    public GroundPathComparator() {}

    @Override
    public int compare(GroundPath x, GroundPath y) {
        if (x.getGroundDistance() > y.getGroundDistance()) {
            return -1;
        } else if (x.getGroundDistance() < y.getGroundDistance()) {
            return 1;
        }
        return 0;
    }
}
