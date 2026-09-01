package macro;

/**
 * Rules that decide if the bot wants another hatchery, and whether a hatchery plan queued now
 * survives the frame.
 *
 * <p>Producers and cancellers must agree on the same inputs. Every input here is invariant under
 * queueing or cancelling a plan: completed hatcheries, living larva, mined minerals and the
 * reaction flags. No rule reads a planned or reserved counter, because a counter that moves when
 * a plan is created lets a producer switch its own canceller on, and a counter that moves when a
 * plan is cancelled lets the canceller switch its own producer back on.
 *
 * <p>All rules are race agnostic. The reactions that delete hatchery plans fire on detected enemy
 * strategies, not on the opponent's race.
 */
public final class HatcheryCapacity {

    static final int EXCESS_HATCHERIES = 3;

    static final int EXCESS_LARVA = 5;

    static final int MINERALS_PER_HATCHERY = 350;

    private HatcheryCapacity() {
    }

    /**
     * Floating minerals override the larva count. Minerals that we cannot spend are worth a new
     * hatchery, even when our larva are already idle.
     *
     * @param hatcheryCount larva-producing hatcheries we control; queued and morphing plans are
     *     excluded
     */
    public static boolean isExcess(int hatcheryCount, int larvaCount, boolean floatingMinerals) {
        return !floatingMinerals && hatcheryCount >= EXCESS_HATCHERIES && larvaCount >= EXCESS_LARVA;
    }

    /**
     * @param ourTotal hatchery count plus hatcheries already queued
     * @param expansionSuppressed true while a reaction deletes queued expansion hatcheries every
     *     frame
     */
    public static boolean isBehind(int ourTotal, int enemyTotal, boolean excess, boolean expansionSuppressed) {
        return !expansionSuppressed && !excess && enemyTotal > ourTotal;
    }

    /**
     * The floating-minerals hatchery request, under the same suppression as the parity request.
     */
    public static boolean isFloatingExpansion(boolean floatingMinerals, boolean expansionSuppressed) {
        return !expansionSuppressed && floatingMinerals;
    }

    /**
     * True when a hatchery plan created this frame survives it. Every producer asks this before
     * creating a hatchery plan, whatever the opponent's race.
     *
     * @param excess the excess rule cancels queued hatcheries this frame
     * @param deletedByReaction a reaction cancels hatchery plans this frame
     */
    public static boolean isQueueable(boolean excess, boolean deletedByReaction) {
        return !excess && !deletedByReaction;
    }

    /**
     * True when mined minerals outstrip what our larva-producing hatcheries can spend.
     *
     * @param minerals minerals mined and unspent, before any reservation
     * @param hatcheryCount completed larva-producing hatcheries
     * @param pastEarlyGame true once the opening is over
     */
    public static boolean isFloatingMinerals(int minerals, int hatcheryCount, boolean pastEarlyGame) {
        return pastEarlyGame && minerals > (hatcheryCount + 1) * MINERALS_PER_HATCHERY;
    }
}
