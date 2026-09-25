package macro;

import bwapi.UnitType;
import lombok.Getter;
import macro.plan.Plan;
import macro.plan.UnitPlan;

/**
 * A window in which Drones go ahead of the advanced unit stream.
 *
 * <p>Advanced units queue at {@link UnitPlan#ADVANCED_UNIT_PRIORITY}, ahead of every Drone plan
 * numbered by the frame it was derived on, and a blocked one claims the next larva against the
 * plans behind it. Once the build's army reaches a milestone of living units, a round opens. While
 * it is open the build withholds new advanced unit plans, queues Drones at
 * {@link UnitPlan#DRONE_ROUND_PRIORITY}, and a queued advanced unit no longer claims larva against
 * the plans behind it.
 *
 * <p>A round opens only while the worker gates still want Drones. It closes once
 * {@link #DRONES_PER_ROUND} more Drones are hatched or in an egg, once the build's Drone cap is met,
 * once the worker gates stop wanting Drones, or after {@link #MAX_ROUND_FRAMES}. The next milestone is then
 * {@link #ARMY_UNITS_PER_ROUND} living army units past the count the round closed on. A threat
 * closes an open round without moving the milestone, so the round reopens once the threat clears,
 * and no round opens while one is present.
 */
public class DroneRound {

    /** Living army units of the build's target types that open the first round. Tuning constant. */
    public static final int FIRST_ROUND_ARMY_UNITS = 6;

    /** Living army units past the last round's close that open the next round. Tuning constant. */
    public static final int ARMY_UNITS_PER_ROUND = 6;

    /** Drones a round adds before it closes. Tuning constant. */
    public static final int DRONES_PER_ROUND = 4;

    /** Longest a round withholds the advanced unit stream, in frames. Tuning constant. */
    public static final int MAX_ROUND_FRAMES = 1440;

    @Getter
    private boolean active = false;

    @Getter
    private int armyMilestone = FIRST_ROUND_ARMY_UNITS;

    @Getter
    private int droneTarget = 0;

    private int startFrame = 0;

    /**
     * Opens or closes the round for this frame.
     *
     * @param frame the current frame
     * @param livingArmy living units of the build's target army types
     * @param drones Drones hatched plus Drones in an egg
     * @param droneCap the build's Drone target; zero for a build that runs no rounds
     * @param workersWanted whether the worker count is still below what the bases can use
     * @param threatened whether a threat must put the army first
     */
    public void update(int frame, int livingArmy, int drones, int droneCap, boolean workersWanted,
                       boolean threatened) {
        if (active) {
            if (threatened) {
                active = false;
                return;
            }
            if (!workersWanted || drones >= Math.min(droneTarget, droneCap)
                    || frame - startFrame >= MAX_ROUND_FRAMES) {
                active = false;
                armyMilestone = livingArmy + ARMY_UNITS_PER_ROUND;
            }
            return;
        }
        if (threatened || !workersWanted || livingArmy < armyMilestone || drones >= droneCap) {
            return;
        }
        active = true;
        startFrame = frame;
        droneTarget = Math.min(drones + DRONES_PER_ROUND, droneCap);
    }

    /**
     * Whether a threat must close a round and keep the next one shut.
     *
     * @param rushed an early, cannon or SCV rush is detected
     * @param allIn the bot has committed to an all-in
     * @param visibleEnemiesAtBases enemy ground or air combat units visible at our bases
     * @return true when the army must come first
     */
    public static boolean isThreatened(boolean rushed, boolean allIn, int visibleEnemiesAtBases) {
        return rushed || allIn || visibleEnemiesAtBases > 0;
    }

    /**
     * Whether a plan is a Drone the round queued ahead of the advanced unit stream.
     *
     * @param plan a queued plan
     * @return true for a Drone plan at {@link UnitPlan#DRONE_ROUND_PRIORITY}
     */
    public static boolean isRoundDrone(Plan plan) {
        return plan.getPriority() == UnitPlan.DRONE_ROUND_PRIORITY
                && plan.getPlannedUnit() == UnitType.Zerg_Drone;
    }
}
