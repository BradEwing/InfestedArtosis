package strategy.buildorder;

import bwapi.UnitType;

/**
 * The Hive-branch request shared by the builds that tech to Hive once their gas outruns what the
 * rest of the build can spend.
 *
 * <p>Counting geysers answers a different question than the branch asks. A structure count cannot
 * tell a bot that lost its gas from one that never had it, nor three Extractors with drones on
 * them from three standing empty, so a build keyed on it stalls on a position that offers two
 * safe geysers even while the bank it needs is already mined. The bank itself is the term the
 * branch actually depends on.
 *
 * <p>Income rate is the same class of proxy and is deliberately not read: two-geyser income funds
 * a Queen's Nest perfectly well, which is the case a geyser count already refuses to survive.
 */
public final class GasBoundHiveTech {

    /**
     * Unreserved gas the branch has to hold: the Queen's Nest plus the Hive it exists to unlock.
     *
     * <p>The pair is the commitment, not either building alone. A Queen's Nest bought on a bank
     * that cannot follow it with a Hive spends the gas and unlocks nothing.
     */
    public static final int BRANCH_GAS = UnitType.Zerg_Queens_Nest.gasPrice() + UnitType.Zerg_Hive.gasPrice();

    /**
     * Frames the bank must stay at or above the bar before the gate opens, one logger flush
     * interval, so a bank that touches the bar for a frame on its way to a Mutalisk does not buy
     * a Queen's Nest the build cannot pay for.
     */
    public static final int SUSTAINED_FRAMES = 480;

    /**
     * The first gate a request stops on, in the order {@link #evaluate} reads them.
     */
    public enum Gate {
        /** The Lair is unfinished, or the structure is already planned or standing. */
        TECH_UNAVAILABLE,
        /** The unreserved gas bank is below the branch bar, or has not held it long enough. */
        GAS_SHORT,
        /** Every gate is open. */
        TRIGGER;

        /**
         * Whether the structure is one the build could plan this frame, so this gate either
         * answers the request or withholds it.
         */
        public boolean isRequest() {
            return this != TECH_UNAVAILABLE;
        }
    }

    private GasBoundHiveTech() {
    }

    /**
     * The gate the Hive-branch request stops on.
     *
     * <p>The bank is read after reservations, so gas a queued plan has already claimed does not
     * open the gate. The sustained term is the frames the bank has stayed at or above the bar
     * without an observed dip, which is what separates a build floating gas it has nothing to
     * spend on from one whose bank is passing through the bar on its way to something else.
     *
     * @param techAvailable whether the structure can be planned at all: its prerequisite is
     *     finished and no copy is planned or standing
     * @param availableGas gas mined and not reserved by a queued plan
     * @param framesAtOrAboveBar frames the unreserved gas bank has held at or above
     *     {@link #BRANCH_GAS} without an observed dip
     * @return {@link Gate#TRIGGER} when the structure should be planned, else the first gate that
     *     is shut
     */
    public static Gate evaluate(boolean techAvailable, int availableGas, int framesAtOrAboveBar) {
        if (!techAvailable) {
            return Gate.TECH_UNAVAILABLE;
        }
        if (availableGas < BRANCH_GAS || framesAtOrAboveBar < SUSTAINED_FRAMES) {
            return Gate.GAS_SHORT;
        }
        return Gate.TRIGGER;
    }

    /**
     * Whether the build should plan the Hive-branch structure this frame.
     *
     * @return true when {@link #evaluate} opens every gate
     * @see #evaluate
     */
    public static boolean shouldPlan(boolean techAvailable, int availableGas, int framesAtOrAboveBar) {
        return evaluate(techAvailable, availableGas, framesAtOrAboveBar) == Gate.TRIGGER;
    }
}
