package learning;

import java.util.List;
import java.util.Map;

/**
 * Gives every seeded arm one real game. A prior-only arm is never swept first the way an untried arm is, so when
 * the leading arm's discounted win rate falls below {@link LearningManager#PROBE_GATE_WIN_RATE} the pick goes to
 * the prior-only arm with the highest prior win rate instead.
 */
final class PriorSafetyNet {

    private PriorSafetyNet() {
    }

    /**
     * Returns the prior-only candidate with the highest prior win rate (ties to the lower name) when the leader's
     * discounted win rate is below the gate, otherwise the leader.
     */
    static String apply(String leader,
                        List<String> candidates,
                        Map<String, Record> records,
                        List<Long> gameTimestamps) {
        Record leaderRecord = records.get(leader);
        if (leaderRecord == null || leaderRecord.games() == 0
                || leaderRecord.discountedMean(gameTimestamps) >= LearningManager.PROBE_GATE_WIN_RATE) {
            return leader;
        }
        String best = null;
        double bestMean = Double.NEGATIVE_INFINITY;
        for (String candidate : candidates) {
            Record record = records.get(candidate);
            if (record == null || !record.isPriorOnly()) {
                continue;
            }
            double mean = record.priorMean();
            if (mean > bestMean || mean == bestMean && candidate.compareTo(best) < 0) {
                bestMean = mean;
                best = candidate;
            }
        }
        return best != null ? best : leader;
    }
}
