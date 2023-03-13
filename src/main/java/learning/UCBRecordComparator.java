package learning;

import java.util.Comparator;

public class UCBRecordComparator implements Comparator<UCBRecord>  {

    private int totalGames = 0;

    public UCBRecordComparator(int totalGames) { this.totalGames = totalGames; }

    @Override
    public int compare(UCBRecord x, UCBRecord y) {
        if (x.index(this.totalGames) > y.index(this.totalGames)) {
            return -1;
        }
        if (x.index(this.totalGames) < y.index(this.totalGames)) {
            return 1;
        }
        return 0;
    }
}
