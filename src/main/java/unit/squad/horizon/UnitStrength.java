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
 * points, shields excluded, multiplying all four domains. It carries no reference or anchor
 * constant. An anchor would be a single factor common to every formula entry, so it would cancel exactly from
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

    /**
     * Air share reported for a side of an engagement that holds nothing armed to split by.
     */
    public static final double UNMEASURED_AIR_SHARE = -1;

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
     * <p>Hit points only, not shields. Shields are Protoss-only, so counting them would reprice every
     * Protoss type against every Zerg and Terran one on top of its hit points: a Zealot would score
     * 2.14 and a Photon Cannon 2.39 times the durability of a Zergling. Without them both score 1.69,
     * and the term leaves every Terran and Zerg pairing exactly where hit points put it.
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
        int pool = type.maxHitPoints();
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

    /**
     * Strength a unit type brings against the opposing units it is fighting, never more than one domain of it.
     * See {@link #engaged}.
     *
     * @param type unit type to price
     * @param groundTargets strength of the opposing side standing on the ground
     * @param airTargets strength of the opposing side in the air
     * @return the strength that unit brings to the fight
     */
    public static double engagedStrength(UnitType type, double groundTargets, double airTargets) {
        double[] s = STRENGTH_TABLE.get(type);
        if (s == null) return 0;
        return engaged(s[0] + s[2], s[1] + s[3], groundTargets, airTargets);
    }

    /**
     * Strength a unit type brings in its stronger domain, the price it carries before anything is known about what
     * it faces. It is also how much that unit weighs as a target when the other side is priced.
     *
     * @param type unit type to price
     * @return the larger of its ground-engaging and air-engaging strength
     */
    public static double strongerDomain(UnitType type) {
        return engagedStrength(type, 0, 0);
    }

    /**
     * Blends a unit's ground-engaging and air-engaging strength over the opposing strength it can actually hit.
     *
     * <p>A unit fires one weapon at one target at a time, so the two scores are alternatives, not a sum: a
     * Mutalisk's single weapon fills both airToGround and airToAir, and a Dragoon's fills both groundToGround and
     * groundToAir. Each score is weighted by the opposing strength standing in its layer, over the opposing
     * strength in the layers the unit has a weapon for. Opposing units it cannot shoot therefore neither dilute nor
     * add to its price: Zerglings keep their full ground strength beside Corsairs, and a Dragoon facing only
     * ground units counts its ground weapon alone.
     *
     * <p>A unit with no weapon for any layer the other side stands in is priced at zero. With nothing measured on
     * the other side it is priced at its stronger domain.
     *
     * @param versusGround the unit's strength against ground targets
     * @param versusAir the unit's strength against air targets
     * @param groundTargets strength of the opposing side standing on the ground
     * @param airTargets strength of the opposing side in the air
     * @return the blended strength
     */
    public static double engaged(double versusGround, double versusAir, double groundTargets, double airTargets) {
        double reachableGround = versusGround > 0 ? groundTargets : 0;
        double reachableAir = versusAir > 0 ? airTargets : 0;
        double reachable = reachableGround + reachableAir;
        if (reachable > 0) return (reachableGround * versusGround + reachableAir * versusAir) / reachable;
        if (groundTargets + airTargets > 0) return 0;
        return Math.max(versusGround, versusAir);
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
