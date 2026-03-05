package unit.squad.cluster;

import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WeaponType;
import unit.managed.ManagedUnit;

import java.util.Collection;

import static util.Filter.isHostileBuilding;

public final class SupplyCalculator {

    private static final int STATIC_DEFENSE_SUPPLY_EQUIVALENT = 6;

    private SupplyCalculator() {}

    public static SupplyBreakdown calculateEnemy(Collection<Unit> units) {
        double ground = 0;
        double rangedGround = 0;
        double airToGround = 0;
        double airToAir = 0;

        for (Unit unit : units) {
            UnitType type = unit.getType();
            double hpRatio = hpRatio(unit);
            double supply = effectiveSupply(type) * hpRatio;

            if (type.isBuilding()) {
                if (isHostileBuilding(type)) {
                    ground += STATIC_DEFENSE_SUPPLY_EQUIVALENT * hpRatio;
                }
                continue;
            }

            if (type.isFlyer()) {
                WeaponType groundWeapon = type.groundWeapon();
                if (groundWeapon != null && groundWeapon != WeaponType.None) {
                    airToGround += supply;
                }
                WeaponType airWeapon = type.airWeapon();
                if (airWeapon != null && airWeapon != WeaponType.None) {
                    airToAir += supply;
                }
            } else {
                ground += supply;
                WeaponType groundWeapon = type.groundWeapon();
                if (groundWeapon != null && groundWeapon != WeaponType.None
                        && groundWeapon.maxRange() > 32) {
                    rangedGround += supply;
                }
            }
        }

        return new SupplyBreakdown(ground, rangedGround, airToGround, airToAir);
    }

    public static SupplyBreakdown calculateFriendly(Collection<ManagedUnit> units) {
        double ground = 0;
        double rangedGround = 0;
        double airToGround = 0;
        double airToAir = 0;

        for (ManagedUnit mu : units) {
            Unit unit = mu.getUnit();
            UnitType type = unit.getType();
            double hpRatio = hpRatio(unit);
            double supply = effectiveSupply(type) * hpRatio;

            if (type.isFlyer()) {
                WeaponType groundWeapon = type.groundWeapon();
                if (groundWeapon != null && groundWeapon != WeaponType.None) {
                    airToGround += supply;
                }
                WeaponType airWeapon = type.airWeapon();
                if (airWeapon != null && airWeapon != WeaponType.None) {
                    airToAir += supply;
                }
            } else {
                ground += supply;
                WeaponType groundWeapon = type.groundWeapon();
                if (groundWeapon != null && groundWeapon != WeaponType.None
                        && groundWeapon.maxRange() > 32) {
                    rangedGround += supply;
                }
            }
        }

        return new SupplyBreakdown(ground, rangedGround, airToGround, airToAir);
    }

    private static double hpRatio(Unit unit) {
        int maxHp = unit.getType().maxHitPoints() + unit.getType().maxShields();
        if (maxHp == 0) return 0;
        return (double) (unit.getHitPoints() + unit.getShields()) / maxHp;
    }

    private static double effectiveSupply(UnitType type) {
        return type.supplyRequired();
    }
}
