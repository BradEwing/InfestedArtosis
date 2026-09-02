package learning;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks win/loss performance of one strategy (opener or build order) for Discounted UCB
 * selection. Wins and losses are stored with timestamps and weighted by gamma to the power
 * of games elapsed since each observation. Losses decay more slowly than wins, so a recent
 * loss keeps pressuring the discounted win rate long after the arm stops being picked.
 */
@Builder
@Data
public class Record implements UCBRecord {
    private String opener;
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

    /**
     * Returns the discounted win rate, weighted by recency against the global game order.
     */
    public double discountedMean(List<Long> gameTimestamps) {
        if (this.games() == 0) {
            return 0.0;
        }
        List<Long> sortedGameTimestamps = GlobalGameOrder.sortedAscending(gameTimestamps);
        double discountedGames = calculateDiscountedGames(sortedGameTimestamps);
        if (discountedGames == 0) {
            return 0.0;
        }
        double discountedWins = calculateDiscountedWins(sortedGameTimestamps);
        return discountedWins / discountedGames;
    }

    /**
     * Returns the discounted game count over the opener's full history as of the latest game.
     */
    double discountedGames(List<Long> gameTimestamps) {
        return calculateDiscountedGames(GlobalGameOrder.sortedAscending(gameTimestamps));
    }

    double discountedGamesBefore(long timestamp, List<Long> gameTimestamps) {
        List<Long> priorGames = gameTimestamps.stream()
                .filter(gameTimestamp -> gameTimestamp < timestamp)
                .collect(java.util.stream.Collectors.toList());
        List<Long> sortedPriorGames = GlobalGameOrder.sortedAscending(priorGames);
        double discountedGames = 0.0;
        for (Long winTimestamp : winTimestamps) {
            if (winTimestamp < timestamp) {
                discountedGames += GlobalGameOrder.weight(UCBSelectionPolicy.GAMMA_WIN, winTimestamp, sortedPriorGames);
            }
        }
        for (Long lossTimestamp : lossTimestamps) {
            if (lossTimestamp < timestamp) {
                discountedGames += GlobalGameOrder.weight(UCBSelectionPolicy.GAMMA_LOSS, lossTimestamp, sortedPriorGames);
            }
        }
        return discountedGames;
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
