package unit.squad.horizon;

import bwapi.DamageType;
import bwapi.UnitSizeType;
import bwapi.UnitType;
import bwapi.WeaponType;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Combat strength per unit type, split into the four attack domains.
 *
 * <p>Every entry comes from {@link #formulaStrength}, which is deliberately damage-type blind:
 * it scores raw weapon output and range only. Damage type is applied once, downstream, by
 * {@link HorizonCombatSimulator} against the size mix of the squad actually being simulated.
 *
 * <p>Three types keep a hand-picked literal instead:
 * <ul>
 *   <li>{@code Zerg_Sunken_Colony} and {@code Terran_Bunker} stand for a defended position rather
 *       than a lone building, and the Bunker has no weapon of its own for the formula to read.</li>
 *   <li>{@code Protoss_Photon_Cannon} is held above its formula value so air and ground squads
 *       both treat a cannon as a position to avoid.</li>
 * </ul>
 * Each literal sits at or above the formula value for the same type in every domain it scores, so
 * none of them can have been chosen with a damage-type discount already folded in.
 */
public class UnitStrength {

    private static final Map<UnitType, double[]> STRENGTH_TABLE = new HashMap<>();

    private static final Set<UnitType> HAND_TUNED = EnumSet.of(
            UnitType.Zerg_Sunken_Colony,
            UnitType.Protoss_Photon_Cannon,
            UnitType.Terran_Bunker);

    static {
        for (UnitType type : UnitType.values()) {
            if (type == UnitType.Unknown || type == UnitType.None) continue;
            STRENGTH_TABLE.put(type, formulaStrength(type));
        }

        STRENGTH_TABLE.put(UnitType.Zerg_Sunken_Colony, new double[]{6, 0, 0, 0});
        STRENGTH_TABLE.put(UnitType.Protoss_Photon_Cannon, new double[]{6, 6, 0, 0});
        STRENGTH_TABLE.put(UnitType.Terran_Bunker, new double[]{12, 12, 0, 0});
    }

    /**
     * Damage-type blind strength for a type, from weapon damage, cooldown and range.
     *
     * @param type unit type to score
     * @return a fresh array of {groundToGround, groundToAir, airToGround, airToAir}
     */
    static double[] formulaStrength(UnitType type) {
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
        } else if (type == UnitType.Zerg_Ultralisk) {
            g2g *= 2.0;
        } else if (type == UnitType.Zerg_Mutalisk) {
            a2g *= 1.5;
            a2a *= 1.5;
        }
        return new double[]{g2g, g2a, a2g, a2a};
    }

    /**
     * Whether a type's table entry is a hand-picked literal rather than the formula value.
     *
     * @param type unit type to test
     * @return true when the entry is a literal
     */
    static boolean isHandTuned(UnitType type) {
        return HAND_TUNED.contains(type);
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
