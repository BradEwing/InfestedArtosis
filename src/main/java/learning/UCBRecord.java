package learning;

/**
 * UCBRecord describes an opener or strategy record that implements UCB algorithms
 */
public interface UCBRecord {

    int games();

    int wins();

    int winsSquared();

    // Bandit Index
    double index(int totalGames);
}
