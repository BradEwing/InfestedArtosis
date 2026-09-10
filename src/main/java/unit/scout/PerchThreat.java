package unit.scout;

import bwapi.UnitType;
import info.map.PerchCalculator;
import util.Filter;
import util.Time;

/**
 * Pure predicate deciding whether an enemy unit's presence should recall a perched overlord.
 * Nothing here touches {@code Game}; every input is a {@code UnitType} and a distance already
 * measured by the caller.
 */
public final class PerchThreat {

    /**
     * How far ahead the leave predicate looks. A reaction budget, not a game constant: an attacker
     * faster than the overlord is treated as already in reach of everything it can cover in this
     * span, so the overlord leaves before the shot rather than after it.
     */
    public static final int REACTION_FRAMES = new Time(0, 1).getFrames();

    private PerchThreat() {
    }

    /**
     * The distance at which an enemy of the given type should recall a perched overlord: its air
     * reach plus the ground it closes over {@link #REACTION_FRAMES} while the overlord flees. An
     * attacker no faster than the overlord closes nothing, so its leave distance is its bare reach.
     *
     * @param type the enemy unit's type
     * @return the leave distance in pixels
     */
    public static double leaveDistancePixels(UnitType type) {
        double closingSpeed = type.topSpeed() - UnitType.Zerg_Overlord.topSpeed();
        if (closingSpeed <= 0) {
            return PerchCalculator.reachPixels(type);
        }
        return PerchCalculator.reachPixels(type) + closingSpeed * REACTION_FRAMES;
    }

    /**
     * Whether an enemy unit of the given type, at the given distance from a perched overlord,
     * should recall it.
     *
     * @param type the enemy unit's type
     * @param distancePixels distance in pixels from the perched overlord
     * @return true if the overlord should be recalled from its perch
     */
    public static boolean threatens(UnitType type, double distancePixels) {
        if (type.isFlyer() && Filter.isAirThreat(type)) {
            return true;
        }
        if (type == UnitType.Zerg_Spire || type == UnitType.Zerg_Hydralisk_Den
                || type == UnitType.Protoss_Stargate || type == UnitType.Terran_Starport) {
            return true;
        }
        return Filter.isAirThreat(type) && distancePixels <= leaveDistancePixels(type);
    }
}
