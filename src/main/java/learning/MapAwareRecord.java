package learning;

import lombok.Builder;
import lombok.Data;

import java.util.Objects;

/**
 * MapAwareRecord represents a map-specific strategy record.
 * Simple data container for map+strategy performance tracking.
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

    public double index(int totalGames) {
        if (totalGames == 0 || this.games() == 0) {
            return 1.0;
        }
        
        double sampleMean = (double) this.wins() / this.games();
        double c = Math.sqrt(2 * Math.log(totalGames) / (2 * this.games()));
        return sampleMean + c;
    }
}
