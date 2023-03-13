package learning;

import java.util.Comparator;

public class UCBRecordComparator implements Comparator<UCBRecord>  {

    private int totalGames = 0;

    public UCBRecordComparator(int totalGames) { this.totalGames = totalGames; }

    @Override
    public int compare(UCBRecord x, UCBRecord y) {
        double xIndex = x.index(this.totalGames);
        double yIndex = y.index(this.totalGames);
        if (xIndex > yIndex) {
            return -1;
        }
        if (xIndex < yIndex) {
            return 1;
        }
        return 0;
    }
}
