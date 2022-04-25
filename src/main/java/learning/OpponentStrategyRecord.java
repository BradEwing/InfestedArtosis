package learning;

import lombok.Data;

@Data
public class OpponentStrategyRecord {
    private String opponent;
    private String strategy;

    private int wins;
    private int loses;
}
