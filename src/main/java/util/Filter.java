package util;

import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WeaponType;

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

    public static Unit closestHostileUnit(Position p, List<Unit> unitList) {
        if (unitList.size() == 1) {
            return unitList.get(0);
        }

        boolean hasSeenEnemyOrHostileBuilding = false;
        Unit closestUnit = null;
        double closestDistance = Double.MAX_VALUE;
        for (Unit u : unitList) {
            UnitType type = u.getType();
            boolean isHostileBuilding = isHostileBuilding(type);
            if (!hasSeenEnemyOrHostileBuilding && (!type.isBuilding() || isHostileBuilding)) {
                hasSeenEnemyOrHostileBuilding = true;
            }
            if (hasSeenEnemyOrHostileBuilding && type.isBuilding() && !isHostileBuilding) {
                continue;
            }
            double distance = p.getDistance(u.getPosition());
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

    public static boolean isHostileBuildingToGround(UnitType unitType) {
        if (!unitType.isBuilding()) {
            return false;
        }

        if (unitType == UnitType.Terran_Bunker || unitType == UnitType.Zerg_Sunken_Colony || unitType == UnitType.Protoss_Photon_Cannon) {
            return true;
        }

        return false;
    }

    /**
     * Checks if a unit type should be excluded from combat targeting.
     */
    public static boolean isLowPriorityCombatTarget(UnitType unitType) {
        return unitType == UnitType.Zerg_Larva ||
               unitType == UnitType.Zerg_Lurker_Egg ||
               unitType == UnitType.Zerg_Cocoon ||
               unitType == UnitType.Zerg_Egg;
    }

    /**
     * Returns true if the given type can threaten air (units with an air weapon, or static AA).
     */
    public static boolean isAirThreat(UnitType unitType) {
        if (unitType.isBuilding()) {
            return unitType == UnitType.Terran_Missile_Turret ||
                    unitType == UnitType.Zerg_Spore_Colony ||
                    unitType == UnitType.Protoss_Photon_Cannon;
        }
        WeaponType airWeapon = unitType.airWeapon();
        return airWeapon != null && airWeapon != WeaponType.None;
    }

    /**
     * Returns true if the given type can threaten ground (units with a ground weapon, or hostile ground buildings).
     */
    public static boolean isGroundThreat(UnitType unitType) {
        if (unitType.isBuilding()) {
            return isHostileBuildingToGround(unitType);
        }
        WeaponType groundWeapon = unitType.groundWeapon();
        return groundWeapon != null && groundWeapon != WeaponType.None;
    }

    public static boolean isWorkerType(UnitType type) {
        return type == UnitType.Zerg_Drone || type == UnitType.Terran_SCV || type == UnitType.Protoss_Probe;
    }

    public static boolean isMeanWorker(Unit unit) {
        if (unit == null) {
            return false;
        }
        final boolean isMeanWorker = isWorkerType(unit.getType());
        return isMeanWorker && unit.isAttacking();
    }
}
