package learning;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * MapAwareRecord represents a map-specific strategy record using Discounted UCB (D-UCB).
 *
 * <p>The index is scored the same way an opponent record is:
 * <ul>
 * <li>Wins and losses decay alike at {@link UCBSelectionPolicy#GAMMA}, so the discounted mean is
 * a recency-weighted win rate</li>
 * <li>A thin record earns a curiosity bonus that fades to zero by
 * {@link UCBSelectionPolicy#CURIOSITY_HORIZON} discounted games and is bounded by
 * {@link UCBSelectionPolicy#CURIOSITY_CAP}</li>
 * </ul>
 *
 * <p>The decay makes the system more responsive to recent shifts in opponent behavior
 * on specific maps, and the bounded bonus keeps an untried strategy competing for the slot without
 * displacing one whose map win rate leads by more than the cap.
 *
 * <p>Historical games are stored with timestamps and weighted by γ^(age). Age is measured on the
 * clock the caller supplies. Production passes the games played on this record's own map, so a
 * map record ages once per appearance of that map rather than once per opponent game; with a
 * fourteen-map pool the two differ by roughly fourteen times.
 */
@Builder
@Data
public class MapAwareRecord implements UCBRecord {
    private String strategy;
    private String mapName;
    private String opponentName;
    private String opponentRace;
    private int wins;
    private int losses;
    @Default
    private List<Long> winTimestamps = new ArrayList<>();
    @Default
    private List<Long> lossTimestamps = new ArrayList<>();

    public int netWins() {
        return wins - losses;
    }

    public int wins() {
        return wins;
    }

    public int games() {
        return wins + losses;
    }

    public int winsSquared() {
        return wins * wins;
    }

    public void addWinTimestamp(long timestamp) {
        winTimestamps.add(timestamp);
    }

    public void addLossTimestamp(long timestamp) {
        lossTimestamps.add(timestamp);
    }

    /** Discounted map win rate plus the bounded curiosity bonus for thin map evidence. */
    public double index(int totalGames, List<Long> gameTimestamps) {
        if (totalGames == 0) {
            return Math.random();
        }

        List<Long> sortedGameTimestamps = GlobalGameOrder.sortedAscending(gameTimestamps);
        double discountedGames = calculateDiscountedGames(sortedGameTimestamps);
        double sampleMean = 0.0;
        if (discountedGames > 0) {
            sampleMean = calculateDiscountedWins(sortedGameTimestamps) / discountedGames;
        }
        return sampleMean + UCBSelectionPolicy.curiosity(discountedGames);
    }

    /** Discounted games on the supplied clock; the same quantity the index scores. */
    public double discountedGames(List<Long> gameTimestamps) {
        if (this.games() == 0) {
            return 0.0;
        }
        return calculateDiscountedGames(GlobalGameOrder.sortedAscending(gameTimestamps));
    }

    private double calculateDiscountedWins(List<Long> sortedGameTimestamps) {
        double discountedWins = 0.0;
        for (Long timestamp : winTimestamps) {
            discountedWins += GlobalGameOrder.weight(UCBSelectionPolicy.GAMMA, timestamp, sortedGameTimestamps);
        }
        return discountedWins;
    }

    private double calculateDiscountedGames(List<Long> sortedGameTimestamps) {
        double discountedGames = calculateDiscountedWins(sortedGameTimestamps);
        for (Long timestamp : lossTimestamps) {
            discountedGames += GlobalGameOrder.weight(UCBSelectionPolicy.GAMMA, timestamp, sortedGameTimestamps);
        }
        return discountedGames;
    }
}
