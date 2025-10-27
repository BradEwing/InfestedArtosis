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
 * is the number of games since that observation (most recent = 0, next oldest = 1, etc.).
 */
@Builder
@Data
public class Record implements UCBRecord {
    private static final double GAMMA = 0.95;
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

    public int wins() { return wins; }

    public int games() { return wins + losses; }

    public int winsSquared() { return wins * wins; }
    
    public void addWinTimestamp(long timestamp) {
        winTimestamps.add(timestamp);
    }
    
    public void addLossTimestamp(long timestamp) {
        lossTimestamps.add(timestamp);
    }

    public double index(int totalGames) {
        if (totalGames == 0 || this.games() == 0) {
            return 1.0;
        }
        
        double discountedWins = calculateDiscountedWins();
        double discountedGames = calculateDiscountedGames();
        
        if (discountedGames == 0) {
            return 1.0;
        }
        
        double sampleMean = discountedWins / discountedGames;
        double c = Math.sqrt(2 * Math.log(totalGames) / discountedGames);
        return sampleMean + c;
    }
    
    private double calculateDiscountedWins() {
        // Combine all timestamps and sort by chronological order
        List<Long> allTimestamps = new ArrayList<>();
        allTimestamps.addAll(winTimestamps);
        allTimestamps.addAll(lossTimestamps);
        allTimestamps.sort((a, b) -> Long.compare(b, a));
        
        double discountedWins = 0.0;
        for (int i = 0; i < allTimestamps.size(); i++) {
            Long t = allTimestamps.get(i);
            if (winTimestamps.contains(t)) {
                double weight = Math.pow(GAMMA, i);
                discountedWins += weight;
            }
        }
        return discountedWins;
    }
    
    private double calculateDiscountedGames() {
        List<Long> allTimestamps = new ArrayList<>();
        allTimestamps.addAll(winTimestamps);
        allTimestamps.addAll(lossTimestamps);
        
        double discountedGames = 0.0;
        for (int i = 0; i < allTimestamps.size(); i++) {
            double weight = Math.pow(GAMMA, i);
            discountedGames += weight;
        }
        return discountedGames;
    }
}
