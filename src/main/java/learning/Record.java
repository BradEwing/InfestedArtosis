package learning;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Record tracks performance of a single strategy (opener or build order) using Discounted UCB (D-UCB).
 * 
 * <p>This implementation uses a hybrid D-UCB approach:
 * <ul>
 * <li>Exponential decay (γ=0.95) is applied to strategy-specific observations</li>
 * <li>Raw total games count is used for the exploration term</li>
 * <li>This provides more aggressive exploration when strategies have old data</li>
 * </ul>
 * 
 * <p>The decay makes the system more responsive to recent shifts in opponent behavior
 * while maintaining the theoretical guarantees of UCB for exploration/exploitation balance.
 * 
 * <p>Historical games are stored with timestamps and weighted by γ^(age) where age
 * is the number of opponent games across all strategies since that observation.
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

    public double index(int totalGames) {
        List<Long> gameTimestamps = new ArrayList<>();
        gameTimestamps.addAll(winTimestamps);
        gameTimestamps.addAll(lossTimestamps);
        return index(totalGames, gameTimestamps);
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
            discountedWins += GlobalGameOrder.weight(UCBSelectionPolicy.GAMMA, timestamp, sortedGameTimestamps);
        }
        return discountedWins;
    }
    
    private double calculateDiscountedGames(List<Long> sortedGameTimestamps) {
        List<Long> allTimestamps = new ArrayList<>();
        allTimestamps.addAll(winTimestamps);
        allTimestamps.addAll(lossTimestamps);
        
        double discountedGames = 0.0;
        for (Long timestamp : allTimestamps) {
            discountedGames += GlobalGameOrder.weight(UCBSelectionPolicy.GAMMA, timestamp, sortedGameTimestamps);
        }
        return discountedGames;
    }
}
