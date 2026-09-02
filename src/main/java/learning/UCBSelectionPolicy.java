package learning;

/**
 * Shared decay and exploration-bonus math for the discounted UCB bandit. Wins and losses
 * decay at different rates: a loss fades slower, so an arm that lost recently stays
 * unattractive for many games afterward. An opener that has not been picked lately earns
 * a small curiosity bonus so it is eventually retried, but the bonus is capped: it can
 * never outweigh a clearly better win rate.
 */
final class UCBSelectionPolicy {

    /** Per-game decay of a recorded win; a win is mostly gone after ~20 games. */
    static final double GAMMA_WIN = 0.95;

    /** Per-game decay of a recorded loss; a loss holds near-full weight for ~20 games. */
    static final double GAMMA_LOSS = 0.98;

    /** Half the discounted-win ceiling; floors the exploration denominator for thin records. */
    static final double EFFECTIVE_GAMES_FLOOR = 0.5 / (1.0 - GAMMA_WIN);

    private UCBSelectionPolicy() {
    }

    static double explorationTerm(int totalGames, double discountedGames) {
        double effectiveGames = Math.max(discountedGames, EFFECTIVE_GAMES_FLOOR);
        return Math.sqrt(2 * Math.log(totalGames) / effectiveGames);
    }
}
