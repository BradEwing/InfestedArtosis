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
 * {@link #REGROUP_JOIN_RADIUS}. A regrouping member keeps attacking a target already within its weapon range, see
 * {@link #keepsTarget}. A retreating flock flees to one shared point, away from every enemy near any of its members;
 * a member beyond {@link #REGROUP_RADIUS} whose straight path to that point runs through an enemy takes the anchor or
 * a flee point of its own instead, see {@link #retreatBranch}, unless it is beyond {@link #RETREAT_FLEE_LEASH}
 * of the anchor, where it flies to the anchor.
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
    /** Tuning value: pixels from an enemy ahead within which a straight retreat path counts as running through it. */
    static final int RETREAT_PATH_CLEARANCE = 160;
    /** Tuning value: pixels a member's own flee point must lie from it to be taken over the anchor. */
    static final int MIN_FLEE_STEP = 64;
    /** Tuning value: pixels from the anchor beyond which a retreating member flies to the anchor, never away. */
    static final int RETREAT_FLEE_LEASH = 2 * RETREAT_SCAN_RADIUS;

    /**
     * Where a retreating member's target comes from.
     */
    public enum RetreatBranch {
        /** No enemy near the flock, so no retreat point. */
        NONE,
        /** The flock's shared retreat point. */
        SHARED,
        /** The flock's anchor. */
        ANCHOR,
        /** The member's own flee point. */
        FLEE
    }

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
     * Whether a regrouping member keeps its current target instead of flying back to the anchor: only a target
     * already within its weapon range, and never while the member stands inside an avoided zone. A regrouping member
     * takes no new target.
     *
     * @param targetInWeaponRange true when the member holds a target within its weapon range
     * @param insideAvoidedZone true when the member stands inside an avoided anti-air zone
     * @return true when the member keeps attacking its target
     */
    public static boolean keepsTarget(boolean targetInWeaponRange, boolean insideAvoidedZone) {
        return targetInWeaponRange && !insideAvoidedZone;
    }

    /**
     * The members that started regrouping this frame.
     *
     * @param previous unit ids regrouping on the previous frame
     * @param current unit ids regrouping this frame
     * @return unit ids in current but not in previous
     */
    public static Set<Integer> entered(Set<Integer> previous, Set<Integer> current) {
        Set<Integer> entered = new HashSet<>(current);
        entered.removeAll(previous);
        return entered;
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
     * The retreat target and branch of every member of a retreating flock.
     */
    public static final class RetreatPlan {

        private final Map<Integer, Position> targets;
        private final Map<Integer, RetreatBranch> branches;

        private RetreatPlan(Map<Integer, Position> targets, Map<Integer, RetreatBranch> branches) {
            this.targets = targets;
            this.branches = branches;
        }

        /**
         * @return retreat targets by unit id, each null when no enemy is near the flock
         */
        public Map<Integer, Position> getTargets() {
            return targets;
        }

        /**
         * @return the branch each target comes from, by unit id, every one {@link RetreatBranch#NONE} when no enemy
         *         is near the flock
         */
        public Map<Integer, RetreatBranch> getBranches() {
            return branches;
        }
    }

    /**
     * The retreat target of every member of a retreating flock: the one {@link #retreatPoint} of its anchor, shared
     * by every member except a far member whose path to it runs through an enemy within {@link #RETREAT_SCAN_RADIUS}
     * of some member, see {@link #retreatBranch}, or null for every member with no enemy near the flock.
     *
     * <p>A member beyond {@link #RETREAT_FLEE_LEASH} of the anchor whose path to the shared point is blocked is
     * added to the leashed ids, and stays on the anchor until it takes the shared point again, so it does not turn
     * back to a flee point of its own the moment it is inside the leash. The ids of members no longer in the flock,
     * and every id when no enemy is near the flock, are dropped.
     *
     * @param members member positions by unit id
     * @param enemies enemy positions
     * @param mapWidth map width in pixels
     * @param mapHeight map height in pixels
     * @param leashed unit ids held on the anchor by the leash, updated in place
     * @return the targets and branches by unit id
     */
    public static RetreatPlan retreatPlan(Map<Integer, Position> members, Collection<Position> enemies,
                                          int mapWidth, int mapHeight, Set<Integer> leashed) {
        leashed.retainAll(members.keySet());
        Position anchor = anchor(members);
        Position point = retreatPoint(anchor, members.values(), enemies, mapWidth, mapHeight);
        Map<Integer, Position> targets = new HashMap<>();
        Map<Integer, RetreatBranch> branches = new HashMap<>();
        if (point == null) {
            leashed.clear();
            for (Integer id : members.keySet()) {
                targets.put(id, null);
                branches.put(id, RetreatBranch.NONE);
            }
            return new RetreatPlan(targets, branches);
        }
        List<Position> near = nearFlock(enemies, members.values());
        for (Map.Entry<Integer, Position> entry : members.entrySet()) {
            Integer id = entry.getKey();
            Position member = entry.getValue();
            RetreatBranch branch = retreatBranch(member, anchor, point, near, mapWidth, mapHeight,
                    leashed.contains(id));
            if (branch == RetreatBranch.SHARED) {
                leashed.remove(id);
            } else if (member.getDistance(anchor) > RETREAT_FLEE_LEASH) {
                leashed.add(id);
            }
            branches.put(id, branch);
            targets.put(id, branch == RetreatBranch.SHARED ? point
                    : branch == RetreatBranch.FLEE ? fleePoint(member, point, near, mapWidth, mapHeight)
                    : anchor);
        }
        return new RetreatPlan(targets, branches);
    }

    /**
     * Which of the three retreat targets a member takes. A member within {@link #REGROUP_RADIUS} of the anchor, or
     * one whose straight path to the shared point runs through no enemy, see {@link #pathThroughEnemy}, takes the
     * shared point. A farther member takes the anchor when it is leashed, when it is beyond
     * {@link #RETREAT_FLEE_LEASH} of the anchor, or when its path there runs through no enemy; otherwise it takes
     * its own {@link #fleePoint}, or the anchor when it has none or the map edge leaves it within
     * {@link #MIN_FLEE_STEP}.
     *
     * @param member the member's position
     * @param anchor the flock's anchor
     * @param shared the flock's shared retreat point
     * @param enemies enemy positions
     * @param mapWidth map width in pixels
     * @param mapHeight map height in pixels
     * @param leashed true when the member is held on the anchor by the leash
     * @return the branch the member's retreat target comes from
     */
    public static RetreatBranch retreatBranch(Position member, Position anchor, Position shared,
                                              Collection<Position> enemies, int mapWidth, int mapHeight,
                                              boolean leashed) {
        double fromAnchor = member.getDistance(anchor);
        if (fromAnchor <= REGROUP_RADIUS || !pathThroughEnemy(member, shared, enemies)) {
            return RetreatBranch.SHARED;
        }
        if (leashed || fromAnchor > RETREAT_FLEE_LEASH || !pathThroughEnemy(member, anchor, enemies)) {
            return RetreatBranch.ANCHOR;
        }
        Position flee = fleePoint(member, shared, enemies, mapWidth, mapHeight);
        return flee != null && flee.getDistance(member) >= MIN_FLEE_STEP ? RetreatBranch.FLEE : RetreatBranch.ANCHOR;
    }

    /**
     * How many of the given members took a retreat branch.
     *
     * @param branches branches by unit id
     * @param ids the members to count
     * @param branch the branch to count
     * @return the number of members in ids whose branch is the given one
     */
    public static int branchCount(Map<Integer, RetreatBranch> branches, Collection<Integer> ids,
                                  RetreatBranch branch) {
        int count = 0;
        for (Integer id : ids) {
            if (branches.get(id) == branch) {
                count++;
            }
        }
        return count;
    }

    /**
     * Whether a straight flight runs through an enemy: some enemy ahead of the start, past it along the flight,
     * lies within {@link #RETREAT_PATH_CLEARANCE} of the path. An enemy behind the start, or level with it, does not
     * count, so fleeing directly away from an enemy close by never runs through it.
     *
     * @param from the start of the flight
     * @param to the end of the flight
     * @param enemies enemy positions
     * @return true when an enemy ahead lies within the clearance of the path
     */
    public static boolean pathThroughEnemy(Position from, Position to, Collection<Position> enemies) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared == 0) {
            return false;
        }
        double clearanceSquared = (double) RETREAT_PATH_CLEARANCE * RETREAT_PATH_CLEARANCE;
        for (Position enemy : enemies) {
            double ex = enemy.getX() - from.getX();
            double ey = enemy.getY() - from.getY();
            double along = (ex * dx + ey * dy) / lengthSquared;
            if (along <= 0) {
                continue;
            }
            double t = Math.min(along, 1);
            double offX = ex - t * dx;
            double offY = ey - t * dy;
            if (offX * offX + offY * offY < clearanceSquared) {
                return true;
            }
        }
        return false;
    }

    /**
     * A member's own flee point: directly away from the summed offsets of every enemy within
     * {@link #RETREAT_SCAN_RADIUS} of it or across its path to the shared point, {@link #RETREAT_FLEE_DISTANCE} from
     * it, kept inside the map.
     *
     * @param member the member's position
     * @param shared the flock's shared retreat point
     * @param enemies enemy positions
     * @param mapWidth map width in pixels
     * @param mapHeight map height in pixels
     * @return the flee point, or null with no such enemy or no direction away from them
     */
    public static Position fleePoint(Position member, Position shared, Collection<Position> enemies,
                                     int mapWidth, int mapHeight) {
        List<Position> threats = new ArrayList<>();
        for (Position enemy : enemies) {
            boolean near = member.getDistance(enemy) <= RETREAT_SCAN_RADIUS;
            if (near || pathThroughEnemy(member, shared, Collections.singletonList(enemy))) {
                threats.add(enemy);
            }
        }
        return awayFrom(member, Collections.singletonList(member), threats, mapWidth, mapHeight);
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
        return awayFrom(anchor, members, nearFlock(enemies, members), mapWidth, mapHeight);
    }

    private static List<Position> nearFlock(Collection<Position> enemies, Collection<Position> members) {
        List<Position> near = new ArrayList<>();
        for (Position enemy : enemies) {
            if (nearAny(enemy, members, RETREAT_SCAN_RADIUS)) {
                near.add(enemy);
            }
        }
        return near;
    }

    private static Position awayFrom(Position anchor, Collection<Position> members, Collection<Position> threats,
                                     int mapWidth, int mapHeight) {
        double sumDx = 0;
        double sumDy = 0;
        for (Position threat : threats) {
            sumDx += threat.getX() - anchor.getX();
            sumDy += threat.getY() - anchor.getY();
        }
        double length = Math.sqrt(sumDx * sumDx + sumDy * sumDy);
        if (threats.isEmpty() || length == 0) {
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
