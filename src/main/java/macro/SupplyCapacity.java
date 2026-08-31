package macro;

/**
 * ProductionManager cancels queued overlords while free supply exceeds the threshold.
 * Every overlord gate reads this predicate so derivation and cancellation agree.
 */
public final class SupplyCapacity {

    private static final int MAX_FREE_SUPPLY = 17;

    public static boolean isExcess(int supplyTotal, int supplyUsed) {
        return supplyTotal - supplyUsed > MAX_FREE_SUPPLY;
    }
}
