package util;

import bwapi.Position;
import bwapi.Unit;

/**
 * The bot's estimate of how long a unit needs to reach somewhere on foot.
 *
 * <p>One formula, shared by the plan manager that decides when to dispatch a builder and by the
 * build-ahead slot that decides how long to wait for one. They have to agree: a deadline shorter
 * than the walk it is timing evicts plans whose builder is still en route.
 */
public final class TravelTime {

    /** Covers pathing detours, collisions with other units and settling onto the build tile. */
    public static final int PATHING_BUFFER_FRAMES = 250;

    private TravelTime() {}

    public static int framesToReach(Unit unit, Position target) {
        return framesToReach(unit.getPosition().getDistance(target), unit.getType().topSpeed());
    }

    public static int framesToReach(double distance, double topSpeed) {
        if (topSpeed <= 0) {
            return PATHING_BUFFER_FRAMES;
        }
        return (int) (distance / topSpeed) + PATHING_BUFFER_FRAMES;
    }
}
