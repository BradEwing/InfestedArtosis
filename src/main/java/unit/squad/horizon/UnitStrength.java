package unit.squad.horizon;

import bwapi.DamageType;
import bwapi.UnitSizeType;
import bwapi.UnitType;
import bwapi.WeaponType;

import java.util.HashMap;
import java.util.Map;

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
 * <p>Static defence carries no hand-picked literal. The Sunken Colony and Photon Cannon score their
 * formula value like any other armed type. The Bunker has no weapon of its own, so
 * {@link #garrisonStrength} prices it as the Marines a full Bunker holds, firing at their own range,
 * behind the Bunker's hit point pool rather than their own. {@link HorizonCombatSimulator} then scales
 * that by the garrison it has observed.
 */
public class UnitStrength {

    private static final Map<UnitType, double[]> STRENGTH_TABLE = new HashMap<>();

    static {
        for (UnitType type : UnitType.values()) {
            if (type == UnitType.Unknown || type == UnitType.None) continue;
            STRENGTH_TABLE.put(type, formulaStrength(type));
        }
    }

    /**
     * Damage-type blind strength for a type, from weapon damage, cooldown, range and durability.
     *
     * <p>The Lurker and Mutalisk multipliers describe attack shape, splash and attack pattern, and
     * are untouched by durability. The Ultralisk multiplier is gone: it stood in for the missing hit
     * point term and would now be counted twice.
     *
     * <p>A Bunker scores its garrison, see {@link #garrisonStrength}.
     *
     * @param type unit type to score
     * @return a fresh array of groundToGround, groundToAir, airToGround, airToAir
     */
    static double[] formulaStrength(UnitType type) {
        if (type == UnitType.Terran_Bunker) return garrisonStrength(type);
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
     * Strength of a full Bunker: the Marines it holds, each firing at its own formula value, escorted
     * by the Bunker's hit point pool instead of their own, because nothing reaches a garrisoned Marine
     * until the Bunker falls.
     *
     * <p>The Marines keep their own weapon range, matching how {@code GameState} measures a Bunker's
     * reach. {@link HorizonCombatSimulator} scales the result by the garrison it has observed.
     *
     * @param bunker the Bunker type
     * @return a fresh array of groundToGround, groundToAir, airToGround, airToAir
     */
    static double[] garrisonStrength(UnitType bunker) {
        UnitType occupant = UnitType.Terran_Marine;
        int occupants = bunker.spaceProvided() / occupant.spaceRequired();
        double pool = durabilityFactor(bunker) / durabilityFactor(occupant);
        double[] entry = formulaStrength(occupant);
        for (int i = 0; i < entry.length; i++) {
            entry[i] *= occupants * pool;
        }
        return entry;
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
