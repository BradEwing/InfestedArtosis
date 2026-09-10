package info.map;

import bwapi.Position;
import bwapi.Race;
import bwapi.UnitType;
import util.Filter;
import util.Time;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Pure, static helpers for computing overlord perch positions: a ground distance field over the map,
 * the pixel reach of an air-threatening attacker type, the safe clearance in tiles for an opponent race,
 * and the selection of the best perch tile for a given watch target.
 * <p>
 * Nothing here touches {@code Game}; every input is data already extracted from the map or from
 * {@code UnitType}/{@code WeaponType} accessors, so this class is directly testable.
 */
public final class PerchCalculator {

    /**
     * The reference span a scout is expected to spend flying to a perch. A yardstick, not a game
     * constant and not a limit: {@link #selectPerch} does not enforce it. It is carried in the perch
     * assignment telemetry so a batch can tell how many assignments overran it, which is what would
     * justify turning it into a real bound.
     */
    public static final int TRANSIT_BUDGET_FRAMES = new Time(0, 30).getFrames();

    /**
     * The granularity at which {@link #selectPerch} compares scout distance before ground height
     * decides. Perch candidates are tiles, so distances that agree to within a tile are treated as
     * equal; without a band the height term would need exact distance equality and could never fire.
     */
    private static final int SCOUT_DISTANCE_BAND_PIXELS = 32;

    private PerchCalculator() {
    }

    /**
     * Multi-source BFS ground distance field, 8-connected (Chebyshev distance in tiles), seeded with
     * every ground-occupiable tile at distance 0. Tiles unreachable from any ground-occupiable tile
     * are left at {@code Integer.MAX_VALUE}.
     *
     * @param groundOccupiable per-tile flag indexed {@code [x][y]}
     * @return the ground distance field, indexed {@code [x][y]}
     */
    public static int[][] groundDistances(boolean[][] groundOccupiable) {
        int width = groundOccupiable.length;
        int height = width == 0 ? 0 : groundOccupiable[0].length;
        int[][] distances = new int[width][height];
        for (int[] column : distances) {
            Arrays.fill(column, Integer.MAX_VALUE);
        }

        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (groundOccupiable[x][y]) {
                    distances[x][y] = 0;
                    queue.add(new int[]{x, y});
                }
            }
        }

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int currentX = current[0];
            int currentY = current[1];
            int nextDistance = distances[currentX][currentY] + 1;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    int neighborX = currentX + dx;
                    int neighborY = currentY + dy;
                    if (neighborX < 0 || neighborX >= width || neighborY < 0 || neighborY >= height) {
                        continue;
                    }
                    if (distances[neighborX][neighborY] <= nextDistance) {
                        continue;
                    }
                    distances[neighborX][neighborY] = nextDistance;
                    queue.add(new int[]{neighborX, neighborY});
                }
            }
        }

        return distances;
    }

    /**
     * How far in pixels an attacker's air weapon threatens, measured from the attacker's tile centre
     * to an overlord's tile centre: the weapon's max range plus half the attacker's largest dimension,
     * plus half the overlord's largest dimension, plus 16 pixels to cover the attacker standing at the
     * edge of its tile rather than its centre.
     *
     * @param attacker the potential air-threatening unit type
     * @return the reach in pixels
     */
    public static int reachPixels(UnitType attacker) {
        int attackerHalfSpan = Math.max(attacker.width(), attacker.height()) / 2;
        int overlordHalfSpan = Math.max(UnitType.Zerg_Overlord.width(), UnitType.Zerg_Overlord.height()) / 2;
        return attacker.airWeapon().maxRange() + attackerHalfSpan + overlordHalfSpan + 16;
    }

    /**
     * Whether a unit type should be considered when computing the safe perch clearance against a given
     * opponent race: a non-flying, non-building, non-hero air threat that something can build,
     * belonging to that race (or any race when the opponent race is unknown), whose reach still leaves
     * at least one tile of visible ground for a perched overlord. Sub-units such as the Goliath turret
     * are excluded because nothing builds them.
     *
     * @param type unit type under consideration
     * @param opponentRace the opponent's race, or {@code Race.Unknown} before it is scouted
     * @return true if the type should factor into {@link #clearanceTiles(Race)}
     */
    public static boolean contributesToClearance(UnitType type, Race opponentRace) {
        if (type.isFlyer() || type.isBuilding() || type.isHero()) {
            return false;
        }
        if (!Filter.isAirThreat(type)) {
            return false;
        }
        if (type.airWeapon().maxRange() <= 0) {
            return false;
        }
        if (type.whatBuilds().getLeft() == UnitType.None) {
            return false;
        }
        if (opponentRace != Race.Unknown && type.getRace() != opponentRace) {
            return false;
        }
        return reachPixels(type) + 32 <= UnitType.Zerg_Overlord.sightRange();
    }

    /**
     * The safe clearance, in tiles, for perching an overlord against the given opponent race: the
     * ceiling of the largest reach (in pixels) among the shallowest-tech types satisfying
     * {@link #contributesToClearance(UnitType, Race)}, divided by tile size. Deeper-tech anti-air
     * (Goliath, Archon) arrives later and is handled by the perch leave predicate instead, so the
     * perch guards against the anti-air the opponent can field first.
     *
     * @param opponentRace the opponent's race, or {@code Race.Unknown} before it is scouted
     * @return clearance in tiles
     */
    public static int clearanceTiles(Race opponentRace) {
        int shallowestDepth = Integer.MAX_VALUE;
        for (UnitType type : UnitType.values()) {
            if (contributesToClearance(type, opponentRace)) {
                shallowestDepth = Math.min(shallowestDepth, techDepth(type));
            }
        }
        int maxReach = 0;
        for (UnitType type : UnitType.values()) {
            if (!contributesToClearance(type, opponentRace) || techDepth(type) != shallowestDepth) {
                continue;
            }
            maxReach = Math.max(maxReach, reachPixels(type));
        }
        return (int) Math.ceil(maxReach / 32.0);
    }

    /**
     * Depth of a unit type in its race's tech tree: the longest chain of {@code requiredUnits}
     * beneath it. Only the ordering between types matters.
     *
     * @param type unit type
     * @return tech depth, 0 for a type with no requirements
     */
    public static int techDepth(UnitType type) {
        return techDepth(type, new HashSet<>());
    }

    private static int techDepth(UnitType type, Set<UnitType> visiting) {
        if (!visiting.add(type)) {
            return 0;
        }
        int depth = 0;
        for (UnitType required : type.requiredUnits().keySet()) {
            depth = Math.max(depth, techDepth(required, visiting) + 1);
        }
        visiting.remove(type);
        return depth;
    }

    /**
     * The distance a scouting unit covers in {@link #TRANSIT_BUDGET_FRAMES} at its top speed. Reported
     * alongside a perch assignment as the yardstick the assignment is measured against; it does not
     * narrow the candidate set.
     *
     * @param scout the scouting unit's type
     * @return the transit budget in pixels
     */
    public static int transitPixels(UnitType scout) {
        return (int) (scout.topSpeed() * TRANSIT_BUDGET_FRAMES);
    }

    /**
     * Picks the best perch tile for a scout watching a target position: a perch that sees the target
     * beats one that does not, the nearest to the scout wins among equals, and the highest ground
     * breaks a remaining tie.
     * <p>
     * Vision leads because a perch that watches nothing is worth little wherever it sits. Distance is
     * measured from the scout rather than from the target, so when no perch sees the target the scout
     * takes the nearest safe perch instead of the one closest to the enemy. Distance is compared in
     * whole tiles of {@link #SCOUT_DISTANCE_BAND_PIXELS}, so the height preference decides between
     * perches the scout reaches equally soon rather than needing an exact distance tie.
     *
     * @param perches candidate perch tiles
     * @param target the position being watched
     * @param scout the scouting unit's current position
     * @param sightRangePixels the scouting unit's sight range in pixels
     * @return the selected perch, or null if perches is empty
     */
    public static MapTile selectPerch(Collection<MapTile> perches, Position target, Position scout,
                                      int sightRangePixels) {
        MapTile best = null;
        boolean bestWatching = false;
        int bestScoutBand = Integer.MAX_VALUE;
        int bestHeight = Integer.MIN_VALUE;

        for (MapTile perch : perches) {
            Position center = perch.getTile().toPosition().add(new Position(16, 16));
            int scoutBand = (int) (center.getDistance(scout) / SCOUT_DISTANCE_BAND_PIXELS);
            boolean watching = center.getDistance(target) <= sightRangePixels;
            int height = perch.getGroundHeight();

            if (best != null && !improves(watching, scoutBand, height,
                    bestWatching, bestScoutBand, bestHeight)) {
                continue;
            }

            best = perch;
            bestWatching = watching;
            bestScoutBand = scoutBand;
            bestHeight = height;
        }

        return best;
    }

    /**
     * Whether a candidate outranks the incumbent on the perch ordering: watching the target first,
     * nearest the scout second, highest ground last.
     */
    private static boolean improves(boolean watching, int scoutBand, int height,
                                    boolean bestWatching, int bestScoutBand, int bestHeight) {
        if (watching != bestWatching) {
            return watching;
        }
        if (scoutBand != bestScoutBand) {
            return scoutBand < bestScoutBand;
        }
        return height > bestHeight;
    }
}
