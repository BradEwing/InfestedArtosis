package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import bwapi.WalkPosition;
import util.Arc;
import util.StaticDefenseZone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Moves a containment arc back from enemies that outrange the units holding it.
 *
 * <p>The arc is first recomputed where it stands against the current zones, which pushes only the points now
 * covered. When that loses points, it is regrown around the same choke at a larger radius, step by step, until a
 * computed arc keeps as many points as the arc it replaces. Every computed point already sits outside every reach
 * zone plus the padding, and members are only ever given computed points, so every member ends up outside the
 * reach of every such enemy.
 */
final class ContainmentPushback {

    static final int RADIUS_STEP = 32;
    static final int MAX_RADIUS = 416;

    private ContainmentPushback() {
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
     * The zones a containing squad keeps its arc out of: every static defence structure, and every other zone whose
     * reach outranges the squad's shortest ranged member. An enemy the squad can answer in its own range is fought
     * on the line instead.
     *
     * @param zones every ground threat zone
     * @param memberRange shortest ground weapon range among the squad's members
     * @return the zones the arc must stay out of
     */
    static List<StaticDefenseZone> outrangingZones(Collection<StaticDefenseZone> zones, int memberRange) {
        List<StaticDefenseZone> kept = new ArrayList<>();
        for (StaticDefenseZone zone : zones) {
            if (zone.getStructure().isBuilding() || outranges(zone.getReach(), memberRange)) {
                kept.add(zone);
            }
        }
        return kept;
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
        return coveringType(arc, zones, padding) != null;
    }

    /**
     * The type behind the longest reaching zone that covers a point of the arc.
     *
     * @param arc computed arc
     * @param zones reach zones to test
     * @param padding pixels added to each zone's reach
     * @return the type, {@link UnitType#None} for a hurt mark, or null when no zone covers the arc
     */
    static UnitType coveringType(Arc arc, Collection<StaticDefenseZone> zones, int padding) {
        StaticDefenseZone longest = null;
        for (Position point : arc.getPositions()) {
            for (StaticDefenseZone zone : zones) {
                if (zone.covers(point, padding) && (longest == null || zone.getReach() > longest.getReach())) {
                    longest = zone;
                }
            }
        }
        return longest == null ? null : longest.getStructure();
    }

    /**
     * Recomputes the arc against the current zones: where it stands when that keeps every point, otherwise the
     * fuller of that and the arc {@link #pushBack} finds.
     *
     * @param current arc the squad holds now
     * @param zones every reach zone the points must stay out of
     * @param padding pixels added to every zone's reach, covering the holding unit's extent and a margin
     * @param accessible walkable positions, or empty to treat the whole map as walkable
     * @param mapPixelWidth map width in pixels
     * @param mapPixelHeight map height in pixels
     * @return the recomputed arc, or null when no point is left clear
     */
    static Arc recompute(Arc current, Collection<StaticDefenseZone> zones, int padding, Set<WalkPosition> accessible,
                         int mapPixelWidth, int mapPixelHeight) {
        Arc inPlace = current.withRadius(current.getRadius());
        inPlace.compute(accessible, zones, padding, mapPixelWidth, mapPixelHeight);
        if (!inPlace.isEmpty() && inPlace.size() >= current.size()) {
            return inPlace;
        }
        Arc pushed = pushBack(current, zones, padding, accessible, mapPixelWidth, mapPixelHeight);
        if (pushed != null && pushed.size() > inPlace.size()) {
            return pushed;
        }
        return inPlace.isEmpty() ? null : inPlace;
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
