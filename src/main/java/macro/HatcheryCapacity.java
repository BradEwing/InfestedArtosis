package macro;

/**
 * The rules that decide if we want another hatchery.
 *
 * <p>Production cancels a queued hatchery when we do not spend the larva we have. A ZvZ build
 * order asks for a hatchery when the enemy has more resource depots than we do, or when our
 * minerals float. The early-rush reaction deletes every queued expansion hatchery on each frame
 * it holds, so both requests are suppressed while it holds.
 *
 * <p>The rules stay in this class because they must agree. If they disagree, the build order
 * adds a hatchery plan and a canceller removes it on the same frame. The bot repeats this every
 * frame and never starts its unit production. Both halves read the one hatchery count from
 * {@link info.GameState#hatcheryCount()}: larva-producing hatcheries we control, with queued
 * plans excluded, so a request can never itself create the excess that cancels it.
 */
public final class HatcheryCapacity {

    static final int EXCESS_HATCHERIES = 3;

    static final int EXCESS_LARVA = 5;

    private HatcheryCapacity() {
    }

    /**
     * Floating minerals override the larva count. Minerals that we cannot spend are worth a new
     * hatchery, even when our larva are already idle.
     *
     * @param hatcheryCount larva-producing hatcheries we control, from
     *     {@link info.GameState#hatcheryCount()}; queued and morphing plans are excluded,
     *     because counting the plan we just queued here is what let the enqueue and the excess
     *     cancel chase each other every frame
     */
    public static boolean isExcess(int hatcheryCount, int larvaCount, boolean floatingMinerals) {
        return !floatingMinerals && hatcheryCount >= EXCESS_HATCHERIES && larvaCount >= EXCESS_LARVA;
    }

    /**
     * @param ourTotal the hatchery count plus the hatcheries already queued, so one queued plan
     *     holds the request down instead of being re-requested next frame
     * @param expansionSuppressed true while a reaction deletes queued expansion hatcheries every
     *     frame, which makes any request this rule approves a same-frame cancel
     */
    public static boolean isBehind(int ourTotal, int enemyTotal, boolean excess, boolean expansionSuppressed) {
        return !expansionSuppressed && !excess && enemyTotal > ourTotal;
    }

    /**
     * The floating-minerals hatchery request, under the same suppression as the parity request.
     * The enqueue/cancel loop starves unit production, the unspent minerals float, and the
     * floating request would otherwise re-queue the expansion the reaction keeps deleting.
     */
    public static boolean isFloatingExpansion(boolean floatingMinerals, boolean expansionSuppressed) {
        return !expansionSuppressed && floatingMinerals;
    }
}
