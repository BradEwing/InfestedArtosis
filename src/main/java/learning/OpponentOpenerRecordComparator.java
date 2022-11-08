package learning;

import java.util.Comparator;

public class OpponentOpenerRecordComparator implements Comparator<OpenerRecord>  {

    public OpponentOpenerRecordComparator() {}

    @Override
    public int compare(OpenerRecord x, OpenerRecord y) {
        if (x.netWins() > y.netWins()) {
            return -1;
        }
        if (x.netWins() < y.netWins()) {
            return 1;
        }
        return 0;
    }
}
