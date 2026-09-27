package unit.squad;

import bwapi.Position;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps an air squad's Mutalisks together, as static functions over plain values.
 *
 * <p>The flock's anchor is the member with the smallest summed distance to the others, so a single straggler does
 * not drag it away from the rest of the flock. A member farther than {@link #REGROUP_RADIUS} from the anchor
 * regroups on it before taking a new target or strike point, and keeps regrouping until it is back within
 * {@link #REGROUP_JOIN_RADIUS}. A retreating flock flees to one shared point, away from every enemy near any of its
 * members.
 */
public final class AirFlock {

    /** Tuning value: pixels from the anchor beyond which a member stops taking orders and regroups. */
    static final int REGROUP_RADIUS = 192;
    /** Tuning value: pixels from the anchor within which a regrouping member rejoins the flock's orders. */
    static final int REGROUP_JOIN_RADIUS = 96;
    /** Tuning value: pixels around each member within which an enemy pushes the flock's retreat point. */
    static final int RETREAT_SCAN_RADIUS = 256;
    /** Tuning value: pixels past the flock's leading member to the flock's retreat point. */
    static final int RETREAT_FLEE_DISTANCE = 256;

    private AirFlock() {
    }

    /**
     * The flock's anchor: the member position with the smallest summed distance to every other member, the lowest
     * unit id winning a tie.
     *
     * @param members member positions by unit id
     * @return the anchor, or null with no members
     */
    public static Position anchor(Map<Integer, Position> members) {
        Position best = null;
        int bestId = Integer.MAX_VALUE;
        double bestSum = Double.MAX_VALUE;
        for (Map.Entry<Integer, Position> entry : members.entrySet()) {
            double sum = 0;
            for (Position other : members.values()) {
                sum += entry.getValue().getDistance(other);
            }
            boolean tie = sum == bestSum && entry.getKey() < bestId;
            if (sum < bestSum || tie) {
                best = entry.getValue();
                bestId = entry.getKey();
                bestSum = sum;
            }
        }
        return best;
    }

    /**
     * The members that regroup on the anchor instead of taking orders: every member beyond
     * {@link #REGROUP_RADIUS}, and every member already regrouping that is still beyond {@link #REGROUP_JOIN_RADIUS}.
     * A flock of one has no stragglers.
     *
     * @param members member positions by unit id
     * @param anchor the flock's anchor
     * @param regrouping unit ids regrouping on the previous frame
     * @return unit ids regrouping this frame
     */
    public static Set<Integer> stragglers(Map<Integer, Position> members, Position anchor, Set<Integer> regrouping) {
        Set<Integer> stragglers = new HashSet<>();
        if (members.size() < 2 || anchor == null) {
            return stragglers;
        }
        for (Map.Entry<Integer, Position> entry : members.entrySet()) {
            double distance = entry.getValue().getDistance(anchor);
            int radius = regrouping.contains(entry.getKey()) ? REGROUP_JOIN_RADIUS : REGROUP_RADIUS;
            if (distance > radius) {
                stragglers.add(entry.getKey());
            }
        }
        return stragglers;
    }

    /**
     * How many members regroup, as telemetry reports it: -1 in RETREAT, where the flock flees to one point and
     * nothing regroups.
     *
     * @param status the squad's status
     * @param regrouping unit ids regrouping
     * @return the count, or -1 in RETREAT
     */
    public static int regroupingCount(SquadStatus status, Set<Integer> regrouping) {
        return status == SquadStatus.RETREAT ? -1 : regrouping.size();
    }

    /**
     * The retreat target of every member of a retreating flock: the one {@link #retreatPoint} of its anchor, the
     * same for every member, or null for every member with no enemy near the flock.
     *
     * @param members member positions by unit id
     * @param enemies enemy positions
     * @param mapWidth map width in pixels
     * @param mapHeight map height in pixels
     * @return retreat targets by unit id
     */
    public static Map<Integer, Position> retreatTargets(Map<Integer, Position> members, Collection<Position> enemies,
                                                        int mapWidth, int mapHeight) {
        Position point = retreatPoint(anchor(members), members.values(), enemies, mapWidth, mapHeight);
        Map<Integer, Position> targets = new HashMap<>();
        for (Integer id : members.keySet()) {
            targets.put(id, point);
        }
        return targets;
    }

    /**
     * The one point every member of a retreating flock flees to: directly away from the summed offsets from the
     * anchor of every enemy within {@link #RETREAT_SCAN_RADIUS} of any member, {@link #RETREAT_FLEE_DISTANCE} past the
     * member farthest along that direction, or past the anchor when no member is ahead of it, kept inside the map.
     * Measuring from the leading member keeps every member at least the flee distance short of the point, so none
     * arrives at it and swaps it for a flee point of its own.
     *
     * @param anchor the flock's anchor
     * @param members member positions
     * @param enemies enemy positions
     * @param mapWidth map width in pixels
     * @param mapHeight map height in pixels
     * @return the retreat point, or null with no enemy near the flock or no direction away from it
     */
    public static Position retreatPoint(Position anchor, Collection<Position> members, Collection<Position> enemies,
                                        int mapWidth, int mapHeight) {
        if (anchor == null) {
            return null;
        }
        double sumDx = 0;
        double sumDy = 0;
        boolean found = false;
        for (Position enemy : enemies) {
            if (!nearAny(enemy, members, RETREAT_SCAN_RADIUS)) {
                continue;
            }
            found = true;
            sumDx += enemy.getX() - anchor.getX();
            sumDy += enemy.getY() - anchor.getY();
        }
        double length = Math.sqrt(sumDx * sumDx + sumDy * sumDy);
        if (!found || length == 0) {
            return null;
        }
        double dirX = -sumDx / length;
        double dirY = -sumDy / length;
        double lead = 0;
        for (Position member : members) {
            lead = Math.max(lead, (member.getX() - anchor.getX()) * dirX + (member.getY() - anchor.getY()) * dirY);
        }
        double flee = lead + RETREAT_FLEE_DISTANCE;
        int x = anchor.getX() + (int) Math.round(dirX * flee);
        int y = anchor.getY() + (int) Math.round(dirY * flee);
        return new Position(Math.max(0, Math.min(x, mapWidth - 1)), Math.max(0, Math.min(y, mapHeight - 1)));
    }

    /**
     * The mean of the member positions.
     *
     * @param members member positions
     * @return the centroid, or null with no members
     */
    public static Position centroid(Collection<Position> members) {
        if (members.isEmpty()) {
            return null;
        }
        long x = 0;
        long y = 0;
        for (Position position : members) {
            x += position.getX();
            y += position.getY();
        }
        return new Position((int) (x / members.size()), (int) (y / members.size()));
    }

    /**
     * Distances from each member to a point, sorted ascending.
     *
     * @param members member positions
     * @param point the point
     * @return the sorted distances
     */
    public static List<Double> distances(Collection<Position> members, Position point) {
        List<Double> distances = new ArrayList<>();
        for (Position position : members) {
            distances.add(position.getDistance(point));
        }
        Collections.sort(distances);
        return distances;
    }

    /**
     * The median of sorted values, the mean of the middle two for an even count.
     *
     * @param sorted values sorted ascending
     * @return the median, or -1 with no values
     */
    public static double median(List<Double> sorted) {
        int size = sorted.size();
        if (size == 0) {
            return -1;
        }
        return size % 2 == 1 ? sorted.get(size / 2) : (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2;
    }

    /**
     * Distance from a point to the nearest of other positions.
     *
     * @param from the point
     * @param others the other positions
     * @return the distance, or -1 with none
     */
    public static double nearestDistance(Position from, Collection<Position> others) {
        double nearest = -1;
        for (Position other : others) {
            double distance = from.getDistance(other);
            if (nearest < 0 || distance < nearest) {
                nearest = distance;
            }
        }
        return nearest;
    }

    private static boolean nearAny(Position point, Collection<Position> members, int radius) {
        for (Position member : members) {
            if (member.getDistance(point) <= radius) {
                return true;
            }
        }
        return false;
    }
}
