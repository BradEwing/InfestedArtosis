package macro;

import bwapi.UnitType;
import macro.plan.Plan;
import macro.plan.PlanType;

/**
 * ProductionManager cancels queued overlords while free supply exceeds the threshold.
 * Every overlord gate reads this predicate so derivation and cancellation agree.
 */
public final class SupplyCapacity {

    private static final int MAX_FREE_SUPPLY = 17;

    /** Returned when no waiting unit plan costs supply, so no plan can be supply blocked. */
    public static final int NO_QUEUED_UNIT = Integer.MAX_VALUE;

    public static boolean isExcess(int supplyTotal, int supplyUsed) {
        return supplyTotal - supplyUsed > MAX_FREE_SUPPLY;
    }

    /**
     * Raw supply a single morph of this type consumes, matching the arithmetic Game.canMake runs
     * before it will allow the morph. Zerglings and scourge hatch two to an egg and are charged
     * for both, so the cheapest morph any zerg plan can ask for is two raw supply.
     */
    public static int morphSupplyCost(UnitType unitType) {
        return unitType.supplyRequired() * (unitType.isTwoUnitsInOneEgg() ? 2 : 1);
    }

    /**
     * Morph cost of the cheapest unit plan waiting in the given set, or {@link #NO_QUEUED_UNIT}
     * when none of them costs supply. Overlords cost nothing and are therefore never the cheapest.
     */
    public static int cheapestUnitSupply(Iterable<Plan> plans) {
        int cheapest = NO_QUEUED_UNIT;
        for (Plan plan : plans) {
            if (plan.getType() != PlanType.UNIT) {
                continue;
            }
            int cost = morphSupplyCost(plan.getPlannedUnit());
            if (cost > 0 && cost < cheapest) {
                cheapest = cost;
            }
        }
        return cheapest;
    }

    /**
     * Free supply cannot cover the cheapest waiting unit, so none of them can morph. Testing
     * against the cheapest plan rather than against zero catches the bot sitting one raw supply
     * short of every plan it holds, which reads as free supply to an equality check.
     */
    public static boolean isBlocked(int supplyTotal, int supplyUsed, int cheapestUnitSupply) {
        return cheapestUnitSupply != NO_QUEUED_UNIT && supplyTotal - supplyUsed < cheapestUnitSupply;
    }
}
