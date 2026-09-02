package learning;

/**
 * Shared decay and curiosity math for the discounted UCB bandit. Wins and losses decay at one
 * rate, so an arm's discounted mean is a win rate and the gates calibrated against it
 * ({@code PROBE_GATE_WIN_RATE}, {@code REPEAT_DONATION_WIN_RATE}) read in win-rate points.
 *
 * <p>An arm holding little discounted evidence earns a curiosity bonus so it is eventually
 * retried. The bonus is bounded by {@link #CURIOSITY_CAP} and carries no dependence on how many
 * games the opponent has been played, so curiosity alone can never displace an incumbent whose
 * discounted win rate leads by more than the cap, at any history length.
 */
final class UCBSelectionPolicy {

    /** Per-game decay of a recorded game; an observation is mostly gone after ~20 games. */
    static final double GAMMA = 0.95;

    /** Largest curiosity bonus, in win-rate points, and the score of an arm with no evidence. */
    static final double CURIOSITY_CAP = 0.15;

    /** Discounted games at which curiosity reaches zero. */
    static final double CURIOSITY_HORIZON = 10.0;

    private UCBSelectionPolicy() {
    }

    static double curiosity(double discountedGames) {
        return CURIOSITY_CAP * Math.max(0.0, 1.0 - discountedGames / CURIOSITY_HORIZON);
    }
}
