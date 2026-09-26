package macro;

/**
 * Rules that decide if the bot wants another hatchery, and whether a hatchery plan queued now
 * survives the frame.
 *
 * <p>Producers and cancellers must agree on the same inputs. Every input to a canceller and to
 * the parity request is invariant under queueing or cancelling a plan: completed hatcheries and
 * macro hatcheries, living larva and the reaction flags. A counter that moves when a plan is
 * created would let a producer switch its own canceller on, and a counter that moves when a plan
 * is cancelled would let the canceller switch its own producer back on.
 *
 * <p>{@link #isFloatingMinerals} is the one want rule that reads planned and reserved counters:
 * in-flight hatchery plans and unreserved minerals. No canceller reads it. Creating a hatchery
 * plan raises its bar, so the request switches itself off; cancelling one lowers the bar and may
 * switch the request back on, and {@link #isEnqueueRearmed} then holds it for the cooldown.
 *
 * <p>{@link #isEnqueueRearmed} reads in-flight plans too, and it is a producer-side rate limit
 * only. No canceller reads it, so a request it holds back cannot switch a canceller on, and its
 * cooldown is set by an enqueue and never reset by a cancel.
 *
 * <p>All rules are race agnostic. The reactions that delete hatchery plans fire on detected enemy
 * strategies, not on the opponent's race.
 */
public final class HatcheryCapacity {

    static final int EXCESS_HATCHERIES = 3;

    static final int EXCESS_LARVA = 5;

    /**
     * Unreserved minerals the floating-minerals request needs per in-flight hatchery plan, and
     * once more on top.
     */
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
     * The excess rule as expansion hatcheries read it: completed macro hatcheries are left out of
     * the count.
     *
     * <p>A macro hatchery answers a larva shortage, and the larva it adds are what the excess rule
     * reads. Counting it would let the hatchery a larva-bound build bought cancel the expansions
     * that build plans for minerals. Macro hatchery plans still read {@link #isExcess} with every
     * hatchery counted.
     *
     * @param hatcheryCount larva-producing hatcheries we control; queued and morphing plans are
     *     excluded
     * @param macroHatcheries completed macro hatcheries among them
     */
    public static boolean isExcessForExpansion(int hatcheryCount, int macroHatcheries, int larvaCount) {
        return isExcess(hatcheryCount - macroHatcheries, larvaCount);
    }

    /**
     * Whether the excess sweep cancels this hatchery plan.
     *
     * @param macroHatchery the plan is a macro hatchery
     * @param excess {@link #isExcess} with every hatchery counted
     * @param excessForExpansion {@link #isExcessForExpansion}
     */
    public static boolean isExcessPlan(boolean macroHatchery, boolean excess, boolean excessForExpansion) {
        return macroHatchery ? excess : excessForExpansion;
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
     * <p>A request can hold for many frames after it is answered. The floating-minerals request
     * raises its own bar when it creates a plan, but a bank that clears the raised bar keeps it
     * true, and the plan stops counting toward the bar the frame its drone morphs, long before the
     * hatchery finishes. A hatchery plan leaves the production queue on the frame it is created,
     * so counting the queue alone does not see it either. This holds the request until the plan
     * it produced has left the production system and the cooldown has run.
     *
     * <p>There is no exception for a hatchery finishing. A hatchery takes far longer to build
     * than the cooldown lasts, so the request the finished hatchery answers is already off
     * cooldown, and an exception keyed on a completed building would be one no plan telemetry
     * column can measure.
     *
     * @param inFlightPlans hatcheries of this kind the bot has committed to and not finished
     * @param framesSinceLastEnqueue frames since any hatchery plan was created
     */
    public static boolean isEnqueueRearmed(int inFlightPlans, int framesSinceLastEnqueue) {
        return inFlightPlans == 0 && framesSinceLastEnqueue >= ENQUEUE_COOLDOWN_FRAMES;
    }

    /**
     * True when unreserved minerals exceed {@link #MINERALS_PER_HATCHERY} for every hatchery plan
     * in flight plus one.
     *
     * <p>The bar does not read completed hatcheries, so it stays at 350 with no hatchery plan in
     * flight however many hatcheries we own. Reservations lower the input, so minerals held for
     * queued plans never count as floating, and reservations that meet or exceed the bank never
     * fire.
     *
     * @param availableMinerals minerals mined and not reserved by a queued plan; negative while
     *     reservations exceed the bank
     * @param plannedHatcheries hatchery plans in flight, expansions and macro hatcheries alike; a
     *     negative count reads as zero
     * @param pastEarlyGame true once the opening is over
     */
    public static boolean isFloatingMinerals(int availableMinerals, int plannedHatcheries, boolean pastEarlyGame) {
        return pastEarlyGame && availableMinerals > (Math.max(0, plannedHatcheries) + 1) * MINERALS_PER_HATCHERY;
    }
}
