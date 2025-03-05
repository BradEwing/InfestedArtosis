package plan;

import java.util.Comparator;

public class PlanComparator implements Comparator<Plan> {

    @Override
    public int compare(Plan x, Plan y) {
        if (x.getPriority() < y.getPriority()) {
            return -1;
        }
        if (x.getPriority() > y.getPriority()) {
            return 1;
        }
        return 0;
    }
}
