package learning;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class OpponentStrategyRecord {
    private String strategy;
    private int wins;
    private int loses;
}
