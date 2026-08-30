package learning;

/**
 * Shared discounted-UCB policy whose idle-arm index rises relative to a selected leader without requiring overtake.
 */
final class UCBSelectionPolicy {

    static final double GAMMA = 0.95;
    /** Half the theoretical discounted-game ceiling. */
    static final double EFFECTIVE_GAMES_FLOOR = 0.5 / (1.0 - GAMMA);

    private UCBSelectionPolicy() {
    }

    static double explorationTerm(int totalGames, double discountedGames) {
        double effectiveGames = Math.max(discountedGames, EFFECTIVE_GAMES_FLOOR);
        return Math.sqrt(2 * Math.log(totalGames) / effectiveGames);
    }
}
