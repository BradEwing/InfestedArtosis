package learning;

import lombok.Data;
import strategy.openers.Opener;
import strategy.strategies.Strategy;

@Data
public class Decisions {
    private Opener opener;
    private Strategy strategy;
    private boolean defensiveSunk;
}
