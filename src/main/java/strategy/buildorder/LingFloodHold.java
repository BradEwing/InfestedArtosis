package strategy.buildorder;

import util.Time;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The response to a one base Zergling flood: a Zerg opponent on two hatcheries in its main,
 * spending its larva on Zerglings over a small worker count. The hold answers it with static
 * defense, a small Zergling guard and a Drone floor, so the bot out-drones an opponent that has
 * stopped at a handful of workers and techs past it, instead of mirroring its Zergling count on
 * fewer larva.
 *
 * <p>The reaction layer decides whether the hold stands; the build order layer reads these rules
 * for the sunken target, the Zergling cap and the Drone floor; the squad layer keeps ground squads
 * at home for as long as it stands.
 *
 * <p>The Drone floor is planned from the defense path, ahead of the active build's own plans, so it
 * overrides any worker cap the build keeps. The Zergling cap applies to the matchup target and the
 * early rush emergency; a build's own Zergling branch and the mineral surplus Zerglings are not
 * capped, and wait behind the floor Drones for larva.
 */
public final class LingFloodHold {

    /**
     * StrategyTracker names whose detection starts the hold: 9PoolMainHatch, which reads the flood from the in-main
     * Hatchery, and 2HatchLing, which reads it once the Zerglings have massed.
     */
    public static final List<String> TRIGGER_STRATEGIES = Collections.unmodifiableList(
            Arrays.asList("9PoolMainHatch", "2HatchLing"));

    /**
     * The last game time the hold stands. The flood's largest waves land before it, and the bot's
     * own tech is expected to have taken over from the static defense by then.
     */
    public static final Time DEADLINE = new Time(10, 0);

    /**
     * Sunkens per base the hold asks for, whether or not an attacker has been seen at the base.
     */
    public static final int SUNKENS = 4;

    /**
     * The most Zerglings the hold lets a build order or the early rush emergency ask for. They
     * guard the drones while the sunkens go up; the sunkens hold the base.
     */
    public static final int ZERGLINGS = 8;

    /**
     * Drones, alive, in an egg or planned, the hold plans up to. Twice the eight workers the flood
     * runs on.
     */
    public static final int DRONE_FLOOR = 16;

    /**
     * The priority a floor Drone is queued at: behind the emergency defense band and Metabolic
     * Boost, ahead of every Zergling a build order queues at its enqueue frame.
     */
    public static final int DRONE_PRIORITY = BuildOrder.EMERGENCY_DEFENSE_PRIORITY + 2;

    private LingFloodHold() {
    }

    /**
     * Whether the hold stands this frame.
     *
     * @param floodDetected whether any of {@link #TRIGGER_STRATEGIES} has been detected
     * @param gameTime current game time
     * @return true from the detection until {@link #DEADLINE}
     */
    public static boolean isActive(boolean floodDetected, Time gameTime) {
        return floodDetected && !gameTime.greaterThan(DEADLINE);
    }

    /**
     * The sunkens per base to plan up to.
     *
     * @param target sunkens per base asked for without the hold
     * @param holding whether the hold stands
     * @return the larger of the target and {@link #SUNKENS} while the hold stands, otherwise the target
     */
    public static int sunkenTarget(int target, boolean holding) {
        return holding ? Math.max(target, SUNKENS) : target;
    }

    /**
     * The Zergling target to plan up to.
     *
     * @param target the Zergling target asked for without the hold
     * @param holding whether the hold stands
     * @return the target, capped at {@link #ZERGLINGS} while the hold stands
     */
    public static int zerglingTarget(int target, boolean holding) {
        return holding ? Math.min(target, ZERGLINGS) : target;
    }

    /**
     * Whether the hold queues another floor Drone. The floor reads only the planned-worker limit,
     * not the expected-worker ceiling {@code GameState.canPlanDrone()} applies, which stops a single
     * base short of the floor against Zerg.
     *
     * @param holding whether the hold stands
     * @param committedDrones Drones alive, in an egg or planned
     * @param canPlanOpeningDrone whether fewer Drones are planned than the hatcheries' planned-worker limit
     * @return true while the hold stands, the floor is unmet and another Drone may be planned
     */
    public static boolean wantsDrone(boolean holding, int committedDrones, boolean canPlanOpeningDrone) {
        return holding && committedDrones < DRONE_FLOOR && canPlanOpeningDrone;
    }
}
