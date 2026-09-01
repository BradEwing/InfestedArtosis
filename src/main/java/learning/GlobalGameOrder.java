package learning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class GlobalGameOrder {

    private GlobalGameOrder() {
    }

    static List<Long> sortedAscending(List<Long> gameTimestamps) {
        List<Long> sorted = new ArrayList<>(gameTimestamps);
        Collections.sort(sorted);
        return sorted;
    }

    static double weight(double gamma, long timestamp, List<Long> sortedGameTimestamps) {
        int gamesAfter = sortedGameTimestamps.size() - upperBound(sortedGameTimestamps, timestamp);
        return Math.pow(gamma, gamesAfter);
    }

    /**
     * Returns the number of recorded games played after the given timestamp.
     */
    static int gamesSinceLastSelection(List<Long> gameTimestamps, long lastSelectionTimestamp) {
        List<Long> sorted = sortedAscending(gameTimestamps);
        return sorted.size() - upperBound(sorted, lastSelectionTimestamp);
    }

    static int upperBound(List<Long> sortedGameTimestamps, long timestamp) {
        int low = 0;
        int high = sortedGameTimestamps.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (sortedGameTimestamps.get(mid) <= timestamp) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }
}
