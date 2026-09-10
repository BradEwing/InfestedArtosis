package strategy.buildorder;

/**
 * Zergling target rules shared by build orders that do not share a base class.
 */
public final class ZerglingTargets {

    /**
     * Zerglings a build keeps on the field while its larva belong to a gas unit.
     */
    public static final int GAS_UNIT_FOCUS_FLOOR = 6;

    private ZerglingTargets() {
    }

    /**
     * Whether the gas unit a build is saving larva for is within reach at all.
     * <p>
     * Gas coming in or gas already banked both count. Reading the worker count alone would call
     * the unit unreachable during any gap in gas mining, a worker rebalance or a killed gatherer
     * included, and hand the build a full army target for those frames. Only a bot with neither
     * gas workers nor the gas to pay with is genuinely unable to reach the unit.
     *
     * @param geyserWorkers drones on a geyser
     * @param availableGas gas not already reserved
     * @param unitGasPrice the gas the unit costs
     * @return true while the build could still get the gas unit
     */
    public static boolean gasUnitReachable(int geyserWorkers, int availableGas, int unitGasPrice) {
        return geyserWorkers > 0 || availableGas >= unitGasPrice;
    }

    /**
     * The zergling target a build keeps while it is saving larva for a gas unit.
     * <p>
     * A build that answers zero here stops making army entirely, and the condition that lifts it
     * is a count of units it may never be able to pay for: a den or a spire with no gas behind it
     * never reaches its count, and the zero then stands for the rest of the game. Two things
     * bound it. The hold only applies while the gas unit is reachable at all, which is either gas
     * coming in or gas already banked, so a denied, lost or never-taken geyser hands the matchup
     * target straight back. While it does apply the target falls to {@link #GAS_UNIT_FOCUS_FLOOR}
     * rather than to zero, so the larva go to the gas unit without the build fielding nothing. The
     * floor is a target like any other, so it costs nothing once those zerglings are alive, and it
     * never raises a matchup target that is already lower.
     *
     * @param matchupTarget the target the matchup asks for, before the gas unit takes priority
     * @param techComplete whether the building that unlocks the gas unit finished
     * @param unitsFielded gas units owned and planned
     * @param unitsWanted gas units the build wants before it spends larva on zerglings
     * @param gasReachable whether gas is being mined or is already banked for the unit
     * @return the zergling target the build order should ask for
     */
    public static int gasUnitFocus(int matchupTarget, boolean techComplete, int unitsFielded,
                                   int unitsWanted, boolean gasReachable) {
        if (techComplete && unitsFielded < unitsWanted && gasReachable) {
            return Math.min(matchupTarget, GAS_UNIT_FOCUS_FLOOR);
        }
        return matchupTarget;
    }
}
