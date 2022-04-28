package learning;

import java.util.Comparator;

public class OpponentStrategyRecordComparator implements Comparator<StrategyRecord>  {

    public OpponentStrategyRecordComparator() {}

    @Override
    public int compare(StrategyRecord x, StrategyRecord y) {
        if (x.netWins() < y.netWins()) {
            return -1;
        }
        if (x.netWins() > y.netWins()) {
            return 1;
        }
        return 0;
    }
}
