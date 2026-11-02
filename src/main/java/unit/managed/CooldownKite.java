package unit.managed;

import bwapi.Position;
import bwapi.UnitType;
import lombok.Getter;
import util.MapEdge;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans one weapon cooldown of a Mutalisk that fights: whether it steps back, to where, and when it turns back to its
 * target. The geometry is static over plain values; an instance holds the plan of the cooldown under way and drops
 * it when the next shot fires, or when the Mutalisk's role or fight target changes.
 *
 * <p>Distances are measured centre to centre. A threat's reach is its air range plus its largest half extent plus the
 * Mutalisk's largest half extent, so a point outside it is outside the threat's range edge to edge. The Mutalisk's
 * reach to its target uses the smallest half extents instead, so a Mutalisk counted as back in range is in range.
 *
 * <p>The rule, see {@link #plan(Position, Position, int, List, double, double, int, int)}: only counted threats whose
 * reach covers the Mutalisk matter. A static threat always counts; a mobile threat counts only when it is slower than
 * the Mutalisk, since backing off from one as fast gains no distance on it. The back-off point must clear every such
 * reach, else there is no back-off and the Mutalisk keeps attacking. Against a static threat the clearing point is
 * taken even when the flight back costs time after the cooldown ends; against mobile threats alone it is taken only
 * when the flight out and back fits in the cooldown.
 */
public final class CooldownKite {

    /**
     * Tuning value: pixels a back-off point aims beyond every reach it leaves, the same cover margin the air harass
     * keeps from anti-air zones. A point aimed exactly at the reach edge is re-entered by the first pixel either unit
     * moves.
     */
    public static final int MARGIN = 16;
    /** Tuning value: pixels a threat's reach is grown by when deciding whether it is near enough to matter. */
    public static final int NEAR_MARGIN = MARGIN;
    /** Tuning value: pixels from the back-off point at which the Mutalisk counts as arrived. */
    public static final int ARRIVAL_DISTANCE = 8;
    /**
     * Tuning value: the longest back-off, in pixels, the same 256 a fighting Mutalisk flees with the kite off, see
     * {@link Mutalisk#retreatFleeDistance}.
     */
    public static final int MAX_BACK_OFF = 256;
    /** Tuning value: degrees either side of the straight-away direction that a back-off may turn, tried in steps. */
    static final int[] TURNS = {0, 30, -30, 60, -60, 90, -90};
    /**
     * Assumption, not measured or sourced: the share of its top speed a Mutalisk is planned to keep over a leg that
     * starts with a turn and from rest. JBWAPI gives no units for UnitType acceleration and turn radius, so they are
     * not converted; half the top speed makes the plan turn back early rather than late. Read against the control
     * with ready_frames_out_of_range per cycle.
     */
    public static final double SPEED_SHARE = 0.5;
    /**
     * Tuning value: frames the plan sets aside for the two turns of an out-and-back leg, on top of the command latency
     * and the frames between two orders. Sized to the frames per cycle that Mutalisks which back off stand ready out of
     * range beyond those which never back off, read with SPEED_SHARE and ready_frames_out_of_range per cycle.
     */
    public static final int TURN_FRAMES = 6;

    private int cycleFireFrame = -1;
    private Object cycleRole;
    private int cycleTargetId = -1;
    private boolean planned;
    /** How this cooldown's plan came out, null until it is planned. */
    @Getter
    private Outcome outcome;
    @Getter
    private Position point;
    @Getter
    private Threat threat;
    @Getter
    private boolean committed;

    /**
     * A unit that can hit the Mutalisk: its position, its reach centre to centre, and whether it moves and how fast.
     */
    @Getter
    public static final class Threat {
        private final Position position;
        private final int reach;
        private final boolean mobile;
        private final double topSpeed;

        public Threat(Position position, int reach) {
            this(position, reach, false, 0);
        }

        public Threat(Position position, int reach, boolean mobile, double topSpeed) {
            this.position = position;
            this.reach = reach;
            this.mobile = mobile;
            this.topSpeed = topSpeed;
        }

        /**
         * @param point a point
         * @return pixels from the point to the edge of the reach, negative inside it
         */
        public double margin(Position point) {
            return point.getDistance(position) - reach;
        }

        /**
         * @param ownTopSpeed the Mutalisk's top speed
         * @return true when backing off can gain distance on this threat: it is static, or slower than the Mutalisk
         */
        public boolean counts(double ownTopSpeed) {
            return !mobile || topSpeed < ownTopSpeed;
        }
    }

    /**
     * How a cooldown's plan came out.
     */
    public enum Outcome {
        /** No counted threat covered the Mutalisk; it keeps attacking. */
        NO_THREAT,
        /** Counted threats covered it but no back-off clears them; it keeps attacking. */
        NO_CLEAR,
        /** It backs off to a point that clears every counted threat. */
        BACK_OFF
    }

    /**
     * A cooldown's plan: the outcome, the back-off point, the widest counted threat that covered the Mutalisk, and
     * whether the back-off is committed: a static threat's clearing point whose flight out and back does not fit in
     * the cooldown, which the Mutalisk flies all the way to before it turns back.
     */
    @Getter
    public static final class Plan {
        private final Outcome outcome;
        private final Position point;
        private final Threat widest;
        private final boolean committed;

        Plan(Outcome outcome, Position point, Threat widest, boolean committed) {
            this.outcome = outcome;
            this.point = point;
            this.widest = widest;
            this.committed = committed;
        }
    }

    /**
     * Starts a new cooldown when the weapon fired on another frame, or the Mutalisk's role or fight target changed
     * since the plan was made, dropping the back-off point so it is planned again from where the Mutalisk is now.
     *
     * @param fireFrame frame the Mutalisk's weapon last fired, -1 when it never has
     * @param role the Mutalisk's role
     * @param targetId its fight target's id, -1 for none
     * @return true when a new cooldown started
     */
    public boolean begin(int fireFrame, Object role, int targetId) {
        if (fireFrame == cycleFireFrame && role == cycleRole && targetId == cycleTargetId) {
            return false;
        }
        cycleFireFrame = fireFrame;
        cycleRole = role;
        cycleTargetId = targetId;
        reset();
        return true;
    }

    /**
     * Drops the plan, so the next ready frame plans the cooldown again.
     */
    public void reset() {
        planned = false;
        outcome = null;
        point = null;
        threat = null;
        committed = false;
    }

    /**
     * @return true once this cooldown's back-off has been planned, whether or not it found a point
     */
    public boolean isPlanned() {
        return planned;
    }

    /**
     * Records this cooldown's plan.
     *
     * @param plan the plan
     */
    public void accept(Plan plan) {
        planned = true;
        outcome = plan.getOutcome();
        point = plan.getPoint();
        threat = plan.getWidest();
        committed = plan.isCommitted();
    }

    /**
     * Drops the back-off point, so the Mutalisk turns back to its target for the rest of this cooldown.
     */
    public void turnBack() {
        point = null;
    }

    /**
     * What a fighting Mutalisk does on a frame it is ready.
     */
    public enum Step {
        /** Its attack animation is playing; no order is issued. */
        WAIT,
        /** Fly to the back-off point. */
        BACK_OFF,
        /** Stay at the back-off point. */
        HOLD,
        /** Attack the target. */
        ATTACK
    }

    /**
     * Picks the step of a fighting Mutalisk with a target.
     *
     * @param attackFrame true while the attack animation plays
     * @param cooldown frames left on the weapon cooldown
     * @param point this cooldown's back-off point, or null
     * @param arrived true when the Mutalisk stands at the point
     * @param turnBack true when the rest of the cooldown is only enough to fly back in range
     * @return the step
     */
    public static Step step(boolean attackFrame, int cooldown, Position point, boolean arrived, boolean turnBack) {
        if (attackFrame) {
            return Step.WAIT;
        }
        if (cooldown <= 0 || point == null || turnBack) {
            return Step.ATTACK;
        }
        return arrived ? Step.HOLD : Step.BACK_OFF;
    }

    /**
     * @param airRange the threat's air range in pixels
     * @param threatHalfExtent the threat's largest half extent
     * @param ownHalfExtent the Mutalisk's largest half extent
     * @return the threat's reach centre to centre
     */
    public static int reach(int airRange, int threatHalfExtent, int ownHalfExtent) {
        return airRange + threatHalfExtent + ownHalfExtent;
    }

    /**
     * @param type a unit type
     * @return its largest half extent in pixels
     */
    public static int largestHalfExtent(UnitType type) {
        return Math.max(Math.max(type.dimensionLeft(), type.dimensionRight()),
                Math.max(type.dimensionUp(), type.dimensionDown()));
    }

    /**
     * @param type a unit type
     * @return its smallest half extent in pixels
     */
    public static int smallestHalfExtent(UnitType type) {
        return Math.min(Math.min(type.dimensionLeft(), type.dimensionRight()),
                Math.min(type.dimensionUp(), type.dimensionDown()));
    }

    /**
     * @param threats threats around the Mutalisk
     * @param self the Mutalisk's position
     * @param ownTopSpeed the Mutalisk's top speed
     * @return the counted threats, see {@link Threat#counts}, whose reach grown by {@link #NEAR_MARGIN} covers it
     */
    public static List<Threat> near(List<Threat> threats, Position self, double ownTopSpeed) {
        List<Threat> near = new ArrayList<>();
        for (Threat threat : threats) {
            if (threat.counts(ownTopSpeed) && threat.margin(self) < NEAR_MARGIN) {
                near.add(threat);
            }
        }
        return near;
    }

    /**
     * Whether a static threat's reach grown by {@link #NEAR_MARGIN} covers the Mutalisk, so a cooldown planned with
     * no counted threat must be planned again.
     *
     * @param threats threats around the Mutalisk
     * @param self the Mutalisk's position
     * @return true when one does
     */
    public static boolean staticThreatNear(List<Threat> threats, Position self) {
        for (Threat threat : threats) {
            if (!threat.isMobile() && threat.margin(self) < NEAR_MARGIN) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param threats threats
     * @return the one with the largest reach, or null when there are none
     */
    public static Threat widest(List<Threat> threats) {
        Threat widest = null;
        for (Threat threat : threats) {
            if (widest == null || threat.getReach() > widest.getReach()) {
                widest = threat;
            }
        }
        return widest;
    }

    /**
     * Pixels a Mutalisk is planned to fly in a number of frames, see {@link #SPEED_SHARE}.
     *
     * @param topSpeed top speed in pixels per frame
     * @param frames frames of flight
     * @return pixels, never negative
     */
    public static double flight(double topSpeed, int frames) {
        return Math.max(0, frames) * topSpeed * SPEED_SHARE;
    }

    /**
     * Plans the cooldown, see the class description.
     *
     * @param self the Mutalisk's position
     * @param target its target's position
     * @param targetReach its reach to the target centre to centre
     * @param threats every threat around it, counted or not
     * @param ownTopSpeed its top speed in pixels per frame
     * @param budget pixels it can fly before the cooldown ends, see {@link #flight}
     * @param mapWidth map width in pixels
     * @param mapHeight map height in pixels
     * @return the plan
     */
    public static Plan plan(Position self, Position target, int targetReach, List<Threat> threats,
                            double ownTopSpeed, double budget, int mapWidth, int mapHeight) {
        List<Threat> near = near(threats, self, ownTopSpeed);
        if (near.isEmpty()) {
            return new Plan(Outcome.NO_THREAT, null, null, false);
        }
        Threat widest = widest(near);
        Position point = clearingPoint(self, target, targetReach, near, mapWidth, mapHeight);
        if (point == null) {
            return new Plan(Outcome.NO_CLEAR, null, widest, false);
        }
        boolean anyStatic = near.stream().anyMatch(t -> !t.isMobile());
        boolean overBudget = outAndBack(self, point, target, targetReach) > budget;
        if (!anyStatic && overBudget) {
            return new Plan(Outcome.NO_CLEAR, null, widest, false);
        }
        return new Plan(Outcome.BACK_OFF, point, widest, overBudget);
    }

    /**
     * The back-off point with the shortest flight out and back that clears every near threat's reach: tried straight
     * away from them, weighted by how deep the Mutalisk stands in each, and turned by each of {@link #TURNS}, each
     * flown until it lies {@link #MARGIN} beyond every reach, no further than {@link #MAX_BACK_OFF}, and moved inside
     * the map, see {@link MapEdge#toward}. A candidate the map edge or the length limit leaves inside a reach is
     * dropped.
     *
     * @param self the Mutalisk's position
     * @param target its target's position
     * @param targetReach its reach to the target centre to centre
     * @param near the threats to clear
     * @param mapWidth map width in pixels
     * @param mapHeight map height in pixels
     * @return the point, or null when none clears
     */
    static Position clearingPoint(Position self, Position target, int targetReach, List<Threat> near, int mapWidth,
                                  int mapHeight) {
        double[] away = awayFrom(self, target, near);
        if (away == null) {
            return null;
        }
        Position best = null;
        double bestCost = Double.MAX_VALUE;
        for (int turn : TURNS) {
            double[] direction = rotate(away, Math.toRadians(turn));
            double length = 0;
            for (Threat threat : near) {
                length = Math.max(length, exitDistance(self, direction, threat.getPosition(),
                        threat.getReach() + MARGIN));
            }
            if (length <= 0 || length > MAX_BACK_OFF) {
                continue;
            }
            Position candidate = MapEdge.toward(self, new Position(
                    self.getX() + (int) Math.round(direction[0] * Math.ceil(length)),
                    self.getY() + (int) Math.round(direction[1] * Math.ceil(length))), mapWidth, mapHeight);
            if (candidate.equals(self) || !clearsAll(candidate, near)) {
                continue;
            }
            double cost = outAndBack(self, candidate, target, targetReach);
            if (cost < bestCost) {
                best = candidate;
                bestCost = cost;
            }
        }
        return best;
    }

    /**
     * @param point a point
     * @param threats threats
     * @return true when the point lies outside the reach of every one of them
     */
    static boolean clearsAll(Position point, List<Threat> threats) {
        for (Threat threat : threats) {
            if (threat.margin(point) <= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param self where the leg starts
     * @param point the back-off point
     * @param target the target's position
     * @param targetReach reach to the target centre to centre
     * @return pixels flown out to the point and back to within reach of the target
     */
    static double outAndBack(Position self, Position point, Position target, int targetReach) {
        return self.getDistance(point) + Math.max(0, point.getDistance(target) - targetReach);
    }

    /**
     * Whether the Mutalisk must turn back now to be within its reach of the target when the cooldown ends.
     *
     * @param self the Mutalisk's position
     * @param target its target's position
     * @param targetReach its reach to the target centre to centre
     * @param topSpeed its top speed in pixels per frame
     * @param framesLeft frames left on the cooldown, after the frames set aside for latency and turns
     * @return true when the flight back takes all the frames left
     */
    public static boolean mustTurnBack(Position self, Position target, int targetReach, double topSpeed,
                                       int framesLeft) {
        double back = Math.max(0, self.getDistance(target) - targetReach);
        return back >= flight(topSpeed, framesLeft);
    }

    private static double[] rotate(double[] direction, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new double[] {direction[0] * cos - direction[1] * sin, direction[0] * sin + direction[1] * cos};
    }

    private static double[] awayFrom(Position self, Position target, List<Threat> near) {
        double x = 0;
        double y = 0;
        for (Threat threat : near) {
            double dx = self.getX() - threat.getPosition().getX();
            double dy = self.getY() - threat.getPosition().getY();
            double length = Math.sqrt(dx * dx + dy * dy);
            if (length == 0) {
                continue;
            }
            double depth = Math.max(1, threat.getReach() + MARGIN - length);
            x += dx / length * depth;
            y += dy / length * depth;
        }
        if (x == 0 && y == 0) {
            x = self.getX() - target.getX();
            y = self.getY() - target.getY();
        }
        double length = Math.sqrt(x * x + y * y);
        if (length == 0) {
            return null;
        }
        return new double[] {x / length, y / length};
    }

    /**
     * @return pixels along the unit direction from the start until the point lies a radius from the centre, 0 when it
     *     already does
     */
    static double exitDistance(Position start, double[] direction, Position centre, double radius) {
        double dx = start.getX() - centre.getX();
        double dy = start.getY() - centre.getY();
        double along = dx * direction[0] + dy * direction[1];
        double inside = dx * dx + dy * dy - radius * radius;
        if (inside >= 0) {
            return 0;
        }
        return -along + Math.sqrt(along * along - inside);
    }
}
