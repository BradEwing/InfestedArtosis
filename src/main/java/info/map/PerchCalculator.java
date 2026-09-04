package info.map;

import bwapi.Position;
import bwapi.Race;
import bwapi.UnitType;
import util.Filter;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;

/**
 * Pure, static helpers for computing overlord perch positions: a ground distance field over the map,
 * the pixel reach of an air-threatening attacker type, the safe clearance in tiles for an opponent race,
 * and the selection of the best perch tile for a given watch target.
 * <p>
 * Nothing here touches {@code Game}; every input is data already extracted from the map or from
 * {@code UnitType}/{@code WeaponType} accessors, so this class is directly testable.
 */
public final class PerchCalculator {

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
     * opponent race: a non-flying, non-building, non-hero air threat belonging to that race (or any race
     * when the opponent race is unknown) whose reach still leaves at least one tile of visible ground
     * for a perched overlord.
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
        if (opponentRace != Race.Unknown && type.getRace() != opponentRace) {
            return false;
        }
        return reachPixels(type) + 32 <= UnitType.Zerg_Overlord.sightRange();
    }

    /**
     * The safe clearance, in tiles, for perching an overlord against the given opponent race: the
     * ceiling of the largest reach (in pixels) among every type satisfying
     * {@link #contributesToClearance(UnitType, Race)}, divided by tile size.
     *
     * @param opponentRace the opponent's race, or {@code Race.Unknown} before it is scouted
     * @return clearance in tiles
     */
    public static int clearanceTiles(Race opponentRace) {
        int maxReach = 0;
        for (UnitType type : UnitType.values()) {
            if (!contributesToClearance(type, opponentRace)) {
                continue;
            }
            maxReach = Math.max(maxReach, reachPixels(type));
        }
        return (int) Math.ceil(maxReach / 32.0);
    }

    /**
     * Picks the best perch tile for watching a target position: among perches within the given sight
     * range of the target, the highest ground, tiebroken by nearest; if none are within sight, the
     * nearest perch, tiebroken by highest ground.
     *
     * @param perches candidate perch tiles
     * @param target the position being watched
     * @param sightRangePixels the watching unit's sight range in pixels
     * @return the selected perch, or null if perches is empty
     */
    public static MapTile selectPerch(Collection<MapTile> perches, Position target, int sightRangePixels) {
        MapTile best = null;
        boolean bestWithinSight = false;
        int bestHeight = Integer.MIN_VALUE;
        double bestDistance = Double.MAX_VALUE;

        for (MapTile perch : perches) {
            Position center = perch.getTile().toPosition().add(new Position(16, 16));
            double distance = center.getDistance(target);
            boolean withinSight = distance <= sightRangePixels;
            int height = perch.getGroundHeight();

            if (best == null) {
                best = perch;
                bestWithinSight = withinSight;
                bestHeight = height;
                bestDistance = distance;
                continue;
            }

            if (withinSight != bestWithinSight) {
                if (withinSight) {
                    best = perch;
                    bestWithinSight = true;
                    bestHeight = height;
                    bestDistance = distance;
                }
                continue;
            }

            if (withinSight) {
                if (height > bestHeight || height == bestHeight && distance < bestDistance) {
                    best = perch;
                    bestHeight = height;
                    bestDistance = distance;
                }
            } else if (distance < bestDistance || distance == bestDistance && height > bestHeight) {
                best = perch;
                bestHeight = height;
                bestDistance = distance;
            }
        }

        return best;
    }
}
