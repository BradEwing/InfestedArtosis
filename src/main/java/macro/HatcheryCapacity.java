package macro;

/**
 * Rules that decide if the bot wants another hatchery, and whether a hatchery plan queued now
 * survives the frame.
 *
 * <p>Producers and cancellers must agree on the same inputs. Every input to a want rule is
 * invariant under queueing or cancelling a plan: completed hatcheries, living larva, mined
 * minerals and the reaction flags. No want rule reads a planned or reserved counter, because a
 * counter that moves when a plan is created lets a producer switch its own canceller on, and a
 * counter that moves when a plan is cancelled lets the canceller switch its own producer back on.
 *
 * <p>{@link #isEnqueueRearmed} is the one rule that reads in-flight plans, and it is a
 * producer-side rate limit only. No canceller reads it, so a request it holds back cannot switch
 * a canceller on, and its cooldown is set by an enqueue and never reset by a cancel.
 *
 * <p>All rules are race agnostic. The reactions that delete hatchery plans fire on detected enemy
 * strategies, not on the opponent's race.
 */
public final class HatcheryCapacity {

    static final int EXCESS_HATCHERIES = 3;

    static final int EXCESS_LARVA = 5;

    static final int MINERALS_PER_HATCHERY = 350;

    /**
     * Frames a hatchery request waits after creating a plan before it may create another.
     */
    public static final int ENQUEUE_COOLDOWN_FRAMES = 200;

    private HatcheryCapacity() {
    }

    /**
     * True once our hatcheries produce more larva than we spend.
     *
     * <p>Reads capacity alone. Floating minerals are a producer signal, and a producer signal
     * that disabled this rule would leave the canceller weakest exactly while the producer is
     * strongest.
     *
     * @param hatcheryCount larva-producing hatcheries we control; queued and morphing plans are
     *     excluded
     */
    public static boolean isExcess(int hatcheryCount, int larvaCount) {
        return hatcheryCount >= EXCESS_HATCHERIES && larvaCount >= EXCESS_LARVA;
    }

    /**
     * @param ourTotal hatchery count plus hatcheries already queued
     * @param expansionSuppressed true while a reaction is holding expansion hatcheries out of the
     *     queue
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
     * True when a hatchery request that has already been answered may be answered again.
     *
     * <p>A request such as floating minerals holds for many frames, and nothing it reads moves
     * when the plan it produced is created, so the request re-arms every frame on its own. A
     * hatchery plan leaves the production queue on the frame it is created, so counting the
     * queue alone does not see it either. This holds the request until the plan it produced has
     * left the production system and the cooldown has run, or until a hatchery has completed.
     *
     * @param inFlightPlans hatchery plans the production system still carries, at any stage
     * @param framesSinceLastEnqueue frames since the last hatchery plan was created
     * @param hatcheriesSinceLastEnqueue hatcheries completed since the last hatchery plan was created
     */
    public static boolean isEnqueueRearmed(int inFlightPlans, int framesSinceLastEnqueue, int hatcheriesSinceLastEnqueue) {
        if (inFlightPlans > 0) {
            return false;
        }

        return framesSinceLastEnqueue >= ENQUEUE_COOLDOWN_FRAMES || hatcheriesSinceLastEnqueue > 0;
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
