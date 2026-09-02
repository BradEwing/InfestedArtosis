package learning;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * MapAwareRecord represents a map-specific strategy record using Discounted UCB (D-UCB).
 *
 * <p>This implementation uses a hybrid D-UCB approach:
 * <ul>
 * <li>Asymmetric exponential decay is applied to strategy-specific observations: wins fade
 * at GAMMA_WIN while losses fade at the slower GAMMA_LOSS</li>
 * <li>The discounted game count drives the exploration term, floored by
 * {@link UCBSelectionPolicy#EFFECTIVE_GAMES_FLOOR} for thin records</li>
 * </ul>
 *
 * <p>The decay makes the system more responsive to recent shifts in opponent behavior
 * on specific maps while maintaining the theoretical guarantees of UCB for exploration/exploitation balance.
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

    public double index(int totalGames, List<Long> gameTimestamps) {
        if (totalGames == 0) {
            return Math.random();
        }

        if (this.games() == 0) {
            return Math.sqrt(Math.log(totalGames)) + (Math.random() * 0.2 - 0.1);
        }

        List<Long> sortedGameTimestamps = GlobalGameOrder.sortedAscending(gameTimestamps);
        double discountedWins = calculateDiscountedWins(sortedGameTimestamps);
        double discountedGames = calculateDiscountedGames(sortedGameTimestamps);

        if (discountedGames == 0) {
            return 1.0;
        }

        double sampleMean = discountedWins / discountedGames;
        double c = UCBSelectionPolicy.explorationTerm(totalGames, discountedGames);
        return sampleMean + c;
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
            discountedWins += GlobalGameOrder.weight(UCBSelectionPolicy.GAMMA_WIN, timestamp, sortedGameTimestamps);
        }
        return discountedWins;
    }

    private double calculateDiscountedGames(List<Long> sortedGameTimestamps) {
        double discountedGames = calculateDiscountedWins(sortedGameTimestamps);
        for (Long timestamp : lossTimestamps) {
            discountedGames += GlobalGameOrder.weight(UCBSelectionPolicy.GAMMA_LOSS, timestamp, sortedGameTimestamps);
        }
        return discountedGames;
    }
}
