package macro;

/**
 * The two rules that decide if we want another hatchery.
 *
 * <p>Production cancels a queued hatchery when we do not spend the larva we have. A ZvZ build
 * order asks for a hatchery when the enemy has more resource depots than we do. Our count
 * includes the hatcheries that we queued.
 *
 * <p>The two rules stay in this class because they must agree. If they disagree, the build order
 * adds the hatchery plan and production cancels it on the next frame. The bot repeats this every
 * frame and never starts its unit production.
 */
public final class HatcheryCapacity {

    static final int EXCESS_HATCHERIES = 3;

    static final int EXCESS_LARVA = 5;

    private HatcheryCapacity() {
    }

    /**
     * Floating minerals override the larva count. Minerals that we cannot spend are worth a new
     * hatchery, even when our larva are already idle.
     */
    public static boolean isExcess(int hatcheryCount, int larvaCount, boolean floatingMinerals) {
        return !floatingMinerals && hatcheryCount >= EXCESS_HATCHERIES && larvaCount >= EXCESS_LARVA;
    }

    public static boolean isBehind(int ourTotal, int enemyTotal, boolean excess) {
        return !excess && enemyTotal > ourTotal;
    }
}
