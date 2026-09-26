package unit.squad;

import bwapi.Position;
import bwapi.Race;
import bwapi.UnitType;
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
 * between the bearings of its outermost points. A squad of at least {@link #MIN_COLLAPSE_MEMBERS} members collapses
 * when at least {@link #MIN_ENEMIES_IN_SECTOR} armed enemies stand in the sector, their centroid is clear of the reach
 * of static defence, sieged tanks and Lurkers, see {@link #fixedFireZones}, and a sim over exactly those enemies
 * reads at or above the matchup engage threshold. No squad collapses against Protoss or Zerg, see
 * {@link #appliesAgainst}.
 *
 * <p>A passed test commits only once it has held, see {@link #gate}: the squad's entry run must reach
 * {@link #ENTRY_EVALUATIONS} passes, see {@link CollapseEntryRun}, and never while a collapse is under way or within
 * {@link #COOLDOWN_FRAMES} frames of the end of its last one. A squad already under fire, see {@link UnderFire},
 * commits on its first pass outside the cooldown.
 *
 * <p>A squad under fire commits at once: every member fights. Otherwise the collapse wraps while the centre fights.
 * The outer third of the squad on each side, by bearing around the choke, are the flanks: each attack-moves to a
 * point past the enemy centroid on the choke side, offset to its own side, fighting what it meets on the way. Every
 * other member fights from the collapse frame. Once every flank has arrived or {@link #WRAP_FRAME_CAP} frames have
 * passed, the flanks fight too.
 */
public final class ContainmentCollapse {

    /** Tuning value: armed enemies that must stand inside the arc's sector before a collapse is considered. */
    static final int MIN_ENEMIES_IN_SECTOR = 3;
    /** Tuning value: members a squad needs to collapse, enough for two flanks on each side and two in the centre. */
    static final int MIN_COLLAPSE_MEMBERS = 6;
    /** Tuning value: pixels past the enemy centroid, toward the choke, that a flank wraps to. */
    static final int WRAP_DEPTH = 64;
    /** Tuning value: pixels a flank's wrap point sits to its own side of the line from the centroid to the choke. */
    static final int WRAP_SPREAD = 48;
    /** Tuning value: pixels from its wrap point at which a flank counts as arrived. */
    static final int FLANK_ARRIVAL_DISTANCE = 96;
    /** Tuning value: frames after a collapse at which the centre commits whether or not the flanks arrived. */
    static final int WRAP_FRAME_CAP = 72;
    /**
     * Tuning value: consecutive containment evaluations on which the collapse test must pass before a collapse
     * commits. A containing squad is evaluated once a frame, so this is one second of a held signal.
     */
    static final int ENTRY_EVALUATIONS = 24;
    /**
     * Tuning value: frames after a collapse ends, when its centre commits or the squad drops the wrap, before the
     * squad may start another. A collapse that could not plan a wrap ends on the frame it starts.
     */
    static final int COOLDOWN_FRAMES = 240;
    /**
     * Tuning value: frames back from a collapse test within which a member losing hit points to a weapon counts as
     * the squad being under fire. One second.
     */
    static final int UNDER_FIRE_FRAMES = 24;
    /**
     * Tuning value: pixels, edge to edge, within which an armed enemy stands in melee contact with a member.
     */
    static final int MELEE_CONTACT_DISTANCE = 32;
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
        LOCK_REFUSED,
        TOO_FEW_MEMBERS,
        UNSUSTAINED,
        COOLING_DOWN
    }

    /**
     * Whether the enemy is already engaging a containing squad when it tests a collapse, and how: a member lost hit
     * points to a weapon within {@link #UNDER_FIRE_FRAMES}, an armed enemy stands within
     * {@link #MELEE_CONTACT_DISTANCE} of a member, or both.
     */
    public enum UnderFire {
        NONE,
        HIT,
        MELEE,
        HIT_AND_MELEE;

        /**
         * @param hit true when a member lost hit points to a weapon within {@link #UNDER_FIRE_FRAMES}
         * @param melee true when an armed enemy stands within {@link #MELEE_CONTACT_DISTANCE} of a member
         * @return the reason, NONE when neither holds
         */
        static UnderFire of(boolean hit, boolean melee) {
            if (hit) {
                return melee ? HIT_AND_MELEE : HIT;
            }
            return melee ? MELEE : NONE;
        }
    }

    /**
     * How the wrap of a collapse ended: skipped because the squad was under fire, every flank arrived, or the wrap
     * ran out its {@link #WRAP_FRAME_CAP} frames.
     */
    public enum WrapEnd {
        SKIPPED,
        ARRIVED,
        CAP
    }

    /**
     * What a member does from the frame of a collapse: fight a target, or attack-move to its flank's wrap point.
     */
    enum MemberOrder {
        FIGHT,
        WRAP
    }

    private ContainmentCollapse() {
    }

    /**
     * The matchup gate for a collapse and for a strong ENGAGE breaking an attrition retreat lock: every opponent
     * race but Protoss and Zerg, against which both fights traded close to nothing.
     *
     * @param opponentRace the opponent's race, Unknown until it is seen
     * @return true when collapses and attrition lock breaks apply against the opponent
     */
    public static boolean appliesAgainst(Race opponentRace) {
        return opponentRace != Race.Protoss && opponentRace != Race.Zerg;
    }

    /**
     * Runs the collapse test on the armed enemies found inside an arc's sector.
     *
     * @param armedInSector positions of the armed enemies inside the sector, see {@link #inSector}
     * @param staticZones enemy static defence zones, at the reach learned over the game
     * @param padding pixels added to every zone's reach
     * @param sectorSim the squad's strength ratio over exactly the enemies in the sector, run only when at least
     *     {@link #MIN_ENEMIES_IN_SECTOR} armed enemies stand there and the squad has at least
     *     {@link #MIN_COLLAPSE_MEMBERS} members
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
        boolean simulated = armedInSector.size() >= MIN_ENEMIES_IN_SECTOR && members >= MIN_COLLAPSE_MEMBERS;
        double ratio = simulated ? sectorSim.getAsDouble() : NOT_SIMULATED;
        Outcome outcome = evaluate(armedInSector.size(), members, staticClear, ratio, engageThreshold,
                lockRenewable);
        return new Read(outcome, armedInSector.size(), ratio, staticClear, flankCount(members), centroid);
    }

    /**
     * Applies the collapse conditions in order.
     *
     * @param armedInSector armed enemies inside the arc's sector
     * @param members members that would take part in the collapse
     * @param staticClear true when their centroid is clear of every static defence zone plus padding
     * @param ratio the squad's strength over exactly the enemies in the sector
     * @param engageThreshold the matchup engage threshold
     * @param lockRenewable true when the squad may arm a fight lock now, see {@link Squad#canRenewFightLock}
     * @return COLLAPSE when every condition holds, else the first that failed
     */
    static Outcome evaluate(int armedInSector, int members, boolean staticClear, double ratio,
                            double engageThreshold, boolean lockRenewable) {
        if (armedInSector < MIN_ENEMIES_IN_SECTOR) {
            return Outcome.TOO_FEW_ENEMIES;
        }
        if (members < MIN_COLLAPSE_MEMBERS) {
            return Outcome.TOO_FEW_MEMBERS;
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
     * The hysteresis gate on a passed collapse test. A squad collapsing or inside its cooldown reads COOLING_DOWN,
     * and a squad not under fire whose entry run has fewer than {@link #ENTRY_EVALUATIONS} passes reads UNSUSTAINED.
     * A squad under fire is not held for the run. Any other outcome passes through.
     *
     * @param outcome the outcome of this evaluation's collapse test
     * @param coolingDown true while the squad is collapsing or inside its cooldown, see {@link Squad#isCollapseLocked}
     * @param passes passes in the squad's entry run, this test included, see {@link CollapseEntryRun#record}
     * @param underFire whether the enemy is already engaging the squad
     * @return COLLAPSE when the passed test has held or the squad is under fire, and the cooldown is over, else the
     *     outcome that held it back
     */
    static Outcome gate(Outcome outcome, boolean coolingDown, int passes, UnderFire underFire) {
        if (outcome != Outcome.COLLAPSE) {
            return outcome;
        }
        if (coolingDown) {
            return Outcome.COOLING_DOWN;
        }
        if (passes < ENTRY_EVALUATIONS && underFire == UnderFire.NONE) {
            return Outcome.UNSUSTAINED;
        }
        return Outcome.COLLAPSE;
    }

    /**
     * What each member does from the frame of a collapse. A squad under fire skips the wrap and every member fights.
     * Otherwise the flanks that can attack-move wrap, see {@link #attackMovesToWrap}, and every other member fights.
     *
     * @param sides per member, -1 or 1 for a flank and 0 for the centre, see {@link #flankSides}
     * @param attackMoves per member, true when its type attack-moves to a wrap point
     * @param underFire whether the enemy was already engaging the squad
     * @return per member, its order
     */
    static MemberOrder[] memberOrders(int[] sides, boolean[] attackMoves, UnderFire underFire) {
        MemberOrder[] orders = new MemberOrder[sides.length];
        for (int i = 0; i < sides.length; i++) {
            orders[i] = sides[i] != 0 && attackMoves[i] && underFire == UnderFire.NONE
                    ? MemberOrder.WRAP
                    : MemberOrder.FIGHT;
        }
        return orders;
    }

    /**
     * Whether a member of a type attack-moves to a wrap point. A Lurker or a Defiler runs its own containing
     * behaviour and ignores the attack-move, so as a flank it fights instead.
     *
     * @param type the member's type
     * @return false for a Lurker or a Defiler
     */
    static boolean attackMovesToWrap(UnitType type) {
        return type != UnitType.Zerg_Lurker && type != UnitType.Zerg_Defiler;
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
     * The zones a collapse keeps its enemy centroid and wrap points out of: those around enemies that fire from where
     * they stand, see {@link ContainmentPushback#movesTheArc}, without the hurt marks. A sieged tank or a Lurker is
     * not in the sector sim, so its reach is kept clear the way a Bunker's is.
     *
     * @param groundThreatZones every ground threat zone, at the reach learned over the game
     * @return the zones around buildings, sieged tanks and Lurkers
     */
    static List<StaticDefenseZone> fixedFireZones(Collection<StaticDefenseZone> groundThreatZones) {
        List<StaticDefenseZone> kept = new ArrayList<>();
        for (StaticDefenseZone zone : ContainmentPushback.arcZones(groundThreatZones)) {
            if (zone.getStructure() != UnitType.None) {
                kept.add(zone);
            }
        }
        return kept;
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
     * Whether the wrap is over and the flanks fight: ARRIVED when every flank still alive is within
     * {@link #FLANK_ARRIVAL_DISTANCE} of its wrap point, else CAP once the wrap has run {@link #WRAP_FRAME_CAP}
     * frames.
     *
     * @param elapsedFrames frames since the collapse
     * @param flankDistances distance of each live flank from its wrap point
     * @return how the wrap ended, or null while it runs
     */
    static WrapEnd wrapEnd(int elapsedFrames, Collection<Double> flankDistances) {
        for (double distance : flankDistances) {
            if (distance > FLANK_ARRIVAL_DISTANCE) {
                return elapsedFrames >= WRAP_FRAME_CAP ? WrapEnd.CAP : null;
            }
        }
        return WrapEnd.ARRIVED;
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
        private final UnderFire underFire;
        private final int runStartFrame;

        Read(Outcome outcome, int enemiesInSector, double ratio, boolean staticClear, int flanks,
             Position enemyCentroid) {
            this(outcome, enemiesInSector, ratio, staticClear, flanks, enemyCentroid, UnderFire.NONE,
                    CollapseEntryRun.NO_RUN);
        }

        private Read(Outcome outcome, int enemiesInSector, double ratio, boolean staticClear, int flanks,
                     Position enemyCentroid, UnderFire underFire, int runStartFrame) {
            this.outcome = outcome;
            this.enemiesInSector = enemiesInSector;
            this.ratio = ratio;
            this.staticClear = staticClear;
            this.flanks = flanks;
            this.enemyCentroid = enemyCentroid;
            this.underFire = underFire;
            this.runStartFrame = runStartFrame;
        }

        /**
         * The same read as the hysteresis gate reports it, see {@link #gate}.
         *
         * @param gated the outcome after the gate
         * @param firing whether the enemy was already engaging the squad
         * @param runStart frame of the first pass of the squad's entry run, {@link CollapseEntryRun#NO_RUN} when none
         * @return the read with the gated outcome
         */
        Read gated(Outcome gated, UnderFire firing, int runStart) {
            return new Read(gated, enemiesInSector, ratio, staticClear, flanks, enemyCentroid, firing, runStart);
        }
    }

    /**
     * A collapse under way: its members, and the wrap point each flank attack-moves to until the wrap ends.
     */
    static final class Maneuver {
        private final Map<ManagedUnit, Position> wraps;
        private final Set<ManagedUnit> members;
        @Getter
        private final int startFrame;

        /**
         * @param wraps the wrap point of each flank
         * @param members every member taking part, flanks included
         * @param startFrame frame of the collapse
         */
        Maneuver(Map<ManagedUnit, Position> wraps, Set<ManagedUnit> members, int startFrame) {
            this.wraps = new HashMap<>(wraps);
            this.members = new HashSet<>(members);
            this.members.addAll(wraps.keySet());
            this.startFrame = startFrame;
        }

        /**
         * @param member a squad member
         * @return the member's wrap point, or null when it is not a flank
         */
        Position wrapFor(ManagedUnit member) {
            return wraps.get(member);
        }

        Set<ManagedUnit> getMembers() {
            return Collections.unmodifiableSet(members);
        }

        /**
         * Distance of each flank still in the squad from its wrap point.
         *
         * @param squadMembers the squad's current members
         * @return the distances
         */
        List<Double> flankDistances(Collection<ManagedUnit> squadMembers) {
            List<Double> distances = new ArrayList<>();
            for (ManagedUnit member : squadMembers) {
                Position wrap = wraps.get(member);
                if (wrap != null) {
                    distances.add(member.getPosition().getDistance(wrap));
                }
            }
            return distances;
        }
    }
}
