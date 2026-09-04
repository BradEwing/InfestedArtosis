package unit.scout;

import bwapi.UnitType;
import info.map.PerchCalculator;
import util.Filter;

/**
 * Pure predicate deciding whether an enemy unit's presence should recall a perched overlord.
 * Nothing here touches {@code Game}; every input is a {@code UnitType} and a distance already
 * measured by the caller.
 */
public final class PerchThreat {

    private PerchThreat() {
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
        if (type == UnitType.Zerg_Spire || type == UnitType.Protoss_Stargate || type == UnitType.Terran_Starport) {
            return true;
        }
        return Filter.isAirThreat(type) && distancePixels <= PerchCalculator.reachPixels(type);
    }
}
