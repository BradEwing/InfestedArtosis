package learning;

import lombok.Data;
import strategy.buildorder.BuildOrder;
import strategy.strategies.Strategy;

@Data
public class Decisions {
    private BuildOrder opener;
    private Strategy strategy;
    private boolean defensiveSunk;
}
