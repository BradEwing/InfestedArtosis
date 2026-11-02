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
 * it when the next shot fires.
 *
 * <p>Distances are measured centre to centre. A threat's reach is its air range plus its largest half extent plus the
 * Mutalisk's largest half extent, so a point outside it is outside the threat's range edge to edge. The Mutalisk's
 * reach to its target uses the smallest half extents instead, so a Mutalisk counted as back in range is in range.
 */
public final class CooldownKite {

    /**
     * Tuning value: pixels a back-off point keeps beyond the largest reach that covers the Mutalisk, the same cover
     * margin the air harass keeps from anti-air zones. A point exactly on the reach edge is re-entered by the first
     * pixel either unit moves.
     */
    public static final int MARGIN = 16;
    /** Tuning value: pixels a threat's reach is grown by when deciding whether it is near enough to matter. */
    public static final int NEAR_MARGIN = MARGIN;
    /** Tuning value: pixels from the back-off point at which the Mutalisk counts as arrived. */
    public static final int ARRIVAL_DISTANCE = 8;
    /** Tuning value: step, in pixels, by which a back-off that cannot be flown out and back is shortened. */
    static final int SHORTEN_STEP = 4;
    /**
     * Assumption, not measured: share of its top speed a Mutalisk is planned to keep over a leg that starts with a turn
     * and from rest. The game's acceleration and turn rate are not converted here, so the plan takes half.
     */
    public static final double SPEED_SHARE = 0.5;
    /**
     * Assumption, not measured: frames the plan sets aside for the two turns of an out-and-back leg, on top of the
     * command latency and the frames between two orders.
     */
    public static final int TURN_FRAMES = 2;

    private int cycleFireFrame = -1;
    private boolean planned;
    @Getter
    private Position point;
    @Getter
    private Threat threat;

    /**
     * A unit that can hit the Mutalisk, as its position and its reach centre to centre.
     */
    @Getter
    public static final class Threat {
        private final Position position;
        private final int reach;

        public Threat(Position position, int reach) {
            this.position = position;
            this.reach = reach;
        }

        /**
         * @param point a point
         * @return pixels from the point to the edge of the reach, negative inside it
         */
        public double margin(Position point) {
            return point.getDistance(position) - reach;
        }
    }

    /**
     * Starts a new cooldown when the weapon fired on a frame other than the one the plan was made for, dropping the
     * back-off point so it is planned again from where the Mutalisk is now.
     *
     * @param fireFrame frame the Mutalisk's weapon last fired, -1 when it never has
     * @return true when a new cooldown started
     */
    public boolean begin(int fireFrame) {
        if (fireFrame == cycleFireFrame) {
            return false;
        }
        cycleFireFrame = fireFrame;
        planned = false;
        point = null;
        threat = null;
        return true;
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
     * @param backOff the back-off point, or null to stay on the target
     * @param widest the threat with the largest reach that covers the Mutalisk, or null
     */
    public void plan(Position backOff, Threat widest) {
        planned = true;
        point = backOff;
        threat = widest;
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
     * @return the threats whose reach grown by {@link #NEAR_MARGIN} covers the Mutalisk
     */
    public static List<Threat> near(List<Threat> threats, Position self) {
        List<Threat> near = new ArrayList<>();
        for (Threat threat : threats) {
            if (threat.margin(self) < NEAR_MARGIN) {
                near.add(threat);
            }
        }
        return near;
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
     * Where the Mutalisk steps back to for this cooldown: away from the near threats, weighted by how deep it stands
     * in each, to {@link #MARGIN} outside every one of them. A back-off it could not fly out and back from before the
     * cooldown ends, back to within its reach of the target, is shortened until it can; one shortened to nothing is
     * no back-off. A point beyond the map is moved inside it, see {@link MapEdge#toward}.
     *
     * @param self the Mutalisk's position
     * @param target its target's position
     * @param targetReach its reach to the target centre to centre
     * @param near threats whose reach covers it, see {@link #near}
     * @param budget pixels it can fly before the cooldown ends, see {@link #flight}
     * @param mapWidth map width in pixels
     * @param mapHeight map height in pixels
     * @return the back-off point, or null to stay on the target
     */
    public static Position backOffPoint(Position self, Position target, int targetReach, List<Threat> near,
                                        double budget, int mapWidth, int mapHeight) {
        if (near.isEmpty() || budget <= 0) {
            return null;
        }
        double[] away = awayFrom(self, target, near);
        if (away == null) {
            return null;
        }
        double needed = 0;
        for (Threat threat : near) {
            needed = Math.max(needed, exitDistance(self, away, threat.getPosition(), threat.getReach() + MARGIN));
        }
        for (double length = Math.ceil(needed); length > 0; length -= SHORTEN_STEP) {
            Position candidate = MapEdge.toward(self, new Position(self.getX() + (int) Math.round(away[0] * length),
                    self.getY() + (int) Math.round(away[1] * length)), mapWidth, mapHeight);
            if (outAndBack(self, candidate, target, targetReach) <= budget) {
                return candidate.equals(self) ? null : candidate;
            }
        }
        return null;
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
