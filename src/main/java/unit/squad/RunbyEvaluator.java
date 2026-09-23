package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import lombok.Builder;
import lombok.Getter;
import unit.squad.horizon.UnitStrength;
import util.Filter;
import util.StaticDefenseZone;
import util.TravelTime;

import java.util.Collections;
import java.util.List;

/**
 * Squad level decisions of a runby: whether a containing squad runs by, whether it aborts on overwhelming
 * enemy strength while that is still allowed, when PENETRATE ends, and when the target base has nothing left.
 *
 * <p>Every decision is a static function over plain values, so it can be tested without a live game. The
 * constants are tuning hypotheses, not Brood War facts; speeds, ranges and strengths are read from JBWAPI.
 */
public final class RunbyEvaluator {

    static final int MIN_LINGS = 6;
    static final int FRESH_FRAMES = 240;
    static final int ARMY_CLEARANCE = 640;
    static final double EVIDENCE_FRACTION = 0.5;
    static final int TARGET_RADIUS = 384;
    static final double ABORT_RATIO = 2.0;
    static final double RUN_PAST_RATIO = 1.5;
    static final double ABORT_SIM_FRACTION = 0.5;
    static final int ABORT_GRACE_AFTER_ARRIVAL = 48;
    static final double PENETRATE_BUDGET_FACTOR = 1.5;
    static final int NO_TARGET_FRAMES = 480;
    static final int RUNBY_TICK = 12;
    static final int WINNABLE_REFRESH = 24;

    private RunbyEvaluator() {
    }

    /**
     * Outcome of the entry gates, naming the first gate that refused.
     */
    public enum EntryVerdict {
        ENTER,
        NOT_ZERGLINGS,
        NO_SPEED,
        TOO_FEW,
        NO_TARGET,
        ARMY_NEAR_TARGET,
        PATH_DEFENDED,
        NO_ARMY_EVIDENCE,
        STATIC_DEFENSE
    }

    /**
     * One tracked enemy army unit: a non-worker, non-building unit that can attack ground.
     */
    @Getter
    public static final class ArmyUnit {
        private final UnitType type;
        private final Position position;
        private final boolean fresh;
        private final boolean cleared;

        /**
         * @param type unit type
         * @param position live position when visible, otherwise last known, or null when unknown
         * @param fresh true when visible or observed within {@link #FRESH_FRAMES}
         * @param cleared true when the last known position is in our vision and the unit is not there
         */
        public ArmyUnit(UnitType type, Position position, boolean fresh, boolean cleared) {
            this.type = type;
            this.position = position;
            this.fresh = fresh;
            this.cleared = cleared;
        }
    }

    /**
     * Inputs to the entry gates.
     */
    @Getter
    @Builder
    public static final class EntryInput {
        private final boolean zerglingsOnly;
        private final boolean metabolicBoost;
        private final int size;
        private final Position squadCenter;
        private final Position anchor;
        @Builder.Default
        private final List<ArmyUnit> army = Collections.emptyList();
        @Builder.Default
        private final List<StaticDefenseZone> zones = Collections.emptyList();
    }

    /**
     * Whether a unit's observation is recent enough to act on. The tracker stamps the observation frame only
     * when a unit is shown or hidden, so a unit in view carries the stamp of the frame it came into view and
     * counts as fresh however old that stamp is.
     *
     * @param visible true when the unit is in view
     * @param lastObservedFrame frame the unit was last shown or hidden
     * @param now current frame
     * @return true when the observation is fresh
     */
    public static boolean isFresh(boolean visible, int lastObservedFrame, int now) {
        return visible || now - lastObservedFrame <= FRESH_FRAMES;
    }

    /**
     * Whether a type belongs to the enemy army for runby purposes.
     *
     * @param type unit type
     * @return true for non-worker, non-building units with a ground attack
     */
    public static boolean isArmyType(UnitType type) {
        return !type.isBuilding() && !Filter.isWorkerType(type) && Filter.isGroundThreat(type);
    }

    /**
     * Whether a static defence zone can fire on lings. The zone list also holds anti-air structures sized by
     * their air range, which never refuse a runby, add to the abort tally, or cover a building from lings.
     *
     * @param zone a static defence zone
     * @return true for a structure that shoots ground
     */
    public static boolean threatensGround(StaticDefenseZone zone) {
        return Filter.isHostileBuildingToGround(zone.getStructure());
    }

    /**
     * Whether an unseen unit may be sitting burrowed where it was last seen in the target base, where seeing the
     * tile empty proves nothing.
     *
     * @param visible true when the unit is in view
     * @param burrowable true when its type can burrow
     * @param lastKnownInTarget true when its last known position is inside the target base
     * @return true when the unit must be assumed to still be there
     */
    public static boolean isLurking(boolean visible, boolean burrowable, boolean lastKnownInTarget) {
        return !visible && burrowable && lastKnownInTarget;
    }

    /**
     * Whether our vision has ruled out an unseen unit's last known position: the tile is in view, the unit is not,
     * and it could not be hiding there burrowed.
     *
     * @param visible true when the unit is in view
     * @param lastKnownTileVisible true when its last known tile is in our vision
     * @param lurking true when {@link #isLurking} holds for it
     * @return true when the position is cleared
     */
    public static boolean isCleared(boolean visible, boolean lastKnownTileVisible, boolean lurking) {
        return !visible && lastKnownTileVisible && !lurking;
    }

    /**
     * Runs the entry gates in order and names the first one that refuses.
     *
     * <p>An army position counts against entry near the target when it is fresh, and also when it is stale but
     * has not been cleared by our vision: a zealot last seen at the mineral line is still assumed to be there.
     * Defenders on the squad's path to the target, army and ground static defence alike, do not refuse entry by
     * their presence: a contain sits in front of exactly those units. They refuse only while the squad lacks
     * {@link #RUN_PAST_RATIO} times their strength. The evidence gate asks for positive knowledge that the army
     * is away, as a share of the supply we track; with no army tracked at all there is no such knowledge.
     *
     * <p>Superiority is not an entry gate. Every unit the target tally counts sits inside the army clearance and
     * every zone it counts covers the anchor, so either has already refused entry here. The tally is first read on
     * the decision tick that follows entry, which opens the abort window.
     *
     * @param input squad and intelligence state
     * @return ENTER, or the first refusing gate
     */
    public static EntryVerdict entryVerdict(EntryInput input) {
        if (!input.isZerglingsOnly()) {
            return EntryVerdict.NOT_ZERGLINGS;
        }
        if (!input.isMetabolicBoost()) {
            return EntryVerdict.NO_SPEED;
        }
        if (input.getSize() < MIN_LINGS) {
            return EntryVerdict.TOO_FEW;
        }
        Position anchor = input.getAnchor();
        if (anchor == null) {
            return EntryVerdict.NO_TARGET;
        }
        if (armyNearTarget(input.getArmy(), anchor)) {
            return EntryVerdict.ARMY_NEAR_TARGET;
        }
        if (!canRunPast(ourTally(input.getSize()),
                pathTally(input.getArmy(), input.getZones(), input.getSquadCenter(), anchor))) {
            return EntryVerdict.PATH_DEFENDED;
        }
        if (!armyEvidenceAway(input.getArmy(), anchor)) {
            return EntryVerdict.NO_ARMY_EVIDENCE;
        }
        for (StaticDefenseZone zone : input.getZones()) {
            if (threatensGround(zone) && zone.covers(anchor, 0)) {
                return EntryVerdict.STATIC_DEFENSE;
            }
        }
        return EntryVerdict.ENTER;
    }

    static boolean armyNearTarget(List<ArmyUnit> army, Position anchor) {
        for (ArmyUnit unit : army) {
            Position position = unit.getPosition();
            if (position == null || !unit.isFresh() && unit.isCleared()) {
                continue;
            }
            if (position.getDistance(anchor) <= ARMY_CLEARANCE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Enemy ground strength the squad runs past on its way to the target: army units within
     * {@link #ARMY_CLEARANCE} of the straight squad to target segment, counted when fresh or not yet cleared by
     * our vision, plus static defence that shoots ground within the same distance of it. Army near the target
     * itself is left to {@link #armyNearTarget}, and static defence covering the target to the static defence
     * gate. Priced with the same table the combat sim uses.
     *
     * @param army tracked enemy army
     * @param zones known static defence
     * @param squadCenter squad center, or null when unknown
     * @param anchor point being raided
     * @return summed ground strength on the path
     */
    public static double pathTally(List<ArmyUnit> army, List<StaticDefenseZone> zones, Position squadCenter,
                                   Position anchor) {
        if (squadCenter == null) {
            return 0;
        }
        double total = 0;
        for (ArmyUnit unit : army) {
            Position position = unit.getPosition();
            if (position == null || !unit.isFresh() && unit.isCleared()
                    || position.getDistance(anchor) <= ARMY_CLEARANCE) {
                continue;
            }
            if (distanceToSegment(position, squadCenter, anchor) <= ARMY_CLEARANCE) {
                total += UnitStrength.groundToGround(unit.getType());
            }
        }
        for (StaticDefenseZone zone : zones) {
            if (threatensGround(zone) && !zone.covers(anchor, 0)
                    && distanceToSegment(zone.getCenter(), squadCenter, anchor) <= ARMY_CLEARANCE) {
                total += UnitStrength.groundToGround(zone.getStructure());
            }
        }
        return total;
    }

    /**
     * Whether the squad has the mass to run past the defenders on its path.
     *
     * @param ours our tally
     * @param path enemy strength on the path
     * @return true when we hold at least {@link #RUN_PAST_RATIO} times the path strength
     */
    public static boolean canRunPast(double ours, double path) {
        return ours >= RUN_PAST_RATIO * path;
    }

    static boolean armyEvidenceAway(List<ArmyUnit> army, Position anchor) {
        int tracked = 0;
        int freshAway = 0;
        for (ArmyUnit unit : army) {
            int supply = unit.getType().supplyRequired();
            tracked += supply;
            Position position = unit.getPosition();
            if (unit.isFresh() && position != null && position.getDistance(anchor) > ARMY_CLEARANCE) {
                freshAway += supply;
            }
        }
        return tracked > 0 && freshAway >= EVIDENCE_FRACTION * tracked;
    }

    /**
     * Enemy ground strength at the target: fresh army units within {@link #TARGET_RADIUS} of the anchor, plus
     * static defence that shoots ground and whose zone covers the anchor. Priced with the same table the combat sim
     * uses.
     *
     * @param army tracked enemy army
     * @param zones known static defence
     * @param anchor point being raided
     * @return summed ground strength
     */
    public static double enemyTally(List<ArmyUnit> army, List<StaticDefenseZone> zones, Position anchor) {
        double total = 0;
        for (ArmyUnit unit : army) {
            Position position = unit.getPosition();
            if (unit.isFresh() && position != null && position.getDistance(anchor) <= TARGET_RADIUS) {
                total += UnitStrength.groundToGround(unit.getType());
            }
        }
        for (StaticDefenseZone zone : zones) {
            if (threatensGround(zone) && zone.covers(anchor, 0)) {
                total += UnitStrength.groundToGround(zone.getStructure());
            }
        }
        return total;
    }

    /**
     * Our ground strength for the abort tally.
     *
     * @param lings zerglings in the squad
     * @return summed ground strength
     */
    public static double ourTally(int lings) {
        return lings * UnitStrength.groundToGround(UnitType.Zerg_Zergling);
    }

    /**
     * Whether the enemy at the target is overwhelmingly stronger than the squad.
     *
     * @param enemy enemy tally
     * @param ours our tally
     * @return true when the enemy holds at least {@link #ABORT_RATIO} times our strength
     */
    public static boolean superiority(double enemy, double ours) {
        return enemy > 0 && enemy >= ABORT_RATIO * ours;
    }

    /**
     * Whether superiority may still end the runby. The window lasts until the squad has been inside the target
     * base for {@link #ABORT_GRACE_AFTER_ARRIVAL} frames, and never reopens once closed.
     *
     * @param closed true once the window has closed
     * @param arrivedFrame frame the squad centroid entered the target base, or -1 before that
     * @param now current frame
     * @return true while the window is open
     */
    public static boolean abortWindowOpen(boolean closed, int arrivedFrame, int now) {
        if (closed) {
            return false;
        }
        return arrivedFrame < 0 || now <= arrivedFrame + ABORT_GRACE_AFTER_ARRIVAL;
    }

    /**
     * Whether the runby retreats on this decision tick.
     *
     * <p>Outside the window nothing ends the runby here. Inside it the target tally aborts on superiority, and
     * once the squad is inside the base the combat sim may abort too, but only on a measured RETREAT far below
     * its own engage threshold.
     *
     * @param windowOpen whether the abort window is open
     * @param enemyTally enemy tally at the target
     * @param ourTally our tally
     * @param inside true when the squad centroid is inside the target base
     * @param simResult the sim verdict this tick, or null when none ran
     * @param simMeasured whether the sim measured a real enemy
     * @param simRatio the sim's overall ratio
     * @param engageThreshold the sim's engage threshold
     * @return true to abort
     */
    public static boolean shouldAbort(boolean windowOpen, double enemyTally, double ourTally, boolean inside,
                                      CombatSimulator.CombatResult simResult, boolean simMeasured, double simRatio,
                                      double engageThreshold) {
        if (!windowOpen) {
            return false;
        }
        if (superiority(enemyTally, ourTally)) {
            return true;
        }
        return inside && simResult == CombatSimulator.CombatResult.RETREAT && simMeasured
                && simRatio < ABORT_SIM_FRACTION * engageThreshold;
    }

    /**
     * Frames PENETRATE may last, from the walk at unupgraded zergling speed. The unupgraded speed is the
     * conservative choice: it overestimates the walk of a squad with Metabolic Boost.
     *
     * @param distance distance to the target in pixels
     * @return the budget in frames
     */
    public static int penetrateBudget(double distance) {
        return (int) (TravelTime.framesToReach(distance, UnitType.Zerg_Zergling.topSpeed()) * PENETRATE_BUDGET_FACTOR);
    }

    /**
     * Whether PENETRATE hands over to HARASS.
     *
     * @param now current frame
     * @param phaseStartFrame frame PENETRATE started
     * @param budgetFrames PENETRATE budget
     * @param inside true when the squad centroid is inside the target base
     * @param safeWorkerInReach true when a worker outside every threat's reach is within a member's reach
     * @return true when the budget is spent, or the squad is inside with a safe worker in reach
     */
    public static boolean penetrateEnds(int now, int phaseStartFrame, int budgetFrames, boolean inside,
                                        boolean safeWorkerInReach) {
        return now - phaseStartFrame >= budgetFrames || inside && safeWorkerInReach;
    }

    /**
     * Whether the target base has had nothing to hit for long enough to move on.
     *
     * @param now current frame
     * @param lastProgressFrame last frame a member had a worker, building or fight to take, or a seek point left
     * @return true once {@link #NO_TARGET_FRAMES} have passed without progress
     */
    public static boolean noTargets(int now, int lastProgressFrame) {
        return now - lastProgressFrame >= NO_TARGET_FRAMES;
    }

    /**
     * Whether a squad decision tick is due.
     *
     * @param now current frame
     * @param lastTickFrame frame of the previous decision tick
     * @return true every {@link #RUNBY_TICK} frames
     */
    public static boolean decisionTickDue(int now, int lastTickFrame) {
        return now - lastTickFrame >= RUNBY_TICK;
    }

    /**
     * Whether the winnable fight verdict needs refreshing.
     *
     * @param now current frame
     * @param lastCheckedFrame frame of the last check, or -1 when never checked
     * @return true every {@link #WINNABLE_REFRESH} frames
     */
    public static boolean winnableRefreshDue(int now, int lastCheckedFrame) {
        return lastCheckedFrame < 0 || now - lastCheckedFrame >= WINNABLE_REFRESH;
    }

    /**
     * Distance from a point to the segment between two others.
     *
     * @param point the point
     * @param start one end of the segment
     * @param end the other end
     * @return the distance in pixels
     */
    public static double distanceToSegment(Position point, Position start, Position end) {
        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared == 0) {
            return point.getDistance(start);
        }
        double t = ((point.getX() - start.getX()) * dx + (point.getY() - start.getY()) * dy) / lengthSquared;
        t = Math.max(0, Math.min(1, t));
        double px = start.getX() + t * dx - point.getX();
        double py = start.getY() + t * dy - point.getY();
        return Math.sqrt(px * px + py * py);
    }
}
