package strategy.buildorder;

import bwapi.UnitType;
import macro.plan.Plan;
import macro.plan.PlanType;
import macro.plan.SpeedlingReaction;

import java.util.List;

/**
 * The rules SpeedlingAllIn adds against a Protoss opponent, as pure functions of their inputs so
 * any speedling build can call them.
 */
public final class SpeedlingReactions {

    /**
     * Frames the drone floor stays suspended after the last enemy ground combat unit was visible at
     * one of our bases, so a unit stepping out of vision for a moment does not release the floor.
     */
    static final int DRONE_FLOOR_LINGER_FRAMES = 120;

    private SpeedlingReactions() {
    }

    /**
     * Whether the build is larva bound with its bank piling up: no larva is free, zergling plans are
     * queued waiting on larva, the mined bank is at the bar, and no hatchery is already on the way.
     *
     * <p>The bank is the mined one, reservations ignored. Queued zergling plans reserve it while they
     * wait, so the unreserved bank stays low exactly when larva is the limit.
     *
     * @param larva larva not yet handed to a plan
     * @param queuedZerglingPlans zergling plans waiting in the production queue
     * @param minedMinerals minerals mined and unspent, ignoring reservations
     * @param unfinishedHatcheries hatchery plans in flight plus hatcheries under construction
     * @param mineralBar the bank that counts as piling up
     */
    static boolean isLarvaBound(int larva, int queuedZerglingPlans, int minedMinerals, int unfinishedHatcheries,
                                int mineralBar) {
        return larva <= 0 && queuedZerglingPlans > 0 && minedMinerals >= mineralBar && unfinishedHatcheries == 0;
    }

    /**
     * Whether the drone floor is suspended: an enemy ground combat unit is visible at our bases, or
     * was within {@link #DRONE_FLOOR_LINGER_FRAMES}.
     *
     * @param visibleEnemiesAtBases enemy mobile ground combat units visible at our bases now
     * @param framesSinceLastVisible frames since one last was, or a large number when none has been
     */
    static boolean droneFloorSuspended(int visibleEnemiesAtBases, int framesSinceLastVisible) {
        return visibleEnemiesAtBases > 0 || framesSinceLastVisible < DRONE_FLOOR_LINGER_FRAMES;
    }

    /**
     * Marks every creep colony and sunken colony plan in the list as created by the Protoss sunken
     * reaction.
     */
    static void tagSunkenPlans(List<Plan> plans) {
        for (Plan plan : plans) {
            if (plan.getType() == PlanType.BUILDING && isColony(plan.getPlannedUnit())) {
                plan.setSpeedlingReaction(SpeedlingReaction.SPEEDLING_P_SUNKEN);
            }
        }
    }

    private static boolean isColony(UnitType unitType) {
        return unitType == UnitType.Zerg_Creep_Colony || unitType == UnitType.Zerg_Sunken_Colony;
    }
}
