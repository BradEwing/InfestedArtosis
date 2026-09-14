package strategy.buildorder;

import bwapi.UnitType;

/**
 * The macro hatchery request shared by the Spire builds that go larva bound once Mutalisks are
 * the spend.
 */
public final class SpireMacroHatchery {

    /**
     * Unreserved minerals that count as floating: the hatchery itself, so buying one does not
     * take the bank below zero.
     */
    public static final int FLOAT_MINERALS = UnitType.Zerg_Hatchery.mineralPrice();

    /**
     * Unreserved gas that counts as floating: two Mutalisks the build could not find larva for.
     */
    public static final int FLOAT_GAS = 2 * UnitType.Zerg_Mutalisk.gasPrice();

    private SpireMacroHatchery() {
    }

    /**
     * Whether the build should add a macro hatchery.
     *
     * <p>The build is larva limited once its Spire is up, so the signal is the one a larva limit
     * produces: fewer free larva than hatcheries to make them, while both unreserved banks sit
     * above what the next purchases need. Minerals alone cannot see this state, because a Mutalisk
     * build that cannot find larva floats gas as well, and a bar scaled to hatchery count waits
     * for a mineral pile the build never reaches.
     *
     * <p>The banks are read after reservations, so resources a queued plan has already claimed do
     * not count as floating. The Spire term keeps the rule shut while the build is still banking
     * for the Spire it has not placed.
     *
     * @param committedSpires Spires standing, under construction, or claimed by a plan in flight
     * @param larva larva not yet handed to a plan, from {@link info.GameState#numLarva()}
     * @param hatcheries completed larva-producing hatcheries
     * @param availableMinerals minerals mined and not reserved by a queued plan
     * @param availableGas gas mined and not reserved by a queued plan
     * @return true when a macro hatchery should be requested
     */
    public static boolean shouldPlan(int committedSpires, int larva, int hatcheries,
                                     int availableMinerals, int availableGas) {
        return committedSpires > 0
                && larva < hatcheries
                && availableMinerals >= FLOAT_MINERALS
                && availableGas >= FLOAT_GAS;
    }
}
