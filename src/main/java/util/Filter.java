package util;

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

        Unit closestCombat = null;
        int closestCombatDistance = Integer.MAX_VALUE;
        Unit closestBuilding = null;
        int closestBuildingDistance = Integer.MAX_VALUE;

        for (Unit u : unitList) {
            UnitType type = u.getType();
            int distance = unit.getDistance(u.getPosition());
            if (!type.isBuilding() || isHostileBuilding(type)) {
                if (distance < closestCombatDistance) {
                    closestCombat = u;
                    closestCombatDistance = distance;
                }
            } else {
                if (distance < closestBuildingDistance) {
                    closestBuilding = u;
                    closestBuildingDistance = distance;
                }
            }
        }

        return closestCombat != null ? closestCombat : closestBuilding;
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
        final boolean isWorker = isWorkerType(unit.getType());
        return isWorker && unit.isAttacking();
    }
}
