package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import bwapi.WalkPosition;
import bwapi.WeaponType;
import util.Arc;
import util.StaticDefenseZone;

import java.util.Collection;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Moves a containment arc back from enemies that outrange the units holding it.
 *
 * <p>The arc is regrown around the same choke at a larger radius, step by step, until a computed arc keeps as
 * many points as the arc it replaces. Every computed point already sits outside every reach zone plus the padding,
 * and members are only ever given computed points, so every member ends up outside the reach of every such enemy.
 */
final class ContainmentPushback {

    static final int RADIUS_STEP = 32;
    static final int MAX_RADIUS = 416;

    private ContainmentPushback() {
    }

    /**
     * Ground reach of an enemy, measured from its edge the way {@link bwapi.Unit#getDistance} measures it. A Bunker
     * reaches as far as the Marines inside it, matching the static defence zones.
     *
     * @param type enemy unit type
     * @param weaponRange maps a ground weapon to its range for the owning player, upgrades included
     * @return reach in pixels, or 0 when the unit has no ground attack
     */
    static int groundReach(UnitType type, ToIntFunction<WeaponType> weaponRange) {
        WeaponType weapon = type == UnitType.Terran_Bunker
                ? UnitType.Terran_Marine.groundWeapon()
                : type.groundWeapon();
        if (weapon == null || weapon == WeaponType.None) {
            return 0;
        }
        return weaponRange.applyAsInt(weapon);
    }

    /**
     * Whether an enemy's ground reach exceeds the member's own, so the member can be hit where it stands and
     * cannot answer.
     *
     * @param enemyReach enemy ground reach in pixels
     * @param memberRange member ground weapon range in pixels
     * @return true when the enemy outranges the member
     */
    static boolean outranges(int enemyReach, int memberRange) {
        return enemyReach > memberRange;
    }

    /**
     * Whether any point of the arc lies within reach plus padding of any zone.
     *
     * @param arc computed arc
     * @param zones reach zones to test
     * @param padding pixels added to each zone's reach
     * @return true when a point is covered
     */
    static boolean covers(Arc arc, Collection<StaticDefenseZone> zones, int padding) {
        for (Position point : arc.getPositions()) {
            for (StaticDefenseZone zone : zones) {
                if (zone.covers(point, padding)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Regrows the arc at a larger radius until every point is clear of the zones and the arc keeps as many points
     * as it had.
     *
     * @param current arc the squad holds now
     * @param zones every reach zone the points must stay out of, static defence and outranging enemies alike
     * @param padding pixels added to every zone's reach, covering the holding unit's extent and a margin
     * @param accessible walkable positions, or empty to treat the whole map as walkable
     * @param mapPixelWidth map width in pixels
     * @param mapPixelHeight map height in pixels
     * @return the first arc that keeps the current point count, else the fullest arc found, or null when no
     *     radius up to {@link #MAX_RADIUS} leaves a point clear
     */
    static Arc pushBack(Arc current, Collection<StaticDefenseZone> zones, int padding, Set<WalkPosition> accessible,
                        int mapPixelWidth, int mapPixelHeight) {
        Arc fullest = null;
        for (int radius = current.getRadius() + RADIUS_STEP; radius <= MAX_RADIUS; radius += RADIUS_STEP) {
            Arc candidate = current.withRadius(radius);
            candidate.compute(accessible, zones, padding, mapPixelWidth, mapPixelHeight);
            if (candidate.isEmpty()) {
                continue;
            }
            if (candidate.size() >= current.size()) {
                return candidate;
            }
            if (fullest == null || candidate.size() > fullest.size()) {
                fullest = candidate;
            }
        }
        return fullest;
    }
}
