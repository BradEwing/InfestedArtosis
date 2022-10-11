package util;

import bwapi.Unit;
import bwapi.UnitType;

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

    public static Unit closestHostileUnit(Unit unit, List<Unit> unitList) {
        if (unitList.size() == 1) {
            return unitList.get(0);
        }

        boolean hasSeenEnemyOrHostileBuilding = false;
        Unit closestUnit = null;
        int closestDistance = Integer.MAX_VALUE;
        for (Unit u : unitList) {
            UnitType type = u.getType();
            boolean isHostileBuilding = isHostileBuilding(type);
            if (!hasSeenEnemyOrHostileBuilding && (!type.isBuilding() || isHostileBuilding)) {
                hasSeenEnemyOrHostileBuilding = true;
            }
            if (hasSeenEnemyOrHostileBuilding && type.isBuilding() && !isHostileBuilding) {
                continue;
            }
            int distance = unit.getDistance(u.getPosition());
            if (distance < closestDistance) {
                closestUnit = u;
                closestDistance = distance;
            }
        }

        if (closestUnit == null) {
            return null;
        }

        return closestUnit;
    }

    public static boolean isHostileBuilding(UnitType unitType) {
        if (!unitType.isBuilding()) {
            return false;
        }

        if (unitType == UnitType.Terran_Bunker || unitType == UnitType.Terran_Missile_Turret || unitType == UnitType.Zerg_Sunken_Colony ||
        unitType == UnitType.Zerg_Spore_Colony || unitType == UnitType.Protoss_Photon_Cannon) {
            return true;
        }

        return false;
    }
}
