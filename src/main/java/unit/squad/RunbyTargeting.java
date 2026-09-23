package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import info.tracking.EnemyReachMemory;
import lombok.Builder;
import lombok.Getter;
import util.Filter;
import util.StaticDefenseZone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Per-ling decisions of a runby, as static functions over plain values.
 *
 * <p>Each ready ling asks {@link #choose} what to do. In order: evade when exposed to a threat and not in a
 * winnable fight, then mean workers in reach, then workers no threat covers, then a winnable fight, then a
 * building no threat covers, then the squad's seek point. PENETRATE uses only the evade, worker and seek steps.
 *
 * <p>A threat's reach is its ground range plus its own extent, because distances here are measured between
 * centers while weapon range is measured between edges, plus the ground it covers in {@link #LOOKAHEAD_FRAMES}
 * at its top speed, plus a buffer. The ground range is the one {@link EnemyReachMemory} has learned for the type;
 * extents and speeds are read from JBWAPI.
 */
public final class RunbyTargeting {

    static final int LOOKAHEAD_FRAMES = 12;
    static final int REACH_BUFFER = 16;
    static final int EVADE_HYSTERESIS = 48;
    static final int EVADE_COMMIT_FRAMES = 12;
    static final int BUILDING_STICKY_FRAMES = 48;
    static final double CURRENT_TARGET_BONUS = 1.2;
    static final int WORKER_SEARCH_RADIUS = 256;
    static final int VISIT_RADIUS = 96;
    static final int VISIT_WORKER_CLEARANCE = 192;
    private static final int[] EVADE_RADII = {64, 96, 128};
    private static final int EVADE_ANGLES = 12;
    private static final int NO_TARGET = -1;

    private RunbyTargeting() {
    }

    /**
     * What a ling does this frame.
     */
    public enum Kind {
        EVADE,
        WORKER,
        FIGHT,
        BUILDING,
        SEEK,
        NONE
    }

    /**
     * A visible enemy the ling could attack.
     */
    @Getter
    public static final class Contact {
        private final int id;
        private final UnitType type;
        private final Position position;
        private final double hpFraction;
        private final boolean meanWorker;

        public Contact(int id, UnitType type, Position position, double hpFraction, boolean meanWorker) {
            this.id = id;
            this.type = type;
            this.position = position;
            this.hpFraction = hpFraction;
            this.meanWorker = meanWorker;
        }

        public boolean isWorker() {
            return Filter.isWorkerType(type);
        }

        public boolean isBuilding() {
            return type.isBuilding();
        }
    }

    /**
     * An enemy that can hurt a ling, with the ground it covers.
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
         * @param type enemy type
         * @param position its live or last known position
         * @param groundRange ground range learned for the type
         * @return a threat covering {@link #reach(UnitType, int)} around the position
         */
        public static Threat of(UnitType type, Position position, int groundRange) {
            return new Threat(position, reach(type, groundRange));
        }

        /**
         * @param mark where one of our units was hit by something no known enemy accounts for
         * @return a threat covering the mark's radius around it
         */
        public static Threat of(EnemyReachMemory.HurtMark mark) {
            return new Threat(mark.getPosition(), EnemyReachMemory.HURT_MARK_RADIUS);
        }

        double margin(Position point) {
            return point.getDistance(position) - reach;
        }
    }

    /**
     * The ling asking for a decision.
     */
    @Getter
    public static final class Ling {
        private final int id;
        private final Position position;
        private final int reach;

        public Ling(int id, Position position, int reach) {
            this.id = id;
            this.position = position;
            this.reach = reach;
        }
    }

    /**
     * Everything shared by the lings of one squad on one frame.
     */
    @Getter
    @Builder
    public static final class Situation {
        private final RunbyState.Phase phase;
        private final boolean winnable;
        @Builder.Default
        private final List<Contact> contacts = Collections.emptyList();
        @Builder.Default
        private final List<Threat> threats = Collections.emptyList();
        @Builder.Default
        private final List<StaticDefenseZone> zones = Collections.emptyList();
        private final Position seekPoint;
        @Builder.Default
        private final Predicate<Position> evadeAllowed = position -> true;
        @Builder.Default
        private final Predicate<Position> workerAllowed = position -> true;
        private final int now;
    }

    /**
     * What a ling remembers between frames so its choices do not flap.
     */
    @Getter
    public static final class LingMemory {
        private boolean evading;
        private Position evadePoint;
        private int evadeUntilFrame;
        private int targetId = NO_TARGET;
        private int buildingId = NO_TARGET;
        private int buildingUntilFrame;

        void startEvade(Position point, int until) {
            evading = true;
            evadePoint = point;
            evadeUntilFrame = until;
        }

        void stopEvade() {
            evading = false;
            evadePoint = null;
        }
    }

    /**
     * A ling's decision: where to move, or which enemy to attack.
     */
    @Getter
    public static final class Decision {
        private final Kind kind;
        private final Position point;
        private final int targetId;

        private Decision(Kind kind, Position point, int targetId) {
            this.kind = kind;
            this.point = point;
            this.targetId = targetId;
        }

        static Decision move(Kind kind, Position point) {
            return new Decision(kind, point, NO_TARGET);
        }

        static Decision attack(Kind kind, int targetId) {
            return new Decision(kind, null, targetId);
        }

        static Decision none() {
            return new Decision(Kind.NONE, null, NO_TARGET);
        }
    }

    /**
     * Ground a unit type covers around its center over the lookahead, at its base ground range.
     *
     * @param type unit type
     * @return reach in pixels
     */
    public static int reach(UnitType type) {
        return reach(type, EnemyReachMemory.baseGroundRange(type));
    }

    /**
     * Ground a unit type covers around its center over the lookahead, at the larger of its base ground range and a
     * learned one. A Bunker's base range is the Marines' it holds.
     *
     * @param type unit type
     * @param groundRange ground range learned for the type
     * @return reach in pixels
     */
    public static int reach(UnitType type, int groundRange) {
        int range = Math.max(groundRange, EnemyReachMemory.baseGroundRange(type));
        return range + extent(type) + (int) Math.ceil(type.topSpeed() * LOOKAHEAD_FRAMES) + REACH_BUFFER;
    }

    static int extent(UnitType type) {
        return Math.max(Math.max(type.dimensionLeft(), type.dimensionRight()),
                Math.max(type.dimensionUp(), type.dimensionDown()));
    }

    /**
     * Picks what one ling does this frame and updates its memory.
     *
     * @param ling the ling
     * @param situation the squad's shared view of the frame
     * @param memory the ling's memory, updated in place
     * @return the decision
     */
    public static Decision choose(Ling ling, Situation situation, LingMemory memory) {
        boolean fightAllowed = situation.getPhase() == RunbyState.Phase.HARASS && situation.isWinnable();
        if (fightAllowed) {
            memory.stopEvade();
        } else {
            Position evade = evadePoint(ling, situation, memory);
            if (evade != null) {
                return Decision.move(Kind.EVADE, evade);
            }
        }

        if (targetLeftWorkerArea(memory.targetId, situation)) {
            memory.targetId = NO_TARGET;
        }
        Contact worker = bestMeanWorker(ling, situation);
        if (worker == null) {
            worker = bestSafeWorker(ling, situation, memory.targetId);
        }
        if (worker != null) {
            memory.targetId = worker.getId();
            return Decision.attack(Kind.WORKER, worker.getId());
        }

        if (fightAllowed && hasFightTarget(situation.getContacts())) {
            memory.targetId = NO_TARGET;
            return Decision.attack(Kind.FIGHT, NO_TARGET);
        }

        if (situation.getPhase() == RunbyState.Phase.HARASS) {
            Contact building = bestSafeBuilding(ling, situation, memory);
            if (building != null) {
                memory.targetId = building.getId();
                return Decision.attack(Kind.BUILDING, building.getId());
            }
        }

        memory.targetId = NO_TARGET;
        if (situation.getSeekPoint() != null) {
            return Decision.move(Kind.SEEK, situation.getSeekPoint());
        }
        return Decision.none();
    }

    /**
     * Returns where an exposed ling should step to, or null when it should not evade.
     *
     * <p>A ling starts evading when it is inside a threat's reach. Once evading it keeps going for at least
     * {@link #EVADE_COMMIT_FRAMES}, and until it is more than {@link #EVADE_HYSTERESIS} beyond every reach, so it
     * does not turn back the moment it clears the edge. A ling with nowhere legal to go stops evading.
     *
     * @param ling the ling
     * @param situation the frame's shared view
     * @param memory the ling's memory, updated in place
     * @return the point to move to, or null
     */
    static Position evadePoint(Ling ling, Situation situation, LingMemory memory) {
        double margin = minMargin(ling.getPosition(), situation.getThreats());
        int now = situation.getNow();
        if (memory.isEvading()) {
            boolean committed = now < memory.getEvadeUntilFrame();
            if (!committed && margin > EVADE_HYSTERESIS) {
                memory.stopEvade();
                return null;
            }
            if (committed && memory.getEvadePoint() != null) {
                return memory.getEvadePoint();
            }
        } else if (margin > 0) {
            return null;
        }

        Position point = findEvadePoint(ling.getPosition(), situation);
        if (point == null) {
            memory.stopEvade();
            return null;
        }
        memory.startEvade(point, now + EVADE_COMMIT_FRAMES);
        return point;
    }

    /**
     * Scores a ring of points around the ling and returns the one farthest outside every threat's reach, among
     * the points the situation allows, breaking ties toward the seek point.
     *
     * @param from the ling's position
     * @param situation the frame's shared view
     * @return the best point, or null when no point is allowed
     */
    static Position findEvadePoint(Position from, Situation situation) {
        return findEvadePoint(from, candidate -> minMargin(candidate, situation.getThreats()),
                situation.getEvadeAllowed(), situation.getSeekPoint());
    }

    /**
     * Scores a ring of points around a unit and returns the one farthest outside every zone's reach plus the
     * padding, among the allowed points, breaking ties toward the seek point.
     *
     * @param from the unit's position
     * @param zones ground the enemy fires on, measured from each zone's edge
     * @param padding pixels added to every zone's reach, covering the unit's extent and a margin
     * @param allowed points the unit may move to
     * @param seek point ties are broken toward, or null
     * @return the best point, or null when no point is allowed
     */
    public static Position findEvadePoint(Position from, Collection<StaticDefenseZone> zones, int padding,
                                          Predicate<Position> allowed, Position seek) {
        return findEvadePoint(from, candidate -> zoneMargin(candidate, zones, padding), allowed, seek);
    }

    /**
     * Smallest distance from a point to the edge of any zone's reach plus the padding; negative inside, and
     * positive infinity with no zones.
     *
     * @param point the point
     * @param zones the zones
     * @param padding pixels added to every zone's reach
     * @return the margin in pixels
     */
    static double zoneMargin(Position point, Collection<StaticDefenseZone> zones, int padding) {
        double min = Double.POSITIVE_INFINITY;
        for (StaticDefenseZone zone : zones) {
            min = Math.min(min, zone.edgeDistance(point.getX(), point.getY()) - zone.getReach() - padding);
        }
        return min;
    }

    private static Position findEvadePoint(Position from, ToDoubleFunction<Position> marginOf,
                                           Predicate<Position> allowed, Position seek) {
        Position best = null;
        double bestMargin = Double.NEGATIVE_INFINITY;
        double bestSeekDistance = Double.MAX_VALUE;
        for (int radius : EVADE_RADII) {
            for (int i = 0; i < EVADE_ANGLES; i++) {
                double angle = 2 * Math.PI * i / EVADE_ANGLES;
                Position candidate = new Position(from.getX() + (int) Math.round(Math.cos(angle) * radius),
                        from.getY() + (int) Math.round(Math.sin(angle) * radius));
                if (!allowed.test(candidate)) {
                    continue;
                }
                double margin = marginOf.applyAsDouble(candidate);
                double seekDistance = seek == null ? 0 : candidate.getDistance(seek);
                if (margin > bestMargin || margin == bestMargin && seekDistance < bestSeekDistance) {
                    best = candidate;
                    bestMargin = margin;
                    bestSeekDistance = seekDistance;
                }
            }
        }
        return best;
    }

    /**
     * Smallest distance from a point to the edge of any threat's reach; negative inside a reach, and positive
     * infinity with no threats.
     *
     * @param point the point
     * @param threats the threats
     * @return the margin in pixels
     */
    static double minMargin(Position point, Collection<Threat> threats) {
        double min = Double.POSITIVE_INFINITY;
        for (Threat threat : threats) {
            min = Math.min(min, threat.margin(point));
        }
        return min;
    }

    static boolean isSafe(Position point, Collection<Threat> threats) {
        return minMargin(point, threats) > 0;
    }

    /**
     * Whether a worker may be targeted: only workers the situation allows, which the runby limits to its target
     * base, so a worker seen anywhere else never pulls a ling out of the base.
     *
     * @param contact the contact
     * @param situation the frame's shared view
     * @return true for an allowed worker
     */
    static boolean isWorkerCandidate(Contact contact, Situation situation) {
        return contact.isWorker() && situation.getWorkerAllowed().test(contact.getPosition());
    }

    /**
     * Whether the ling's current target is a worker that has left the ground workers may be taken on, so the
     * ling drops it and decides again rather than chasing it out of the base.
     *
     * @param targetId the ling's current target, or -1
     * @param situation the frame's shared view
     * @return true when the target is a visible worker outside the allowed ground
     */
    static boolean targetLeftWorkerArea(int targetId, Situation situation) {
        if (targetId == NO_TARGET) {
            return false;
        }
        for (Contact contact : situation.getContacts()) {
            if (contact.getId() == targetId) {
                return contact.isWorker() && !situation.getWorkerAllowed().test(contact.getPosition());
            }
        }
        return false;
    }

    static Contact bestMeanWorker(Ling ling, Situation situation) {
        Contact best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Contact contact : situation.getContacts()) {
            if (!isWorkerCandidate(contact, situation) || !contact.isMeanWorker()) {
                continue;
            }
            double distance = ling.getPosition().getDistance(contact.getPosition());
            if (distance > ling.getReach() || !isSafe(contact.getPosition(), situation.getThreats())) {
                continue;
            }
            if (distance < bestDistance) {
                best = contact;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Picks a worker outside every threat's reach: lowest hit point fraction first, then nearest, with the
     * current target favoured by {@link #CURRENT_TARGET_BONUS} on both. Only workers the situation allows are
     * considered, and among them those within {@link #WORKER_SEARCH_RADIUS} of the ling are preferred.
     *
     * @param ling the ling
     * @param situation the frame's shared view
     * @param currentTargetId the ling's current target, or -1
     * @return the chosen worker, or null
     */
    static Contact bestSafeWorker(Ling ling, Situation situation, int currentTargetId) {
        List<Contact> safe = new ArrayList<>();
        List<Contact> near = new ArrayList<>();
        for (Contact contact : situation.getContacts()) {
            if (!isWorkerCandidate(contact, situation) || !isSafe(contact.getPosition(), situation.getThreats())) {
                continue;
            }
            safe.add(contact);
            if (ling.getPosition().getDistance(contact.getPosition()) <= WORKER_SEARCH_RADIUS) {
                near.add(contact);
            }
        }
        Contact best = null;
        double bestHp = Double.MAX_VALUE;
        double bestDistance = Double.MAX_VALUE;
        for (Contact contact : near.isEmpty() ? safe : near) {
            double bonus = contact.getId() == currentTargetId ? CURRENT_TARGET_BONUS : 1.0;
            double hp = contact.getHpFraction() / bonus;
            double distance = ling.getPosition().getDistance(contact.getPosition()) / bonus;
            if (hp < bestHp || hp == bestHp && distance < bestDistance) {
                best = contact;
                bestHp = hp;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Whether a winnable fight has anything to take: a non-worker unit, or a building that shoots ground.
     *
     * @param contacts visible enemies
     * @return true when a fight target exists
     */
    static boolean hasFightTarget(Collection<Contact> contacts) {
        for (Contact contact : contacts) {
            if (isFightTarget(contact.getType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a type is taken in a winnable fight rather than as a worker or an undefended building.
     *
     * @param type enemy type
     * @return true for non-worker units and for buildings that shoot ground
     */
    public static boolean isFightTarget(UnitType type) {
        if (Filter.isWorkerType(type)) {
            return false;
        }
        return !type.isBuilding() || Filter.isHostileBuildingToGround(type);
    }

    /**
     * Whether a building is out of every threat's reach and every static defence zone that shoots ground,
     * measured to its edge.
     *
     * @param building the building
     * @param threats the threats
     * @param zones known static defence
     * @return true when the building is uncovered
     */
    static boolean isUncovered(Contact building, Collection<Threat> threats, Collection<StaticDefenseZone> zones) {
        int extent = extent(building.getType());
        for (Threat threat : threats) {
            if (building.getPosition().getDistance(threat.getPosition()) <= threat.getReach() + extent) {
                return false;
            }
        }
        for (StaticDefenseZone zone : zones) {
            if (RunbyEvaluator.threatensGround(zone) && zone.covers(building.getPosition(), extent)) {
                return false;
            }
        }
        return true;
    }

    static Contact bestSafeBuilding(Ling ling, Situation situation, LingMemory memory) {
        Contact best = null;
        double bestDistance = Double.MAX_VALUE;
        Contact sticky = null;
        for (Contact contact : situation.getContacts()) {
            if (!contact.isBuilding() || Filter.isLowPriorityCombatTarget(contact.getType())) {
                continue;
            }
            if (!isUncovered(contact, situation.getThreats(), situation.getZones())) {
                continue;
            }
            if (contact.getId() == memory.buildingId) {
                sticky = contact;
            }
            double distance = ling.getPosition().getDistance(contact.getPosition());
            if (distance < bestDistance) {
                best = contact;
                bestDistance = distance;
            }
        }
        if (sticky != null && situation.getNow() < memory.buildingUntilFrame) {
            return sticky;
        }
        if (best != null && best.getId() != memory.buildingId) {
            memory.buildingId = best.getId();
            memory.buildingUntilFrame = situation.getNow() + BUILDING_STICKY_FRAMES;
        }
        return best;
    }

    /**
     * The squad's seek point: the nearest visible worker in the target base, else the nearest recently seen
     * worker there, else the first likely worker spot not yet visited.
     *
     * @param from the squad center
     * @param visibleWorkers visible worker positions in the target base
     * @param recentWorkers last known positions of recently seen workers in the target base
     * @param likelySpots likely worker spots, in visiting order
     * @param visited spots already visited
     * @return the goal, with type NONE and no point when nothing is left
     */
    public static Goal seekGoal(Position from, Collection<Position> visibleWorkers, Collection<Position> recentWorkers,
                                List<Position> likelySpots, Set<Position> visited) {
        Position visible = nearest(from, visibleWorkers);
        if (visible != null) {
            return new Goal(RunbyState.GoalType.VISIBLE, visible);
        }
        Position recent = nearest(from, recentWorkers);
        if (recent != null) {
            return new Goal(RunbyState.GoalType.LAST_SEEN, recent);
        }
        for (Position spot : likelySpots) {
            if (!visited.contains(spot)) {
                return new Goal(RunbyState.GoalType.LIKELY, spot);
            }
        }
        return new Goal(RunbyState.GoalType.NONE, null);
    }

    /**
     * Marks every likely spot a ling has reached with no visible worker near it.
     *
     * @param likelySpots likely worker spots
     * @param visited spots already visited, updated in place
     * @param lings positions of the squad's lings
     * @param visibleWorkers positions of visible workers
     */
    public static void markVisited(List<Position> likelySpots, Set<Position> visited, Collection<Position> lings,
                                   Collection<Position> visibleWorkers) {
        for (Position spot : likelySpots) {
            if (visited.contains(spot)) {
                continue;
            }
            boolean reached = false;
            for (Position ling : lings) {
                if (ling.getDistance(spot) <= VISIT_RADIUS) {
                    reached = true;
                    break;
                }
            }
            if (!reached) {
                continue;
            }
            boolean workerNear = false;
            for (Position worker : visibleWorkers) {
                if (worker.getDistance(spot) <= VISIT_WORKER_CLEARANCE) {
                    workerNear = true;
                    break;
                }
            }
            if (!workerNear) {
                visited.add(spot);
            }
        }
    }

    private static Position nearest(Position from, Collection<Position> positions) {
        Position best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Position position : positions) {
            double distance = from == null ? 0 : from.getDistance(position);
            if (best == null || distance < bestDistance) {
                best = position;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * A seek point and where it came from.
     */
    @Getter
    public static final class Goal {
        private final RunbyState.GoalType type;
        private final Position point;

        public Goal(RunbyState.GoalType type, Position point) {
            this.type = type;
            this.point = point;
        }
    }
}
