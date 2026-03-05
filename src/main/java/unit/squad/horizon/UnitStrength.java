package unit.squad.horizon;

import bwapi.UnitType;
import bwapi.WeaponType;

import java.util.HashMap;
import java.util.Map;

public class UnitStrength {

    private static final Map<UnitType, double[]> STRENGTH_TABLE = new HashMap<>();

    static {
        for (UnitType type : UnitType.values()) {
            if (type == UnitType.Unknown || type == UnitType.None) continue;
            double g2g = computeWeaponDps(type.groundWeapon(), type.maxGroundHits());
            double g2a = computeWeaponDps(type.airWeapon(), type.maxAirHits());
            double a2g = 0;
            double a2a = 0;
            if (type.isFlyer()) {
                a2g = g2g;
                a2a = g2a;
                g2g = 0;
                g2a = 0;
            }
            if (type == UnitType.Zerg_Lurker) {
                g2g *= 2.5;
            } else if (type == UnitType.Zerg_Mutalisk) {
                a2g *= 1.5;
                a2a *= 1.5;
            }
            STRENGTH_TABLE.put(type, new double[]{g2g, g2a, a2g, a2a});
        }

        STRENGTH_TABLE.put(UnitType.Zerg_Sunken_Colony, new double[]{6, 0, 0, 0});
        STRENGTH_TABLE.put(UnitType.Protoss_Photon_Cannon, new double[]{6, 6, 0, 0});
        STRENGTH_TABLE.put(UnitType.Terran_Bunker, new double[]{12, 12, 0, 0});
        STRENGTH_TABLE.put(UnitType.Zerg_Spore_Colony, new double[]{0, 2, 0, 0});
        STRENGTH_TABLE.put(UnitType.Terran_Missile_Turret, new double[]{0, 2, 0, 0});
    }

    private static double computeWeaponDps(WeaponType weapon, int maxHits) {
        if (weapon == null || weapon == WeaponType.None) return 0;
        int cooldown = weapon.damageCooldown();
        if (cooldown == 0) return 0;
        return (double) weapon.damageAmount() * weapon.damageFactor() * maxHits / cooldown;
    }

    public static double groundToGround(UnitType type) {
        double[] s = STRENGTH_TABLE.get(type);
        return s != null ? s[0] : 0;
    }

    public static double groundToAir(UnitType type) {
        double[] s = STRENGTH_TABLE.get(type);
        return s != null ? s[1] : 0;
    }

    public static double airToGround(UnitType type) {
        double[] s = STRENGTH_TABLE.get(type);
        return s != null ? s[2] : 0;
    }

    public static double airToAir(UnitType type) {
        double[] s = STRENGTH_TABLE.get(type);
        return s != null ? s[3] : 0;
    }

    public static double antiAirStrength(UnitType type) {
        double[] s = STRENGTH_TABLE.get(type);
        return s != null ? s[1] + s[3] : 0;
    }

    public static double totalStrength(UnitType type) {
        double[] s = STRENGTH_TABLE.get(type);
        return s != null ? s[0] + s[1] + s[2] + s[3] : 0;
    }
}
