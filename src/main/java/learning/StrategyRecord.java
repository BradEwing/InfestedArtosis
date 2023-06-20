package learning;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Jacksonized
@Builder
@Data
public class StrategyRecord implements UCBRecord {
    private String strategy;
    private int wins;
    private int losses;

    public int netWins() {
        return wins - losses;
    }

    public int wins() { return wins; }

    public int games() { return wins + losses; }

    public int winsSquared() { return wins * wins; }

    public double index(int totalGames) {
        if (totalGames == 0 || this.games() == 0) {
            return 2.0;
        }
        double sampleMean = (double) this.wins() / this.games(); // cast to double, otherwise this always returns 0.0
        double c = Math.sqrt(2 * Math.log(totalGames) / this.games());
        return sampleMean + c;
    }
}
