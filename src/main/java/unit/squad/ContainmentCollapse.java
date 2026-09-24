package unit.squad;

import bwapi.Position;
import lombok.Getter;
import unit.managed.ManagedUnit;
import util.StaticDefenseZone;
import util.Vec2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleSupplier;

/**
 * Decides whether a containing squad collapses its arc onto the enemies that have walked into it, and plans how.
 *
 * <p>The arc is drawn around the choke and faces away from the enemy base, so an enemy that has come through the
 * choke stands inside the bowl the arc makes. The sector is that bowl: within the arc's reach of the choke and
 * between the bearings of its outermost points. A squad collapses when at least {@link #MIN_ENEMIES_IN_SECTOR}
 * armed enemies stand in the sector, their centroid is clear of static defence reach, and a sim over exactly those
 * enemies reads at or above the matchup engage threshold.
 *
 * <p>The collapse wraps before it commits. The outer third of the squad on each side, by bearing around the choke,
 * are the flanks: each moves to a point past the enemy centroid on the choke side, offset to its own side. The rest
 * hold their points until every flank has arrived or {@link #WRAP_FRAME_CAP} frames have passed, then the whole
 * squad fights.
 */
public final class ContainmentCollapse {

    /** Tuning value: armed enemies that must stand inside the arc's sector before a collapse is considered. */
    static final int MIN_ENEMIES_IN_SECTOR = 3;
    /** Tuning value: pixels past the enemy centroid, toward the choke, that a flank wraps to. */
    static final int WRAP_DEPTH = 64;
    /** Tuning value: pixels a flank's wrap point sits to its own side of the line from the centroid to the choke. */
    static final int WRAP_SPREAD = 48;
    /** Tuning value: pixels from its wrap point at which a flank counts as arrived. */
    static final int FLANK_ARRIVAL_DISTANCE = 96;
    /** Tuning value: frames after a collapse at which the centre commits whether or not the flanks arrived. */
    static final int WRAP_FRAME_CAP = 72;
    /** Ratio a read reports when too few armed enemies stood in the sector for the sim to run. */
    static final double NOT_SIMULATED = -1;

    /**
     * What a collapse test concluded. Every value but COLLAPSE names the first condition that failed.
     */
    public enum Outcome {
        COLLAPSE,
        TOO_FEW_ENEMIES,
        STATIC_COVERED,
        SIM_UNFAVOURABLE,
        LOCK_REFUSED
    }

    private ContainmentCollapse() {
    }

    /**
     * Runs the collapse test on the armed enemies found inside an arc's sector.
     *
     * @param armedInSector positions of the armed enemies inside the sector, see {@link #inSector}
     * @param staticZones enemy static defence zones, at the reach learned over the game
     * @param padding pixels added to every zone's reach
     * @param sectorSim the squad's strength ratio over exactly the enemies in the sector, run only when at least
     *     {@link #MIN_ENEMIES_IN_SECTOR} armed enemies stand there
     * @param engageThreshold the matchup engage threshold
     * @param lockRenewable true when the squad may arm a fight lock now, see {@link Squad#canRenewFightLock}
     * @param members members that would take part in the collapse
     * @return the read, or null when no armed enemy stands inside the sector
     */
    static Read read(List<Position> armedInSector, Collection<StaticDefenseZone> staticZones, int padding,
                     DoubleSupplier sectorSim, double engageThreshold, boolean lockRenewable, int members) {
        if (armedInSector.isEmpty()) {
            return null;
        }
        Position centroid = centroid(armedInSector);
        boolean staticClear = clearOfStaticDefence(centroid, staticZones, padding);
        double ratio = armedInSector.size() >= MIN_ENEMIES_IN_SECTOR ? sectorSim.getAsDouble() : NOT_SIMULATED;
        Outcome outcome = evaluate(armedInSector.size(), staticClear, ratio, engageThreshold, lockRenewable);
        return new Read(outcome, armedInSector.size(), ratio, staticClear, flankCount(members), centroid);
    }

    /**
     * Applies the collapse conditions in order.
     *
     * @param armedInSector armed enemies inside the arc's sector
     * @param staticClear true when their centroid is clear of every static defence zone plus padding
     * @param ratio the squad's strength over exactly the enemies in the sector
     * @param engageThreshold the matchup engage threshold
     * @param lockRenewable true when the squad may arm a fight lock now, see {@link Squad#canRenewFightLock}
     * @return COLLAPSE when every condition holds, else the first that failed
     */
    static Outcome evaluate(int armedInSector, boolean staticClear, double ratio, double engageThreshold,
                            boolean lockRenewable) {
        if (armedInSector < MIN_ENEMIES_IN_SECTOR) {
            return Outcome.TOO_FEW_ENEMIES;
        }
        if (!staticClear) {
            return Outcome.STATIC_COVERED;
        }
        if (ratio < engageThreshold) {
            return Outcome.SIM_UNFAVOURABLE;
        }
        if (!lockRenewable) {
            return Outcome.LOCK_REFUSED;
        }
        return Outcome.COLLAPSE;
    }

    /**
     * Whether a position stands inside the sector of an arc: no farther from the choke than the arc's farthest
     * point, and between the bearings of its outermost points as seen from the choke.
     *
     * @param choke center the arc is drawn around
     * @param arcPoints computed points of the arc
     * @param position position to test
     * @return true when the position is inside the sector, false for an arc of fewer than two points
     */
    static boolean inSector(Position choke, List<Position> arcPoints, Position position) {
        if (arcPoints.size() < 2) {
            return false;
        }
        double reach = 0;
        double sumX = 0;
        double sumY = 0;
        for (Position point : arcPoints) {
            reach = Math.max(reach, choke.getDistance(point));
            sumX += point.getX() - choke.getX();
            sumY += point.getY() - choke.getY();
        }
        if (choke.getDistance(position) > reach) {
            return false;
        }
        double facing = Math.atan2(sumY, sumX);
        double low = Double.MAX_VALUE;
        double high = -Double.MAX_VALUE;
        for (Position point : arcPoints) {
            double offset = bearingOffset(choke, point, facing);
            low = Math.min(low, offset);
            high = Math.max(high, offset);
        }
        double offset = bearingOffset(choke, position, facing);
        return offset >= low && offset <= high;
    }

    private static double bearingOffset(Position from, Position to, double facing) {
        double bearing = Math.atan2(to.getY() - from.getY(), to.getX() - from.getX());
        double offset = bearing - facing;
        while (offset > Math.PI) {
            offset -= 2 * Math.PI;
        }
        while (offset < -Math.PI) {
            offset += 2 * Math.PI;
        }
        return offset;
    }

    /**
     * Centroid of a set of positions.
     *
     * @param positions positions to average
     * @return the centroid, or null when there are none
     */
    static Position centroid(Collection<Position> positions) {
        if (positions.isEmpty()) {
            return null;
        }
        long x = 0;
        long y = 0;
        for (Position position : positions) {
            x += position.getX();
            y += position.getY();
        }
        return new Position((int) (x / positions.size()), (int) (y / positions.size()));
    }

    /**
     * Whether a position is out of reach of every static defence zone plus the padding.
     *
     * @param position position to test
     * @param zones enemy static defence zones, at the reach learned over the game
     * @param padding pixels added to every zone's reach
     * @return true when no zone covers the position
     */
    static boolean clearOfStaticDefence(Position position, Collection<StaticDefenseZone> zones, int padding) {
        for (StaticDefenseZone zone : zones) {
            if (zone.covers(position, padding)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Number of members that flank in a collapse: the outer third on each side.
     *
     * @param members members taking part
     * @return flank count, 0 for a squad of fewer than three
     */
    static int flankCount(int members) {
        return 2 * (members / 3);
    }

    /**
     * Splits members into flanks and centre by their bearing around the choke. Bearings are measured from the
     * direction of the arc's midpoint; the outer third with the lowest bearings are one flank and the outer third
     * with the highest the other.
     *
     * @param choke center the arc is drawn around
     * @param arcMidpoint centroid of the arc's points
     * @param positions member positions
     * @return per position, -1 or 1 for a flank and 0 for the centre
     */
    static int[] flankSides(Position choke, Position arcMidpoint, List<Position> positions) {
        int[] sides = new int[positions.size()];
        int perSide = flankCount(positions.size()) / 2;
        if (perSide == 0) {
            return sides;
        }
        double facing = Math.atan2(arcMidpoint.getY() - choke.getY(), arcMidpoint.getX() - choke.getX());
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingDouble(i -> bearingOffset(choke, positions.get(i), facing)));
        for (int i = 0; i < perSide; i++) {
            sides[order.get(i)] = -1;
            sides[order.get(order.size() - 1 - i)] = 1;
        }
        return sides;
    }

    /**
     * Where a flank wraps to: {@link #WRAP_DEPTH} past the enemy centroid toward the choke, and
     * {@link #WRAP_SPREAD} to the flank's own side of that line. With the centroid on the choke the wrap continues
     * the line from the arc's midpoint through the centroid.
     *
     * @param choke center the arc is drawn around
     * @param enemyCentroid centroid of the enemies in the sector
     * @param arcMidpoint centroid of the arc's points
     * @param flankCenter mean position of the flank's members
     * @return the wrap point, or the centroid when no direction can be taken
     */
    static Position wrapPoint(Position choke, Position enemyCentroid, Position arcMidpoint, Position flankCenter) {
        Vec2 depth = Vec2.between(enemyCentroid, choke).normalize();
        if (depth.length() == 0) {
            depth = Vec2.between(arcMidpoint, enemyCentroid).normalize();
        }
        if (depth.length() == 0) {
            return enemyCentroid;
        }
        Vec2 side = depth.perpendicular();
        Vec2 toFlank = Vec2.between(enemyCentroid, flankCenter);
        double sign = toFlank.x * side.x + toFlank.y * side.y < 0 ? -1 : 1;
        Vec2 offset = new Vec2(depth.x * WRAP_DEPTH + side.x * sign * WRAP_SPREAD,
                depth.y * WRAP_DEPTH + side.y * sign * WRAP_SPREAD);
        return offset.toPosition(enemyCentroid);
    }

    /**
     * Whether the wrap is over and the centre commits: every flank still alive is within
     * {@link #FLANK_ARRIVAL_DISTANCE} of its wrap point, or the wrap has run {@link #WRAP_FRAME_CAP} frames.
     *
     * @param elapsedFrames frames since the collapse
     * @param flankDistances distance of each live flank from its wrap point
     * @return true when the centre commits
     */
    static boolean wrapComplete(int elapsedFrames, Collection<Double> flankDistances) {
        if (elapsedFrames >= WRAP_FRAME_CAP) {
            return true;
        }
        for (double distance : flankDistances) {
            if (distance > FLANK_ARRIVAL_DISTANCE) {
                return false;
            }
        }
        return true;
    }

    /**
     * One collapse test on a containing squad, with every value the telemetry reports.
     */
    @Getter
    static final class Read {
        private final Outcome outcome;
        private final int enemiesInSector;
        private final double ratio;
        private final boolean staticClear;
        private final int flanks;
        private final Position enemyCentroid;

        Read(Outcome outcome, int enemiesInSector, double ratio, boolean staticClear, int flanks,
             Position enemyCentroid) {
            this.outcome = outcome;
            this.enemiesInSector = enemiesInSector;
            this.ratio = ratio;
            this.staticClear = staticClear;
            this.flanks = flanks;
            this.enemyCentroid = enemyCentroid;
        }
    }

    /**
     * A collapse under way: the point each member holds or wraps to until the centre commits.
     */
    static final class Maneuver {
        private final Map<ManagedUnit, Position> orders;
        private final Set<ManagedUnit> flanks;
        @Getter
        private final int startFrame;

        Maneuver(Map<ManagedUnit, Position> orders, Set<ManagedUnit> flanks, int startFrame) {
            this.orders = new HashMap<>(orders);
            this.flanks = new HashSet<>(flanks);
            this.startFrame = startFrame;
        }

        Position orderFor(ManagedUnit member) {
            return orders.get(member);
        }

        Set<ManagedUnit> getMembers() {
            return Collections.unmodifiableSet(orders.keySet());
        }

        /**
         * Distance of each flank still in the squad from its wrap point.
         *
         * @param members the squad's current members
         * @return the distances
         */
        List<Double> flankDistances(Collection<ManagedUnit> members) {
            List<Double> distances = new ArrayList<>();
            for (ManagedUnit member : members) {
                if (flanks.contains(member)) {
                    distances.add(member.getPosition().getDistance(orders.get(member)));
                }
            }
            return distances;
        }
    }
}
