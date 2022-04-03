package util;

import bwapi.Unit;

import java.util.List;

public final class Filter {

    public static Unit closestUnit(Unit unit, List<Unit> unitList) {
        if (unitList.size() == 1) {
            return unitList.get(0);
        }
        Unit closestUnit = null;
        int closestDistance = Integer.MAX_VALUE;
        for (Unit u : unitList) {
            int distance = unit.getDistance(u);
            if (distance < closestDistance) {
                closestUnit = u;
                closestDistance = distance;
            }
        }

        return closestUnit;
    }
}
