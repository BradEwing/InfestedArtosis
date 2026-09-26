package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import bwapi.WeaponType;
import info.tracking.EnemyReachMemory;
import lombok.Builder;
import lombok.Getter;
import unit.squad.horizon.UnitStrength;
import util.Filter;
import util.StaticDefenseZone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Per-Mutalisk decisions of an air harass, as static functions over plain values.
 *
 * <p>Anti-air is read as {@link AirThreat} zones: the ground each enemy that can hit a flyer covers, measured from
 * its footprint's edge. A zone is avoided when the anti-air strength stacked on it exceeds the flock's tolerance,
 * see {@link AirHarassEvaluator#tolerance}; a tolerated zone is flown into like open ground.
 *
 * <p>Each ready Mutalisk asks {@link #choose} what to do. It picks the best target outside every avoided zone in
 * {@link Tier} order, workers first, then isolated anti-air the flock kills quickly, then supply, then production,
 * then anything else. It evades when it stands inside an avoided zone, other than the zone of the anti-air it is
 * killing, stepping to the edge point nearest its goal, and otherwise attacks its target or skirts the avoided
 * zones toward the squad's strike point. Ranges, speeds, hit points and damage are read from JBWAPI; the constants
 * are tuning values.
 */
public final class AirHarassTargeting {

    static final int LOOKAHEAD_FRAMES = 12;
    static final int REACH_BUFFER = 16;
    static final int COVER_MARGIN = 16;
    static final int KILL_VOLLEYS = 2;
    static final int LOCAL_TARGET_RADIUS = 160;
    static final int EVADE_HYSTERESIS = 32;
    static final int EVADE_COMMIT_FRAMES = 12;
    static final double CURRENT_TARGET_BONUS = 1.2;
    static final int SEGMENT_STEP = 16;
    private static final int[] EDGE_RADII = {128, 192, 256};
    private static final int EDGE_ANGLES = 16;
    private static final int NO_TARGET = -1;

    private AirHarassTargeting() {
    }

    /**
     * Target tiers of a harass, lowest first.
     */
    public enum Tier {
        OTHER,
        PRODUCTION,
        SUPPLY,
        ISOLATED_AA,
        WORKER
    }

    /**
     * What a Mutalisk does this frame.
     */
    public enum Kind {
        EVADE,
        ATTACK,
        SEEK,
        NONE
    }

    /**
     * Ground an enemy able to hit a flyer covers, with the anti-air strength the combat sim prices it at.
     */
    @Getter
    public static final class AirThreat {
        private final int id;
        private final UnitType type;
        private final Position position;
        private final int reach;
        private final double strength;
        private final StaticDefenseZone zone;

        public AirThreat(int id, UnitType type, Position position, int reach, double strength) {
            this.id = id;
            this.type = type;
            this.position = position;
            this.reach = reach;
            this.strength = strength;
            this.zone = new StaticDefenseZone(type, position, reach);
        }

        /**
         * A threat at the type's air range, grown by the ground a mobile unit covers over the lookahead at its top
         * speed plus a buffer, and priced with {@link UnitStrength#antiAirStrength}.
         *
         * @param id enemy unit id
         * @param type enemy type
         * @param position its live or last known position
         * @param airRange its air range in pixels, see {@link #airRange}
         * @return the threat
         */
        public static AirThreat of(int id, UnitType type, Position position, int airRange) {
            int travel = type.isBuilding() ? 0 : (int) Math.ceil(type.topSpeed() * LOOKAHEAD_FRAMES) + REACH_BUFFER;
            return new AirThreat(id, type, position, airRange + travel, UnitStrength.antiAirStrength(type));
        }

        /**
         * Distance from a point to the edge of the zone grown by the padding; negative inside.
         *
         * @param point the point
         * @param padding pixels added to the reach
         * @return the margin in pixels
         */
        public double margin(Position point, int padding) {
            return zone.edgeDistance(point.getX(), point.getY()) - reach - padding;
        }

        public boolean covers(Position point, int padding) {
            return margin(point, padding) <= 0;
        }
    }

    /**
     * A visible enemy a Mutalisk could attack.
     */
    @Getter
    public static final class Contact {
        private final int id;
        private final UnitType type;
        private final Position position;
        private final int hitPoints;
        private final double hpFraction;

        /**
         * @param id enemy unit id
         * @param type enemy type
         * @param position its position
         * @param hitPoints its hit points plus shields
         * @param hpFraction that pool over its maximum
         */
        public Contact(int id, UnitType type, Position position, int hitPoints, double hpFraction) {
            this.id = id;
            this.type = type;
            this.position = position;
            this.hitPoints = hitPoints;
            this.hpFraction = hpFraction;
        }
    }

    /**
     * The Mutalisk asking for a decision.
     */
    @Getter
    public static final class Muta {
        private final int id;
        private final Position position;

        public Muta(int id, Position position) {
            this.id = id;
            this.position = position;
        }
    }

    /**
     * Everything shared by the Mutalisks of one squad on one frame.
     */
    @Getter
    @Builder
    public static final class Situation {
        @Builder.Default
        private final List<Contact> contacts = Collections.emptyList();
        @Builder.Default
        private final List<AirThreat> avoided = Collections.emptyList();
        private final int flockSize;
        private final Position seekPoint;
        @Builder.Default
        private final Predicate<Position> targetAllowed = position -> true;
        @Builder.Default
        private final Predicate<Position> pointAllowed = position -> true;
        private final int now;
    }

    /**
     * What a Mutalisk remembers between frames so its choices do not flap.
     */
    @Getter
    public static final class MutaMemory {
        private boolean evading;
        private Position evadePoint;
        private int evadeUntilFrame;
        private int targetId = NO_TARGET;

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
     * A Mutalisk's decision: where to move, or which enemy to attack and in which tier.
     */
    @Getter
    public static final class Decision {
        private final Kind kind;
        private final Position point;
        private final int targetId;
        private final Tier tier;

        private Decision(Kind kind, Position point, int targetId, Tier tier) {
            this.kind = kind;
            this.point = point;
            this.targetId = targetId;
            this.tier = tier;
        }

        static Decision move(Kind kind, Position point) {
            return new Decision(kind, point, NO_TARGET, null);
        }

        static Decision attack(Contact target, Tier tier) {
            return new Decision(Kind.ATTACK, null, target.getId(), tier);
        }

        static Decision none() {
            return new Decision(Kind.NONE, null, NO_TARGET, null);
        }
    }

    /**
     * Air range of an enemy type. A Bunker fires its Marines' air weapon, allowed
     * {@link EnemyReachMemory#BUNKER_ALLOWANCE} past their range.
     *
     * @param type enemy type
     * @param weaponRange range of a weapon for the owning player
     * @return range in pixels, or 0 for a type with no air weapon
     */
    public static int airRange(UnitType type, ToIntFunction<WeaponType> weaponRange) {
        boolean bunker = type == UnitType.Terran_Bunker;
        WeaponType weapon = bunker ? UnitType.Terran_Marine.airWeapon() : type.airWeapon();
        if (weapon == null || weapon == WeaponType.None) {
            return 0;
        }
        int range = Math.max(weapon.maxRange(), weaponRange.applyAsInt(weapon));
        return bunker ? range + EnemyReachMemory.BUNKER_ALLOWANCE : range;
    }

    /**
     * Whether an enemy type fires on flyers for harass purposes: a unit with an air weapon, a structure that shoots
     * air, or a Bunker, which holds Marines.
     *
     * @param type enemy type
     * @return true when the type is anti-air
     */
    public static boolean isAntiAir(UnitType type) {
        return type == UnitType.Terran_Bunker || Filter.isAirThreat(type);
    }

    /**
     * The zones a flock avoids: every threat whose own strength plus that of every other threat covering its
     * position exceeds the tolerance.
     *
     * @param threats every known anti-air threat
     * @param tolerance anti-air strength the flock accepts
     * @return the avoided threats
     */
    public static List<AirThreat> avoided(Collection<AirThreat> threats, double tolerance) {
        List<AirThreat> avoided = new ArrayList<>();
        for (AirThreat threat : threats) {
            double stacked = 0;
            for (AirThreat other : threats) {
                if (other.covers(threat.getPosition(), 0)) {
                    stacked += other.getStrength();
                }
            }
            if (stacked > tolerance) {
                avoided.add(threat);
            }
        }
        return avoided;
    }

    /**
     * Anti-air strength that can fire within a radius of a point: every threat whose zone, grown by the radius,
     * covers it.
     *
     * @param threats anti-air threats
     * @param point the point
     * @param radius pixels added to every reach
     * @return summed strength
     */
    public static double defenseAt(Collection<AirThreat> threats, Position point, int radius) {
        double total = 0;
        for (AirThreat threat : threats) {
            if (threat.covers(point, radius)) {
                total += threat.getStrength();
            }
        }
        return total;
    }

    /**
     * Pixels a Mutalisk keeps beyond an avoided zone: its own largest extent plus the cover margin.
     *
     * @return padding in pixels
     */
    public static int padding() {
        UnitType muta = UnitType.Zerg_Mutalisk;
        return Math.max(Math.max(muta.dimensionLeft(), muta.dimensionRight()),
                Math.max(muta.dimensionUp(), muta.dimensionDown())) + COVER_MARGIN;
    }

    /**
     * The tier a contact is taken in, or null when the harass leaves it alone: anti-air the flock cannot kill
     * within {@link #KILL_VOLLEYS} volleys.
     *
     * @param contact the contact
     * @param flockSize Mutalisks in the squad
     * @return the tier, or null
     */
    public static Tier tier(Contact contact, int flockSize) {
        UnitType type = contact.getType();
        if (Filter.isWorkerType(type)) {
            return Tier.WORKER;
        }
        if (isAntiAir(type)) {
            return killsQuickly(contact, flockSize) ? Tier.ISOLATED_AA : null;
        }
        if (type.supplyProvided() > 0 && !type.isResourceDepot()) {
            return Tier.SUPPLY;
        }
        if (type.isBuilding() && (type.canProduce() || type.isResourceDepot())) {
            return Tier.PRODUCTION;
        }
        return Tier.OTHER;
    }

    /**
     * Whether the flock kills a contact within {@link #KILL_VOLLEYS} volleys of primary damage, less the type's base
     * armor. Upgrades and the Glave Wurm bounce are not counted.
     *
     * @param contact the contact
     * @param flockSize Mutalisks in the squad
     * @return true when the volleys cover its hit points and shields
     */
    public static boolean killsQuickly(Contact contact, int flockSize) {
        WeaponType glave = UnitType.Zerg_Mutalisk.groundWeapon();
        int perHit = Math.max(1, glave.damageAmount() - contact.getType().armor());
        return (long) flockSize * perHit * KILL_VOLLEYS >= contact.getHitPoints();
    }

    /**
     * Whether a contact stands where an avoided zone other than its own fires on the Mutalisk attacking it.
     *
     * @param contact the contact
     * @param avoided avoided zones
     * @return true when covered
     */
    public static boolean covered(Contact contact, Collection<AirThreat> avoided) {
        for (AirThreat threat : avoided) {
            if (threat.getId() != contact.getId() && threat.covers(contact.getPosition(), COVER_MARGIN)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Picks what one Mutalisk does this frame and updates its memory.
     *
     * @param muta the Mutalisk
     * @param situation the squad's shared view of the frame
     * @param memory the Mutalisk's memory, updated in place
     * @return the decision
     */
    public static Decision choose(Muta muta, Situation situation, MutaMemory memory) {
        Contact target = bestTarget(muta, situation, memory.targetId);
        Tier tier = target == null ? null : tier(target, situation.getFlockSize());
        int ignoredId = tier == Tier.ISOLATED_AA ? target.getId() : NO_TARGET;
        List<AirThreat> zones = without(situation.getAvoided(), ignoredId);
        Position goal = target != null ? target.getPosition() : situation.getSeekPoint();

        Position evade = evadePoint(muta, zones, goal, situation, memory);
        if (evade != null) {
            return Decision.move(Kind.EVADE, evade);
        }
        if (target != null) {
            memory.targetId = target.getId();
            return Decision.attack(target, tier);
        }
        memory.targetId = NO_TARGET;
        Position skirt = edgePoint(muta.getPosition(), zones, situation.getSeekPoint(), situation.getPointAllowed(),
                true);
        return skirt == null ? Decision.none() : Decision.move(Kind.SEEK, skirt);
    }

    /**
     * The best target for a Mutalisk: among allowed contacts, and contacts within {@link #LOCAL_TARGET_RADIUS} of it,
     * that have a tier and no other avoided zone covers, the highest tier first, then the most injured and nearest,
     * with the current target favoured by {@link #CURRENT_TARGET_BONUS}.
     *
     * @param muta the Mutalisk
     * @param situation the frame's shared view
     * @param currentTargetId the Mutalisk's current target, or -1
     * @return the target, or null
     */
    static Contact bestTarget(Muta muta, Situation situation, int currentTargetId) {
        Contact best = null;
        Tier bestTier = null;
        double bestScore = -1;
        for (Contact contact : situation.getContacts()) {
            double distance = muta.getPosition().getDistance(contact.getPosition());
            if (!situation.getTargetAllowed().test(contact.getPosition()) && distance > LOCAL_TARGET_RADIUS) {
                continue;
            }
            Tier tier = tier(contact, situation.getFlockSize());
            if (tier == null || covered(contact, situation.getAvoided())) {
                continue;
            }
            double bonus = contact.getId() == currentTargetId ? CURRENT_TARGET_BONUS : 1.0;
            double score = (1.0 + 0.5 * (1.0 - contact.getHpFraction())) * bonus / Math.max(distance, 1);
            if (bestTier == null || tier.ordinal() > bestTier.ordinal()
                    || tier == bestTier && score > bestScore) {
                best = contact;
                bestTier = tier;
                bestScore = score;
            }
        }
        return best;
    }

    /**
     * Returns where an exposed Mutalisk should step to, or null when it should not evade.
     *
     * <p>A Mutalisk starts evading when it is inside an avoided zone. Once evading it keeps going for at least
     * {@link #EVADE_COMMIT_FRAMES}, and until it is more than {@link #EVADE_HYSTERESIS} beyond every zone.
     */
    static Position evadePoint(Muta muta, List<AirThreat> zones, Position goal, Situation situation,
                               MutaMemory memory) {
        double margin = minMargin(muta.getPosition(), zones);
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
        Position point = edgePoint(muta.getPosition(), zones, goal, situation.getPointAllowed(), false);
        if (point == null) {
            memory.stopEvade();
            return null;
        }
        memory.startEvade(point, now + EVADE_COMMIT_FRAMES);
        return point;
    }

    /**
     * The point a Mutalisk moves to on its way to a goal around the avoided zones.
     *
     * <p>With the straight line to the goal clear of every zone the goal itself is returned. Otherwise a ring of
     * points around the Mutalisk is scored: among the allowed points outside every zone, and, when asked, reached by
     * a clear straight hop, the one nearest the goal; with none, the allowed point farthest outside the zones.
     * Stepping toward the goal along the ring is what wraps the flock around the edge of a zone.
     *
     * @param from the Mutalisk's position
     * @param zones avoided zones
     * @param goal where it is heading, or null
     * @param allowed points it may move to
     * @param clearHop true to require a clear straight hop to the chosen point
     * @return the point, or null with no goal or no allowed point
     */
    public static Position edgePoint(Position from, Collection<AirThreat> zones, Position goal,
                                     Predicate<Position> allowed, boolean clearHop) {
        if (goal == null) {
            return null;
        }
        if (segmentClear(from, goal, zones)) {
            return goal;
        }
        Position nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        Position safest = null;
        double safestMargin = Double.NEGATIVE_INFINITY;
        for (int radius : EDGE_RADII) {
            for (int i = 0; i < EDGE_ANGLES; i++) {
                double angle = 2 * Math.PI * i / EDGE_ANGLES;
                Position candidate = new Position(from.getX() + (int) Math.round(Math.cos(angle) * radius),
                        from.getY() + (int) Math.round(Math.sin(angle) * radius));
                if (!allowed.test(candidate)) {
                    continue;
                }
                double margin = minMargin(candidate, zones);
                if (margin > safestMargin) {
                    safest = candidate;
                    safestMargin = margin;
                }
                if (margin <= 0 || clearHop && !segmentClear(from, candidate, zones)) {
                    continue;
                }
                double distance = candidate.getDistance(goal);
                if (distance < nearestDistance) {
                    nearest = candidate;
                    nearestDistance = distance;
                }
            }
        }
        return nearest != null ? nearest : safest;
    }

    /**
     * Whether the straight line between two points stays outside every zone, sampled every
     * {@link #SEGMENT_STEP} pixels.
     *
     * @param from one end
     * @param to the other end
     * @param zones avoided zones
     * @return true when every sample is outside
     */
    static boolean segmentClear(Position from, Position to, Collection<AirThreat> zones) {
        if (zones.isEmpty()) {
            return true;
        }
        double length = from.getDistance(to);
        int steps = Math.max(1, (int) Math.ceil(length / SEGMENT_STEP));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Position sample = new Position(from.getX() + (int) Math.round((to.getX() - from.getX()) * t),
                    from.getY() + (int) Math.round((to.getY() - from.getY()) * t));
            if (minMargin(sample, zones) <= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Smallest distance from a point to the edge of any zone grown by the Mutalisk padding; negative inside, and
     * positive infinity with no zones.
     *
     * @param point the point
     * @param zones the zones
     * @return the margin in pixels
     */
    static double minMargin(Position point, Collection<AirThreat> zones) {
        int padding = padding();
        double min = Double.POSITIVE_INFINITY;
        for (AirThreat zone : zones) {
            min = Math.min(min, zone.margin(point, padding));
        }
        return min;
    }

    private static List<AirThreat> without(List<AirThreat> zones, int id) {
        if (id == NO_TARGET) {
            return zones;
        }
        List<AirThreat> kept = new ArrayList<>();
        for (AirThreat zone : zones) {
            if (zone.getId() != id) {
                kept.add(zone);
            }
        }
        return kept;
    }
}
