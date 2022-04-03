package planner;

import java.util.Comparator;

public class PlannedItemComparator implements Comparator<PlannedItem> {

    @Override
    public int compare(PlannedItem x, PlannedItem y) {
        if (x.getPriority() < y.getPriority()) {
            return -1;
        }
        if (x.getPriority() > y.getPriority()) {
            return 1;
        }
        return 0;
    }
}
