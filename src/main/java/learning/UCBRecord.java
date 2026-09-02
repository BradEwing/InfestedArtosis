package learning;

import java.util.List;

/**
 * UCBRecord describes an opener or strategy record that implements UCB algorithms
 */
public interface UCBRecord {

    int games();

    int wins();

    int winsSquared();

    /**
     * Bandit index on an explicit clock. The caller owns the clock: an opponent record ages on the
     * opponent's games, a map record on that map's games. There is deliberately no overload that
     * infers a clock, because one that inferred it from the record's own timestamps scored a
     * different algorithm than production ran.
     */
    double index(int totalGames, List<Long> gameTimestamps);
}
