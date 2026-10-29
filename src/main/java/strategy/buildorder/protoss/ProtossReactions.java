package strategy.buildorder.protoss;

import bwapi.UnitType;
import util.Time;

/**
 * The Protoss matchup's sunken and zergling targets as pure functions of what was observed, so a
 * build order that does not extend {@link ProtossBase} reads the same numbers the matchup builds do.
 */
public final class ProtossReactions {

    static final int MAX_ZEALOT_DEMAND = 25;

    static final int ZERGLINGS_PER_GATEWAY = 6;

    private static final int EXCESS_MINERALS = 350;

    private static final int CANNON_ZERGLINGS_BASE = 8;

    private static final int CANNON_ZERGLINGS_PER_CANNON = 3;

    private static final int TWO_GATE_ZERGLINGS = 12;

    private static final int ONE_GATE_CORE_ZERGLINGS = 4;

    private static final int FFE_ZERGLINGS = 2;

    private static final int DEFAULT_ZERGLINGS = 6;

    private static final int ZEALOTS_FOR_SECOND_SUNKEN = 3;

    private static final int ZEALOTS_FOR_THIRD_SUNKEN = 6;

    private static final int LATE_SUNKEN_DRONES = 20;

    private static final Time TWO_GATE_SUNKEN_OPENS = new Time(3, 20);

    private static final Time LATE_SUNKEN_OPENS = new Time(10, 0);

    private ProtossReactions() {
    }

    /**
     * Zerglings owed to the Zealots seen: two per Zealot, capped at six per Gateway seen and never
     * above {@link #MAX_ZEALOT_DEMAND}.
     *
     * @param zealots Zealots seen
     * @param gateways Gateways seen alive, zero when none was seen
     */
    public static int zealotDrivenZerglings(int zealots, int gateways) {
        int cap = MAX_ZEALOT_DEMAND;
        if (gateways > 0) {
            cap = Math.min(ZERGLINGS_PER_GATEWAY * gateways, MAX_ZEALOT_DEMAND);
        }
        return Math.min(zealots * 2, cap);
    }

    /**
     * Sunkens per base the Protoss matchup asks for.
     *
     * <p>One if CannonRush is detected, and nothing else. Otherwise the sum of:
     * <ul>
     *     <li>one if 2Gate or ProxyGate is detected and a Zealot has been seen or the game is past 3:20;</li>
     *     <li>one if more than 3 Zealots are seen, and another if more than 6;</li>
     *     <li>one past 10:00 with more than 20 drones.</li>
     * </ul>
     *
     * @param cannonRush whether CannonRush is detected
     * @param twoGateOrProxyGate whether 2Gate or ProxyGate is detected
     * @param zealots Zealots seen
     * @param gameTime the game clock
     * @param drones our drones
     */
    public static int matchupSunkens(boolean cannonRush, boolean twoGateOrProxyGate, int zealots, Time gameTime,
                                     int drones) {
        if (cannonRush) {
            return 1;
        }
        int sunkens = 0;
        if (twoGateOrProxyGate && (zealots > 0 || gameTime.greaterThan(TWO_GATE_SUNKEN_OPENS))) {
            sunkens += 1;
        }
        if (zealots > ZEALOTS_FOR_SECOND_SUNKEN) {
            sunkens += 1;
        }
        if (zealots > ZEALOTS_FOR_THIRD_SUNKEN) {
            sunkens += 1;
        }
        if (gameTime.greaterThan(LATE_SUNKEN_OPENS) && drones > LATE_SUNKEN_DRONES) {
            sunkens += 1;
        }
        return sunkens;
    }

    /**
     * The zergling total the Protoss matchup holds against what was seen: the base for the first
     * detected strategy in the order CannonRush, 2Gate or ProxyGate, 1GateCore and FFE, plus
     * {@link #zealotDrivenZerglings}.
     *
     * @param cannonRush whether CannonRush is detected
     * @param cannons Photon Cannons seen alive
     * @param availableMinerals minerals not reserved by a queued plan
     * @param twoGateOrProxyGate whether 2Gate or ProxyGate is detected
     * @param oneGateCore whether 1GateCore is detected
     * @param fastExpand whether FFE is detected
     * @param zealots Zealots seen
     * @param gateways Gateways seen alive
     */
    public static int zerglingTarget(boolean cannonRush, int cannons, int availableMinerals,
                                     boolean twoGateOrProxyGate, boolean oneGateCore, boolean fastExpand,
                                     int zealots, int gateways) {
        int zerglings = DEFAULT_ZERGLINGS;
        if (cannonRush) {
            zerglings = CANNON_ZERGLINGS_BASE + cannons * CANNON_ZERGLINGS_PER_CANNON;
            if (availableMinerals > EXCESS_MINERALS) {
                zerglings += availableMinerals % UnitType.Zerg_Zergling.mineralPrice();
            }
        } else if (twoGateOrProxyGate) {
            zerglings = TWO_GATE_ZERGLINGS;
        } else if (oneGateCore) {
            zerglings = ONE_GATE_CORE_ZERGLINGS;
        } else if (fastExpand) {
            zerglings = FFE_ZERGLINGS;
        }
        return zerglings + zealotDrivenZerglings(zealots, gateways);
    }
}
