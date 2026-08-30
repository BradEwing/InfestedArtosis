package learning;

/**
 * Shared exploration-bonus math for the discounted UCB bandit. An opener that has not
 * been picked lately earns a small curiosity bonus so it is eventually retried, but the
 * bonus is capped: it can never outweigh a clearly better win rate.
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
