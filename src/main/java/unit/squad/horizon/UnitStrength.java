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
 * it scores weapon output, range and durability only. Damage type is applied once, downstream, by
 * {@link HorizonCombatSimulator} against the size mix of the squad actually being simulated.
 *
 * <p>Durability enters through {@link #durabilityFactor}, the square root of a type's maximum hit
 * point and shield pool, multiplying all four domains. It carries no reference or anchor constant.
 * An anchor would be a single factor common to every formula entry, so it would cancel exactly from
 * the friendly over enemy ratio {@link HorizonCombatSimulator#selectResult} compares against the
 * engage threshold, and could not change a verdict. This factor is per type and therefore survives
 * that ratio: it moves a marine against a zergling, a zealot against a hydralisk and a sunken
 * against either, by different amounts.
 *
 * <p>Three types keep a hand-picked literal instead:
 * <ul>
 *   <li>{@code Zerg_Sunken_Colony} and {@code Terran_Bunker} stand for a defended position rather
 *       than a lone building, and the Bunker has no weapon of its own for the formula to read.</li>
 *   <li>{@code Protoss_Photon_Cannon} is held above its formula value so air and ground squads
 *       both treat a cannon as a position to avoid.</li>
 * </ul>
 * Each literal is written on the pre-durability scale and carried onto the new one by the same
 * {@link #durabilityFactor} the formula entries take, so every literal keeps exactly the multiple of
 * its own type's formula value that it held before durability was priced. That keeps each literal at
 * or above the formula value in every domain it scores, so none of them can have been chosen with a
 * damage-type discount already folded in.
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

        STRENGTH_TABLE.put(UnitType.Zerg_Sunken_Colony,
                handTunedEntry(UnitType.Zerg_Sunken_Colony, 6, 0, 0, 0));
        STRENGTH_TABLE.put(UnitType.Protoss_Photon_Cannon,
                handTunedEntry(UnitType.Protoss_Photon_Cannon, 6, 6, 0, 0));
        STRENGTH_TABLE.put(UnitType.Terran_Bunker,
                handTunedEntry(UnitType.Terran_Bunker, 12, 12, 0, 0));
    }

    /**
     * Damage-type blind strength for a type, from weapon damage, cooldown, range and durability.
     *
     * <p>The Lurker and Mutalisk multipliers describe attack shape, splash and attack pattern, and
     * are untouched by durability. The Ultralisk multiplier is gone: it stood in for the missing hit
     * point term and would now be counted twice.
     *
     * @param type unit type to score
     * @return a fresh array of groundToGround, groundToAir, airToGround, airToAir
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
        } else if (type == UnitType.Zerg_Mutalisk) {
            a2g *= 1.5;
            a2a *= 1.5;
        }
        double durability = durabilityFactor(type);
        return new double[]{g2g * durability, g2a * durability, a2g * durability, a2a * durability};
    }

    /**
     * How much of a type's weapon output its hit points escort into a fight.
     *
     * <p>The square root of the pool, not the pool itself: doubling a hit point pool buys well under
     * twice the damage delivered, because the unit is under fire for the whole of the extra time it
     * survives. Rooting it also puts the term on the same shape as
     * {@code HorizonCombatSimulator.hpWeighting}, which prices the fraction of that pool a unit has
     * left.
     *
     * <p>A type with no hit point pool at all scores 1.0 rather than 0. The pool is zero only for
     * turret sub-units such as {@code Terran_Goliath_Turret}, which carry a real weapon on a type
     * with no health of its own; zeroing them would invent a new unscored attacker.
     *
     * @param type unit type to score
     * @return the durability multiplier for that type
     */
    static double durabilityFactor(UnitType type) {
        int pool = type.maxHitPoints() + type.maxShields();
        if (pool <= 0) return 1.0;
        return Math.sqrt(pool);
    }

    /**
     * Carries a hand-picked literal, written on the pre-durability scale, onto the durability scale.
     *
     * @param type unit type the literal belongs to
     * @param domains the literal's four domain values before durability
     * @return a fresh array of the four domain values after durability
     */
    private static double[] handTunedEntry(UnitType type, double... domains) {
        double durability = durabilityFactor(type);
        double[] entry = new double[domains.length];
        for (int i = 0; i < domains.length; i++) {
            entry[i] = domains[i] * durability;
        }
        return entry;
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
