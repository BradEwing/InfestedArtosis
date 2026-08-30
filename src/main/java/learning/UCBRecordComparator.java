package learning;

import java.util.Comparator;
import java.util.List;

public class UCBRecordComparator implements Comparator<UCBRecord>  {

    private int totalGames = 0;
    private List<Long> gameTimestamps;

    public UCBRecordComparator(int totalGames, List<Long> gameTimestamps) {
        this.totalGames = totalGames;
        this.gameTimestamps = gameTimestamps;
    }

    @Override
    public int compare(UCBRecord x, UCBRecord y) {
        double xIndex = x.index(this.totalGames, this.gameTimestamps);
        double yIndex = y.index(this.totalGames, this.gameTimestamps);
        if (xIndex > yIndex) {
            return -1;
        }
        if (xIndex < yIndex) {
            return 1;
        }
        return 0;
    }
}
