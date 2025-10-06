package learning;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.util.Objects;

@Jacksonized
@Builder
@Data
public class Record implements UCBRecord {
    private String opener;
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
            // bias for macro play
            if (Objects.equals(opener, "12Hatch")) {
                return 2.0;
            } else {
                return 1.0;
            }
        }
        double sampleMean = (double) this.wins() / this.games(); // cast to double, otherwise this always returns 0.0
        double c = Math.sqrt(2 * Math.log(totalGames) / (2 * this.games()));
        return sampleMean + c;
    }
}
