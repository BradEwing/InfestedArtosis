package macro;

import macro.plan.Plan;

/**
 * Rules that move drones between mineral patches and geysers. Inputs are raw banked totals and
 * the gas owed to queued and scheduled plans; reservations are excluded so scheduling a plan
 * cannot flip a rule.
 */
public final class GasBalance {

    static final int IDLE_GAS_MARGIN = 150;

    static final int FLOATING_MINERALS_MARGIN = 100;

    static final int RELEASE_INTERVAL_FRAMES = 240;

    private GasBalance() {
    }

    public static int gasDemand(Iterable<Plan> plans) {
        int demand = 0;
        for (Plan plan : plans) {
            demand += plan.gasPrice();
        }
        return demand;
    }

    /** True when the gas left after paying every waiting plan exceeds banked minerals by the margin. */
    public static boolean isGasIdle(int gas, int minerals, int gasDemand) {
        return gas - gasDemand - minerals > IDLE_GAS_MARGIN;
    }

    /** True when a waiting plan needs unbanked gas or minerals float past gas. */
    public static boolean wantsGasWorkers(int gas, int minerals, int gasDemand) {
        return gasDemand > gas || minerals - gas > FLOATING_MINERALS_MARGIN;
    }

    public static boolean isReleaseDue(int frame, int lastReleaseFrame) {
        return frame - lastReleaseFrame >= RELEASE_INTERVAL_FRAMES;
    }
}
