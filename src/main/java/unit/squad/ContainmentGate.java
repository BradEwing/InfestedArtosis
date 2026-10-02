package unit.squad;

import bwapi.UnitType;
import bwapi.WeaponType;

import java.util.Map;

/**
 * Whether a squad's makeup can contain a Terran army and hold the arc against it, read from ground weapon ranges.
 *
 * <p>A melee unit has a ground weapon that reaches at most {@link #MELEE_MAX_RANGE}. Against Terran, a squad that is
 * mostly melee is kept out of a contain when a known mobile enemy ground unit outranges melee, or the opponent has been
 * detected as mech, since it cannot answer fire from the arc and is ground down while it stands there. A squad whose
 * ranged units make up at least {@link #MIN_RANGED_SHARE} of its supply still contains. Against any other race the
 * gate never applies.
 */
public final class ContainmentGate {

    /**
     * The longest ground weapon range, in pixels, that counts as melee: one tile.
     */
    static final int MELEE_MAX_RANGE = 32;

    /**
     * Share of a squad's supply that its ranged units must reach for it to contain against an enemy that outranges
     * melee: a quarter.
     */
    static final double MIN_RANGED_SHARE = 0.25;

    private ContainmentGate() {
    }

    /**
     * @param type a unit type
     * @return the unit's ground weapon range in pixels, 0 when it cannot attack ground
     */
    static int groundRange(UnitType type) {
        WeaponType weapon = type.groundWeapon();
        return weapon == WeaponType.None ? 0 : weapon.maxRange();
    }

    /**
     * @param type a unit type
     * @return true when its ground weapon reaches farther than {@link #MELEE_MAX_RANGE}
     */
    static boolean isRanged(UnitType type) {
        return groundRange(type) > MELEE_MAX_RANGE;
    }

    /**
     * @param composition unit counts of a squad
     * @return the share of the squad's supply that its ranged units supply, 0 for an empty squad
     */
    static double rangedShare(Map<UnitType, Integer> composition) {
        int total = 0;
        int ranged = 0;
        for (Map.Entry<UnitType, Integer> entry : composition.entrySet()) {
            int supply = entry.getKey().supplyRequired() * entry.getValue();
            total += supply;
            if (isRanged(entry.getKey())) {
                ranged += supply;
            }
        }
        return total == 0 ? 0 : (double) ranged / total;
    }

    /**
     * @param composition unit counts of a squad
     * @return the longest ground weapon range among the squad's units, in pixels
     */
    static int longestRange(Map<UnitType, Integer> composition) {
        int longest = 0;
        for (Map.Entry<UnitType, Integer> entry : composition.entrySet()) {
            if (entry.getValue() > 0) {
                longest = Math.max(longest, groundRange(entry.getKey()));
            }
        }
        return longest;
    }

    /**
     * @param range a ground weapon range in pixels
     * @param enemy living enemy unit counts
     * @return true when a known mobile enemy unit's ground weapon reaches farther than the range
     */
    static boolean enemyOutranges(int range, Map<UnitType, Integer> enemy) {
        for (Map.Entry<UnitType, Integer> entry : enemy.entrySet()) {
            UnitType type = entry.getKey();
            if (entry.getValue() > 0 && !type.isBuilding() && groundRange(type) > range) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the squad's makeup lets it contain: always against a non-Terran opponent, otherwise unless the squad is
     * short of ranged supply while the enemy outranges melee or is detected as mech.
     *
     * @param versusTerran true when the opponent is Terran
     * @param mechDetected true when the TerranMech strategy has been detected
     * @param ours the squad's unit counts
     * @param enemy living enemy unit counts
     * @return true when the squad may contain
     */
    static boolean compositionAllows(boolean versusTerran, boolean mechDetected, Map<UnitType, Integer> ours,
                                     Map<UnitType, Integer> enemy) {
        if (!versusTerran) {
            return true;
        }
        if (!mechDetected && !enemyOutranges(MELEE_MAX_RANGE, enemy)) {
            return true;
        }
        return rangedShare(ours) >= MIN_RANGED_SHARE;
    }

    /**
     * Whether a squad the combat sim read as RETREAT may hold a contain arc instead: always against a non-Terran
     * opponent, otherwise only when no known mobile enemy outranges the squad's longest weapon, counting melee as
     * {@link #MELEE_MAX_RANGE}, the opponent is not detected as mech, and the squad's makeup lets it contain, see {@link #compositionAllows}.
     *
     * @param versusTerran true when the opponent is Terran
     * @param mechDetected true when the TerranMech strategy has been detected
     * @param ours the squad's unit counts
     * @param enemy living enemy unit counts
     * @return true when the squad may take the arc on a sim RETREAT
     */
    static boolean safeToHold(boolean versusTerran, boolean mechDetected, Map<UnitType, Integer> ours,
                              Map<UnitType, Integer> enemy) {
        if (!versusTerran) {
            return true;
        }
        return !mechDetected && !enemyOutranges(Math.max(MELEE_MAX_RANGE, longestRange(ours)), enemy)
                && compositionAllows(true, false, ours, enemy);
    }
}
