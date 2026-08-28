package telemetry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Supply-weighted quantiles over the arrival offsets of an engagement.
 *
 * Weighting by supply rather than by unit count keeps a late ultralisk from reading like a late
 * zergling: the question the trickle metric asks is how much of the army was late, not how many
 * bodies were.
 */
final class SupplyQuantiles {

    private SupplyQuantiles() {}

    /**
     * @param arrivals one {@code {offsetFrames, supplyWeight}} pair per unit
     * @param quantile fraction in [0, 1]
     * @return the smallest offset at which the cumulative supply weight reaches the quantile,
     *         or -1 when there is no positively weighted arrival
     */
    static int weighted(List<int[]> arrivals, double quantile) {
        if (arrivals.isEmpty()) {
            return -1;
        }

        int total = 0;
        for (int[] arrival : arrivals) {
            total += arrival[1];
        }
        if (total <= 0) {
            return -1;
        }

        List<int[]> sorted = new ArrayList<>(arrivals);
        sorted.sort(Comparator.comparingInt(arrival -> arrival[0]));

        double threshold = quantile * total;
        int cumulative = 0;
        for (int[] arrival : sorted) {
            cumulative += arrival[1];
            if (cumulative >= threshold) {
                return arrival[0];
            }
        }
        return sorted.get(sorted.size() - 1)[0];
    }
}
