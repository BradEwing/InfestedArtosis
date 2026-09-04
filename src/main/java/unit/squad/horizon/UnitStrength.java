package unit.squad.horizon;

import bwapi.DamageType;
import bwapi.UnitSizeType;
import bwapi.UnitType;
import bwapi.WeaponType;

import java.util.HashMap;
import java.util.Map;

public class UnitStrength {

    /**
     * Durability anchor for the effective hit point term, in hit points. Dividing by it before the
     * square root leaves a light ground unit close to the value it carried before the term existed,
     * so the absolute static defense entries below and the simulator's engage thresholds stay on
     * the scale they were tuned against.
     */
    private static final double EHP_REFERENCE = 40.0;

    private static final Map<UnitType, double[]> STRENGTH_TABLE = new HashMap<>();

    static {
        for (UnitType type : UnitType.values()) {
            if (type == UnitType.Unknown || type == UnitType.None) continue;
            double g2g = computeWeaponDps(type.groundWeapon(), type.maxGroundHits());
            double g2a = computeWeaponDps(type.airWeapon(), type.maxAirHits());
            double groundRange = type.groundWeapon() != null ? type.groundWeapon().maxRange() : 0;
            double airRange = type.airWeapon() != null ? type.airWeapon().maxRange() : 0;
            double groundRangeFactor = groundRange > 0 ? Math.log(groundRange / 4.0 + 16.0) : 1.0;
            double airRangeFactor = airRange > 0 ? Math.log(airRange / 4.0 + 16.0) : 1.0;
            g2g *= groundRangeFactor;
            g2a *= airRangeFactor;
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
            double ehp = effectiveHitPointFactor(type);
            STRENGTH_TABLE.put(type, new double[]{g2g * ehp, g2a * ehp, a2g * ehp, a2a * ehp});
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

    /**
     * Durability multiplier for a unit type: the square root of its maximum hit points plus shields,
     * relative to {@link #EHP_REFERENCE}.
     *
     * <p>Two forces trade evenly under Lanchester's square law when their sums of
     * {@code sqrt(dps * effectiveHitPoints)} match, so durability belongs in a per-unit strength
     * under a square root rather than as a straight product. The damage half is deliberately left
     * linear: every correction the simulator layers on top of this table - attack upgrades, Adrenal
     * Glands, the current-health weighting, the worker divisor - is linear in damage, and taking the
     * root of that half as well would misprice all of them. Shields count towards the pool at face
     * value because they must be removed before hit points can be.
     */
    private static double effectiveHitPointFactor(UnitType type) {
        double effectiveHitPoints = type.maxHitPoints() + type.maxShields();
        if (effectiveHitPoints <= 0) return 1.0;
        return Math.sqrt(effectiveHitPoints / EHP_REFERENCE);
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

    public static double effectiveness(DamageType damageType, UnitSizeType targetSize) {
        if (damageType == DamageType.Explosive) {
            if (targetSize == UnitSizeType.Small) return 0.5;
            if (targetSize == UnitSizeType.Medium) return 0.75;
        } else if (damageType == DamageType.Concussive) {
            if (targetSize == UnitSizeType.Medium) return 0.5;
            if (targetSize == UnitSizeType.Large) return 0.25;
        }
        return 1.0;
    }
}
