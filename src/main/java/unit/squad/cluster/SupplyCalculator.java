package unit.squad.cluster;

import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WeaponType;
import info.tracking.ObservedUnit;
import unit.managed.ManagedUnit;

import java.util.Collection;

import static util.Filter.isHostileBuilding;

public final class SupplyCalculator {

    private SupplyCalculator() {}

    public static SupplyBreakdown calculateEnemy(Collection<ObservedUnit> units) {
        double ground = 0;
        double rangedGround = 0;
        double airToGround = 0;
        double airToAir = 0;
        double antiAir = 0;

        for (ObservedUnit ou : units) {
            UnitType type = ou.getUnitType();
            double hpRatio = hpRatio(ou.getUnit());
            double supply = effectiveSupply(type) * hpRatio;

            if (type.isBuilding()) {
                if (isHostileBuilding(type)) {
                    double defenseSupply = staticDefenseSupply(type) * hpRatio;
                    ground += defenseSupply;
                    WeaponType airWeapon = type.airWeapon();
                    if (airWeapon != null && airWeapon != WeaponType.None) {
                        antiAir += defenseSupply;
                    }
                }
                continue;
            }

            WeaponType airWeapon = type.airWeapon();
            boolean canAttackAir = airWeapon != null && airWeapon != WeaponType.None;

            if (type.isFlyer()) {
                WeaponType groundWeapon = type.groundWeapon();
                if (groundWeapon != null && groundWeapon != WeaponType.None) {
                    airToGround += supply;
                }
                if (canAttackAir) {
                    airToAir += supply;
                    antiAir += supply;
                }
            } else {
                ground += supply;
                WeaponType groundWeapon = type.groundWeapon();
                if (groundWeapon != null && groundWeapon != WeaponType.None
                        && groundWeapon.maxRange() > 32) {
                    rangedGround += supply;
                }
                if (canAttackAir) {
                    antiAir += supply;
                }
            }
        }

        return new SupplyBreakdown(ground, rangedGround, airToGround, airToAir, antiAir);
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
        int maxHp = 3 * unit.getType().maxHitPoints() + unit.getType().maxShields();
        if (maxHp == 0) return 0;
        return (double) (3 * unit.getHitPoints() + unit.getShields()) / maxHp;
    }

    private static double effectiveSupply(UnitType type) {
        return type.supplyRequired();
    }

    private static double staticDefenseSupply(UnitType type) {
        if (type == UnitType.Terran_Bunker) return 12;
        if (type == UnitType.Protoss_Photon_Cannon) return 6;
        if (type == UnitType.Zerg_Sunken_Colony) return 6;
        if (type == UnitType.Zerg_Spore_Colony) return 2;
        if (type == UnitType.Terran_Missile_Turret) return 2;
        return 6;
    }
}
