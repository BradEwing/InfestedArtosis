package learning;

import java.util.List;

/**
 * UCBRecord describes an opener or strategy record that implements UCB algorithms
 */
public interface UCBRecord {

    int games();

    int wins();

    int winsSquared();

    // Bandit Index
    double index(int totalGames);

    double index(int totalGames, List<Long> gameTimestamps);
}
