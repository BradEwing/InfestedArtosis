package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Air reinforcement decisions, as static functions over plain values: whether a rallying air squad still waits for
 * its move out threshold, which active air squad it reinforces, and the path it flies there outside known anti-air.
 *
 * <p>An anti-air threat is read as a disc around its position: its reach, plus the largest distance from its
 * centre to a corner of its footprint, plus the Mutalisk padding of {@link AirHarassTargeting#padding()}. The disc
 * holds every point {@link AirHarassTargeting.AirThreat#covers} does, so a path clear of the discs is clear of the
 * zones.
 */
public final class AirReinforcement {

    /** Tuning value: pixels between squad centres within which a reinforcing squad joins its target. */
    static final int ARRIVAL_DISTANCE = (int) SquadManager.SQUAD_MERGE_DISTANCE;
    /**
     * Tuning value: pixels within which a squad centre has reached a waypoint and steers for the next one. Every
     * path edge keeps this much clear of every disc, so cutting a reached waypoint's corner stays outside them.
     */
    static final int WAYPOINT_REACHED = 48;
    /** Tuning value: pixels from the goal its arrival points sit at, well inside {@link #ARRIVAL_DISTANCE}. */
    static final int ARRIVAL_RING = ARRIVAL_DISTANCE / 2;
    /** Tuning value: pixels outside a disc its detour waypoints are placed at; larger than {@link #WAYPOINT_REACHED}. */
    static final int DETOUR_MARGIN = 64;
    /** Tuning value: detour waypoints placed evenly around each disc. */
    static final int DETOUR_ANGLES = 16;
    /**
     * Tuning value: detour waypoints a path search places at most, which bounds its cost however many anti-air
     * threats are known.
     */
    static final int MAX_DETOUR_NODES = 256;
    /** Tuning value: frames between path searches for a reinforcing squad. */
    public static final int REPLAN_FRAMES = 12;

    private AirReinforcement() {
    }

    /**
     * Whether an air squad holding a status is active, and so a reinforcement target: fighting, retreating or
     * harassing.
     *
     * @param status the squad's status
     * @return true for FIGHT, RETREAT and HARASS
     */
    public static boolean isActive(SquadStatus status) {
        return status == SquadStatus.FIGHT || status == SquadStatus.RETREAT || status == SquadStatus.HARASS;
    }

    /**
     * Whether a squad looks for an active air squad to reinforce: a rallying air squad that still holds members. A
     * squad emptied by its own arrival earlier in the frame is left alone.
     *
     * @param status the squad's status
     * @param airSquad true for an air squad
     * @param members units in the squad
     * @return true when the squad may reinforce
     */
    public static boolean seeksReinforcementTarget(SquadStatus status, boolean airSquad, int members) {
        return status == SquadStatus.RALLY && airSquad && members > 0;
    }

    /**
     * Whether a rallying air squad leaves the rally point on its own once it reaches its move out threshold. It
     * does only while no air squad it could reinforce is active; otherwise it reinforces, or waits.
     *
     * @param activeAirSquad true when an air squad it could reinforce is active
     * @return true when the move out threshold applies
     */
    public static boolean moveOutRuleApplies(boolean activeAirSquad) {
        return !activeAirSquad;
    }

    /**
     * The strength a squad needs to launch this tick. A rallying squad whose move out rule is suspended by
     * {@link #moveOutRuleApplies} can never reach it; every other squad keeps its move out threshold.
     *
     * @param moveOutThreshold the squad's move out threshold
     * @param status status the squad held entering the tick
     * @param activeAirSquad true when an air squad the squad could reinforce is active
     * @return threshold to compare the squad's strength against
     */
    public static int launchThreshold(int moveOutThreshold, SquadStatus status, boolean activeAirSquad) {
        if (status == SquadStatus.RALLY && !moveOutRuleApplies(activeAirSquad)) {
            return Integer.MAX_VALUE;
        }
        return moveOutThreshold;
    }

    /**
     * Whether a squad may reinforce an air squad holding a status. A harassing squad takes Mutalisks only, as its
     * entry does; any other active squad takes what it may merge with.
     *
     * @param targetStatus the target squad's status
     * @param composition the reinforcing squad's unit counts by type
     * @return true when the reinforcement is allowed
     */
    public static boolean mayReinforce(SquadStatus targetStatus, Map<UnitType, Integer> composition) {
        return targetStatus != SquadStatus.HARASS || AirHarassEvaluator.mutalisksOnly(composition);
    }

    /**
     * Whether a reinforcing squad has reached its target and joins it.
     *
     * @param distance pixels between the two squad centres
     * @return true within {@link #ARRIVAL_DISTANCE}
     */
    public static boolean arrived(double distance) {
        return distance < ARRIVAL_DISTANCE;
    }

    /**
     * An active air squad a rallying squad could reinforce.
     *
     * @param <T> the squad handle
     */
    @Getter
    public static final class Candidate<T> {
        private final T squad;
        private final SquadStatus status;
        private final Position center;

        public Candidate(T squad, SquadStatus status, Position center) {
            this.squad = squad;
            this.status = status;
            this.center = center;
        }
    }

    /**
     * The squad a reinforcement flies to and the path it takes.
     *
     * @param <T> the squad handle
     */
    @Getter
    public static final class Route<T> {
        private final T target;
        private final List<Position> path;

        Route(T target, List<Position> path) {
            this.target = target;
            this.path = path;
        }
    }

    /**
     * Picks the nearest active candidate a safe path reaches, trying candidates from the nearest out.
     *
     * @param from the reinforcing squad's centre
     * @param candidates air squads it could join
     * @param threats every known anti-air threat
     * @param allowed points a waypoint may sit at
     * @param <T> the squad handle
     * @return the route, or null when no active candidate has a safe path
     */
    public static <T> Route<T> choose(Position from, Collection<Candidate<T>> candidates,
                                      Collection<AirHarassTargeting.AirThreat> threats, Predicate<Position> allowed) {
        List<Candidate<T>> ordered = new ArrayList<>();
        for (Candidate<T> candidate : candidates) {
            if (isActive(candidate.getStatus()) && candidate.getCenter() != null) {
                ordered.add(candidate);
            }
        }
        ordered.sort(Comparator.comparingDouble(candidate -> from.getDistance(candidate.getCenter())));
        for (Candidate<T> candidate : ordered) {
            List<Position> path = safePath(from, candidate.getCenter(), threats, allowed);
            if (path != null) {
                return new Route<>(candidate.getSquad(), path);
            }
        }
        return null;
    }


    /**
     * The shortest path from a point to the goal, or to an arrival point {@link #ARRIVAL_RING} from it, whose every
     * leg keeps {@link #WAYPOINT_REACHED} clear of every anti-air disc. It is searched over detour waypoints placed
     * {@link #DETOUR_MARGIN} outside each disc and arrival points evenly around the goal; a squad at an arrival point
     * is inside {@link #ARRIVAL_DISTANCE} of the goal. A disc that covers the goal is the fight the target squad is
     * already in: a leg may cross it only inside {@link #ARRIVAL_DISTANCE} of the goal, where the squad has joined.
     * Detour waypoints are placed around the discs nearest the straight line first, up to
     * {@link #MAX_DETOUR_NODES}; every leg is still checked against every disc.
     *
     * @param from start point
     * @param goal the target squad's centre
     * @param threats every known anti-air threat
     * @param allowed points a waypoint may sit at
     * @return the waypoints after the start, ending at the goal or an arrival point, or null when no safe path exists
     */
    public static List<Position> safePath(Position from, Position goal, Collection<AirHarassTargeting.AirThreat> threats,
                                          Predicate<Position> allowed) {
        List<Disc> discs = new ArrayList<>();
        for (AirHarassTargeting.AirThreat threat : threats) {
            discs.add(Disc.of(threat, goal));
        }
        if (legClear(from, goal, discs)) {
            return Collections.singletonList(goal);
        }
        List<Position> nodes = new ArrayList<>();
        nodes.add(from);
        nodes.add(goal);
        for (Position point : ring(goal.getX(), goal.getY(), ARRIVAL_RING)) {
            if (allowed.test(point) && outsideAll(point, discs)) {
                nodes.add(point);
            }
        }
        int arrivals = nodes.size();
        List<Disc> nearestFirst = new ArrayList<>(discs);
        nearestFirst.sort(Comparator.comparingDouble(disc -> disc.segmentDistance(from, goal)));
        for (Disc disc : nearestFirst) {
            if (nodes.size() - arrivals >= MAX_DETOUR_NODES) {
                break;
            }
            for (Position point : ring(disc.x, disc.y, disc.radius + DETOUR_MARGIN)) {
                if (allowed.test(point) && outsideAll(point, discs)) {
                    nodes.add(point);
                }
            }
        }
        return search(nodes, arrivals, goal, discs);
    }

    /**
     * The point a reinforcing squad steers for: the first waypoint its centre has not reached yet, or the last.
     *
     * @param center the squad's centre
     * @param path waypoints ending at the goal or an arrival point
     * @return the point to move to
     */
    public static Position steer(Position center, List<Position> path) {
        for (int i = 0; i < path.size() - 1; i++) {
            if (center.getDistance(path.get(i)) > WAYPOINT_REACHED) {
                return path.get(i);
            }
        }
        return path.get(path.size() - 1);
    }

    private static List<Position> ring(int x, int y, double radius) {
        List<Position> points = new ArrayList<>();
        for (int i = 0; i < DETOUR_ANGLES; i++) {
            double angle = 2 * Math.PI * i / DETOUR_ANGLES;
            points.add(new Position(x + (int) Math.round(Math.cos(angle) * radius),
                    y + (int) Math.round(Math.sin(angle) * radius)));
        }
        return points;
    }

    /**
     * A* from node 0 to any of the arrival nodes, 1 up to the given end, over legs {@link #legClear} accepts.
     */
    private static List<Position> search(List<Position> nodes, int arrivals, Position goal, List<Disc> discs) {
        int count = nodes.size();
        double[] cost = new double[count];
        int[] previous = new int[count];
        boolean[] closed = new boolean[count];
        for (int i = 0; i < count; i++) {
            cost[i] = Double.POSITIVE_INFINITY;
            previous[i] = -1;
        }
        cost[0] = 0;
        while (true) {
            int current = -1;
            double best = Double.POSITIVE_INFINITY;
            for (int i = 0; i < count; i++) {
                if (closed[i] || cost[i] == Double.POSITIVE_INFINITY) {
                    continue;
                }
                double estimate = cost[i] + Math.max(0, nodes.get(i).getDistance(goal) - ARRIVAL_RING);
                if (estimate < best) {
                    best = estimate;
                    current = i;
                }
            }
            if (current < 0) {
                return null;
            }
            if (current > 0 && current < arrivals) {
                return unwind(nodes, previous, current);
            }
            closed[current] = true;
            Position at = nodes.get(current);
            for (int next = 1; next < count; next++) {
                if (closed[next]) {
                    continue;
                }
                double through = cost[current] + at.getDistance(nodes.get(next));
                if (through < cost[next] && legClear(at, nodes.get(next), discs)) {
                    cost[next] = through;
                    previous[next] = current;
                }
            }
        }
    }

    private static List<Position> unwind(List<Position> nodes, int[] previous, int end) {
        List<Position> path = new ArrayList<>();
        for (int at = end; at != 0; at = previous[at]) {
            path.add(nodes.get(at));
        }
        Collections.reverse(path);
        return path;
    }

    private static boolean outsideAll(Position point, List<Disc> discs) {
        for (Disc disc : discs) {
            if (disc.blocks(point)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a leg between two points keeps {@link #WAYPOINT_REACHED} clear of every disc, a disc covering the
     * goal excepted inside {@link #ARRIVAL_DISTANCE} of the goal.
     *
     * @param from one end
     * @param to the other end
     * @param discs the discs
     * @return true when the leg is clear
     */
    static boolean legClear(Position from, Position to, List<Disc> discs) {
        for (Disc disc : discs) {
            if (disc.blocks(from, to)) {
                return false;
            }
        }
        return true;
    }

    /**
     * An anti-air threat read as a disc. A disc covering the goal only blocks ground outside
     * {@link #ARRIVAL_DISTANCE} of the goal.
     */
    static final class Disc {
        private final int x;
        private final int y;
        private final double radius;
        private final Position goal;

        Disc(int x, int y, double radius, Position goal) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.goal = goal;
        }

        static Disc of(AirHarassTargeting.AirThreat threat, Position goal) {
            UnitType type = threat.getType();
            double corner = Math.hypot(Math.max(type.dimensionLeft(), type.dimensionRight()),
                    Math.max(type.dimensionUp(), type.dimensionDown()));
            double radius = threat.getReach() + corner + AirHarassTargeting.padding();
            Position center = threat.getPosition();
            boolean coversGoal = Math.hypot(goal.getX() - center.getX(), goal.getY() - center.getY()) <= radius;
            return new Disc(center.getX(), center.getY(), radius, coversGoal ? goal : null);
        }

        boolean holds(Position point, int margin) {
            return Math.hypot(point.getX() - x, point.getY() - y) <= radius + margin;
        }

        boolean blocks(Position point) {
            return holds(point, WAYPOINT_REACHED) && !nearGoal(point.getX(), point.getY());
        }

        /**
         * Whether a leg passes within {@link #WAYPOINT_REACHED} of the disc. For a disc covering the goal, only the
         * part of the leg inside that reach counts, and it blocks unless both of its ends are inside
         * {@link #ARRIVAL_DISTANCE} of the goal; the part is a chord and the arrival area a disc, so the whole part
         * then lies inside it.
         */
        boolean blocks(Position from, Position to) {
            if (goal == null) {
                return segmentDistance(from, to) <= radius + WAYPOINT_REACHED;
            }
            double dx = to.getX() - from.getX();
            double dy = to.getY() - from.getY();
            double fx = from.getX() - x;
            double fy = from.getY() - y;
            double reach = radius + WAYPOINT_REACHED;
            double a = dx * dx + dy * dy;
            double c = fx * fx + fy * fy - reach * reach;
            if (a == 0) {
                return c <= 0 && !nearGoal(from.getX(), from.getY());
            }
            double b = 2 * (fx * dx + fy * dy);
            double discriminant = b * b - 4 * a * c;
            if (discriminant < 0) {
                return false;
            }
            double root = Math.sqrt(discriminant);
            double enter = Math.max(0, (-b - root) / (2 * a));
            double leave = Math.min(1, (-b + root) / (2 * a));
            if (enter > leave) {
                return false;
            }
            return !nearGoal(from.getX() + enter * dx, from.getY() + enter * dy)
                    || !nearGoal(from.getX() + leave * dx, from.getY() + leave * dy);
        }

        private boolean nearGoal(double px, double py) {
            return goal != null && Math.hypot(px - goal.getX(), py - goal.getY()) < ARRIVAL_DISTANCE;
        }

        double segmentDistance(Position from, Position to) {
            double dx = to.getX() - from.getX();
            double dy = to.getY() - from.getY();
            double lengthSquared = dx * dx + dy * dy;
            double t = lengthSquared == 0 ? 0 : ((x - from.getX()) * dx + (y - from.getY()) * dy) / lengthSquared;
            t = Math.max(0, Math.min(1, t));
            return Math.hypot(from.getX() + t * dx - x, from.getY() + t * dy - y);
        }
    }
}
