package learning;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class StrategyRecord {
    private String strategy;
    private int wins;
    private int losses;

    public int netWins() {
        return wins - losses;
    }
}
