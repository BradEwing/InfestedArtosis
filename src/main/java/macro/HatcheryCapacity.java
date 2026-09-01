package macro;

/**
 * Rules that decide if the bot wants another hatchery.
 *
 * <p>The rules must agree on the same hatchery count. Both read
 * {@link info.GameState#hatcheryCount()}.
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
}
