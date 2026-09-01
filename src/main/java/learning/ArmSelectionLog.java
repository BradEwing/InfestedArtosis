package learning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Selection history of one opener, expressed in games: games since it was last selected,
 * games since it last re-entered after a dormant stretch, and games played and won since
 * that re-entry.
 */
final class ArmSelectionLog {

    static final int NEVER_SELECTED = Integer.MAX_VALUE;

    private final int gamesSinceLastSelection;
    private final int reEntryAge;
    private final int trialCount;
    private final int trialWins;

    private ArmSelectionLog(int gamesSinceLastSelection, int reEntryAge, int trialCount, int trialWins) {
        this.gamesSinceLastSelection = gamesSinceLastSelection;
        this.reEntryAge = reEntryAge;
        this.trialCount = trialCount;
        this.trialWins = trialWins;
    }

    /**
     * Builds the log from a Record's win and loss timestamps against the global game order.
     * A re-entry is a selection preceded by a gap of at least dormantGames games. The trial
     * is every selection from the most recent re-entry onward.
     */
    static ArmSelectionLog from(Record record, List<Long> gameTimestamps, int dormantGames) {
        List<Long> selectionTimestamps = new ArrayList<>(record.getWinTimestamps());
        selectionTimestamps.addAll(record.getLossTimestamps());
        if (selectionTimestamps.isEmpty()) {
            return new ArmSelectionLog(NEVER_SELECTED, NEVER_SELECTED, 0, 0);
        }
        Collections.sort(selectionTimestamps);
        Set<Long> winTimestamps = new HashSet<>(record.getWinTimestamps());
        List<Long> sortedGameTimestamps = GlobalGameOrder.sortedAscending(gameTimestamps);

        int reEntryIndex = 0;
        for (int i = 1; i < selectionTimestamps.size(); i++) {
            int olderAge = age(selectionTimestamps.get(i - 1), sortedGameTimestamps);
            int newerAge = age(selectionTimestamps.get(i), sortedGameTimestamps);
            if (olderAge - newerAge - 1 >= dormantGames) {
                reEntryIndex = i;
            }
        }

        int trialWins = 0;
        for (int i = reEntryIndex; i < selectionTimestamps.size(); i++) {
            if (winTimestamps.contains(selectionTimestamps.get(i))) {
                trialWins++;
            }
        }

        int newestAge = age(selectionTimestamps.get(selectionTimestamps.size() - 1), sortedGameTimestamps);
        int reEntryAge = age(selectionTimestamps.get(reEntryIndex), sortedGameTimestamps);
        int trialCount = selectionTimestamps.size() - reEntryIndex;
        return new ArmSelectionLog(newestAge, reEntryAge, trialCount, trialWins);
    }

    private static int age(long timestamp, List<Long> sortedGameTimestamps) {
        return sortedGameTimestamps.size() - GlobalGameOrder.upperBound(sortedGameTimestamps, timestamp);
    }

    int gamesSinceLastSelection() {
        return gamesSinceLastSelection;
    }

    int reEntryAge() {
        return reEntryAge;
    }

    int trialCount() {
        return trialCount;
    }

    int trialWins() {
        return trialWins;
    }
}
