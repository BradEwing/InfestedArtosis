package macro;

import macro.plan.Plan;

/**
 * Rules that move drones between mineral patches and geysers.
 *
 * <p>Every input is a raw banked total or a sum over waiting plans. Reservations are excluded
 * on purpose: a reservation moves when a plan is scheduled, so a rule that read one could pull a
 * gas worker on the frame a mineral-only building is scheduled. Gas demand covers plans still
 * in the queue as well as scheduled plans, so gas a queued plan is waiting for never reads as
 * surplus.
 */
public final class GasBalance {

    static final int IDLE_GAS_MARGIN = 150;

    static final int FLOATING_MINERALS_MARGIN = 100;

    static final int RELEASE_INTERVAL_FRAMES = 240;

    private GasBalance() {
    }

    /**
     * Gas the given plans will spend once they start. Queued plans have reserved nothing and
     * scheduled plans have not yet paid, so both still draw on the bank.
     */
    public static int gasDemand(Iterable<Plan> plans) {
        int demand = 0;
        for (Plan plan : plans) {
            demand += plan.gasPrice();
        }
        return demand;
    }

    /**
     * True when gas is idle: once every waiting plan is paid, the gas left over still exceeds
     * banked minerals by the margin. A waiting plan that needs more gas than is banked makes
     * this false whatever the mineral count.
     *
     * @param gas gas mined and unspent, before any reservation
     * @param minerals minerals mined and unspent, before any reservation
     * @param gasDemand gas owed to queued and scheduled plans
     */
    public static boolean isGasIdle(int gas, int minerals, int gasDemand) {
        return gas - gasDemand - minerals > IDLE_GAS_MARGIN;
    }

    /**
     * True when the geysers should be refilled: a waiting plan needs gas that is not banked, or
     * minerals are floating past gas. The first clause refills independent of minerals, so a
     * geyser emptied while gas was idle refills as soon as demand returns.
     */
    public static boolean wantsGasWorkers(int gas, int minerals, int gasDemand) {
        return gasDemand > gas || minerals - gas > FLOATING_MINERALS_MARGIN;
    }

    /**
     * True when enough frames have passed since the last release to move another drone off gas.
     * One release per interval drains a geyser gradually instead of emptying it in one frame.
     */
    public static boolean isReleaseDue(int frame, int lastReleaseFrame) {
        return frame - lastReleaseFrame >= RELEASE_INTERVAL_FRAMES;
    }
}
