package macro.plan;

import java.util.Comparator;

/**
 * Orders plans by priority. At equal priority a Sunken or Spore morph comes first: one whose Creep
 * Colony is complete has only its cost left to wait for, and one whose colony is not ready is
 * blocked before it can claim the bank or the build-ahead slot, so scanning it first costs the
 * plans behind it nothing.
 */
public class PlanComparator implements Comparator<Plan> {

    @Override
    public int compare(Plan x, Plan y) {
        if (x.getPriority() < y.getPriority()) {
            return -1;
        }
        if (x.getPriority() > y.getPriority()) {
            return 1;
        }
        boolean xColonyMorph = ColonyClaims.isColonyMorph(x.getPlannedUnit());
        boolean yColonyMorph = ColonyClaims.isColonyMorph(y.getPlannedUnit());
        return Boolean.compare(yColonyMorph, xColonyMorph);
    }
}
