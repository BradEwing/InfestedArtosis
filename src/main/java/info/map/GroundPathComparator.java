package info.map;

import java.util.Comparator;

/**
 * Orders ground paths by ascending ground distance.
 *
 * <p>Null paths represent an unknown distance and are ordered last so that sorting a collection that
 * contains them cannot throw.
 */
public class GroundPathComparator implements Comparator<GroundPath> {

    public GroundPathComparator() {}

    @Override
    public int compare(GroundPath x, GroundPath y) {
        if (x == null) {
            return y == null ? 0 : 1;
        }
        if (y == null) {
            return -1;
        }
        return Integer.compare(x.getGroundDistance(), y.getGroundDistance());
    }
}
