package info.tracking;

import bwapi.Position;
import bwapi.UnitType;
import bwapi.WeaponType;
import telemetry.ReachTelemetry;
import util.StaticDefenseZone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Ground reach of every enemy unit type, learned over the whole game, and the places our units were hit from where
 * no known enemy accounts for the hit.
 *
 * <p>Reach is measured edge to edge, the way {@link bwapi.Unit#getDistance} measures weapon range. A type's reach is
 * the largest of its base weapon range, the range its owner's weapon reports with upgrades, and every distance one
 * of its units was seen to hit from. It never falls, so every squad and every episode reads what any earlier one
 * learned. A Bunker has no weapon of its own: it starts at the range of the Marines it holds, and any further reach
 * is learned from the shots it fires.
 *
 * <p>A hit is attributed to a type only when the distance lies within {@link #MAX_LEARN_STEP} of the type's current
 * reach, so a hit from an unseen shooter never teaches a visible bystander an unbounded range. A hit nothing
 * accounts for leaves a {@link HurtMark} where the victim stood, kept for {@link #HURT_MARK_WINDOW} frames from the
 * last hit near it.
 */
public class EnemyReachMemory {

    public static final int HURT_MARK_WINDOW = 480;
    public static final int HURT_MARK_RADIUS = 64;
    static final int HURT_MARK_MERGE_RADIUS = 32;
    static final int MAX_LEARN_STEP = 64;

    /**
     * What raised a reach.
     */
    public enum Source {
        API,
        VISIBLE,
        BULLET,
        HURTMARK
    }

    private final Map<UnitType, Integer> learned = new HashMap<>();
    private final List<HurtMark> hurtMarks = new ArrayList<>();

    /**
     * The ground weapon a unit of the type fires. A Bunker fires the Marines' weapon.
     *
     * @param type enemy unit type
     * @return its ground weapon, or {@link WeaponType#None}
     */
    public static WeaponType groundWeapon(UnitType type) {
        WeaponType weapon = type == UnitType.Terran_Bunker ? UnitType.Terran_Marine.groundWeapon() : type.groundWeapon();
        return weapon == null ? WeaponType.None : weapon;
    }

    /**
     * Base ground range of a type with no upgrade and nothing learned.
     *
     * @param type unit type
     * @return range in pixels, or 0 for a type with no ground weapon
     */
    public static int baseGroundRange(UnitType type) {
        WeaponType weapon = groundWeapon(type);
        return weapon == WeaponType.None ? 0 : weapon.maxRange();
    }

    /**
     * Ground range of a type as its owner's weapon reports it, upgrades included when the owner's upgrades are
     * visible.
     *
     * @param type unit type
     * @param weaponRange maps a ground weapon to its range for the owning player
     * @return range in pixels, or 0 for a type with no ground weapon
     */
    public static int groundRange(UnitType type, ToIntFunction<WeaponType> weaponRange) {
        WeaponType weapon = groundWeapon(type);
        return weapon == WeaponType.None ? 0 : weaponRange.applyAsInt(weapon);
    }

    /**
     * The largest reach known for a type.
     *
     * @param type enemy unit type
     * @return the larger of its base ground range and its learned reach
     */
    public int groundReach(UnitType type) {
        return Math.max(baseGroundRange(type), learned.getOrDefault(type, 0));
    }

    /**
     * The largest reach known for a type, given a reach from another source.
     *
     * @param type enemy unit type
     * @param apiReach reach from a table or from the owner's weapon
     * @return the larger of the two
     */
    public int groundReach(UnitType type, int apiReach) {
        return Math.max(groundReach(type), apiReach);
    }

    /**
     * Raises a type's reach to the range its owner's weapon reports. Reports a row only when that exceeds the reach
     * already known, which is the only way an upgrade shows here.
     *
     * @param type visible enemy type
     * @param apiRange range from {@link bwapi.Player#weaponMaxRange}
     * @param frame current frame
     */
    public void seed(UnitType type, int apiRange, int frame) {
        raise(type, apiRange, Source.API, null, frame);
    }

    /**
     * Raises a type's reach. Never lowers it.
     *
     * @param type enemy unit type
     * @param reach observed reach in pixels
     * @param source what observed it
     * @param victim where the unit it hit stood, or null
     * @param frame current frame
     * @return true when the known reach rose
     */
    public boolean raise(UnitType type, int reach, Source source, Position victim, int frame) {
        int old = groundReach(type);
        if (reach <= old) {
            return false;
        }
        learned.put(type, reach);
        ReachTelemetry.reachRaised(frame, type, old, reach, source, victim);
        return true;
    }

    /**
     * Whether a hit from a unit of the type at a distance can be pinned on it: within {@link #MAX_LEARN_STEP} past
     * its known reach.
     *
     * @param currentReach reach known for the type
     * @param distance edge distance from the shooter to the victim
     * @return true when the hit is attributable
     */
    static boolean isAttributable(int currentReach, int distance) {
        return distance <= currentReach + MAX_LEARN_STEP;
    }

    /**
     * Learns from a hit attributed to a unit of the type. A hit from within the known reach teaches nothing; a hit
     * from past it raises the reach to the distance.
     *
     * @param shooter type of the shooter
     * @param distance edge distance from the shooter to the victim
     * @param source VISIBLE or BULLET
     * @param victim where the victim stood
     * @param frame current frame
     * @return true when the hit was attributable to the type
     */
    public boolean learnFromHit(UnitType shooter, int distance, Source source, Position victim, int frame) {
        if (!isAttributable(groundReach(shooter), distance)) {
            return false;
        }
        raise(shooter, distance, source, victim, frame);
        return true;
    }

    /**
     * Learns from a hit no visible enemy was seen targeting, given the visible enemies with a ground weapon near
     * the victim.
     *
     * <p>A bystander already within its type's known reach explains the hit and teaches nothing. Otherwise the hit
     * is pinned on a type only when exactly one bystander stands within {@link #MAX_LEARN_STEP} past its known
     * reach: with two or more, or none, the shooter is unknown and the caller records a hurt mark instead, so a
     * visible unit that merely stands near a hit from an unseen shooter never teaches its type.
     *
     * @param bystanders visible enemies with a ground weapon, each with its edge distance to the victim
     * @param victim where the victim stood
     * @param frame current frame
     * @return true when the hit was explained or attributed
     */
    public boolean learnFromBystanders(Collection<Bystander> bystanders, Position victim, int frame) {
        Bystander sole = null;
        int beyondReach = 0;
        for (Bystander bystander : bystanders) {
            int reach = groundReach(bystander.getType());
            if (bystander.getDistance() <= reach) {
                return true;
            }
            if (isAttributable(reach, bystander.getDistance())) {
                sole = bystander;
                beyondReach++;
            }
        }
        if (beyondReach != 1) {
            return false;
        }
        raise(sole.getType(), sole.getDistance(), Source.VISIBLE, victim, frame);
        return true;
    }

    /**
     * A visible enemy with a ground weapon near a hit no enemy was seen targeting.
     */
    public static final class Bystander {
        private final UnitType type;
        private final int distance;

        public Bystander(UnitType type, int distance) {
            this.type = type;
            this.distance = distance;
        }

        public UnitType getType() {
            return type;
        }

        public int getDistance() {
            return distance;
        }
    }

    /**
     * Learns from a Marine shot with no visible source that hit one of our units: the shot came from inside a
     * Bunker, and the nearest known Bunker within the radius of the shot is taken to have fired it. Bunker reach
     * rises to the gap between the Bunker's footprint and the victim's.
     *
     * @param shot where the shot landed
     * @param victimType type of the unit hit
     * @param victim where the unit hit stood
     * @param bunkers last known positions of living Bunkers
     * @param radius farthest a Bunker's center may be from the shot
     * @param frame current frame
     * @return true when a Bunker was credited with the shot
     */
    public boolean learnFromBunkerShot(Position shot, UnitType victimType, Position victim,
                                       Collection<Position> bunkers, int radius, int frame) {
        Position bunker = nearestWithin(shot, bunkers, radius);
        if (bunker == null) {
            return false;
        }
        int gap = footprintGap(UnitType.Terran_Bunker, bunker, victimType, victim);
        return learnFromHit(UnitType.Terran_Bunker, gap, Source.BULLET, victim, frame);
    }

    /**
     * Records a hit no known enemy accounts for. A hit near a live mark refreshes it instead of adding another.
     *
     * @param victim where the unit hit stood
     * @param frame current frame
     */
    public void recordHurt(Position victim, int frame) {
        for (HurtMark mark : liveHurtMarks(frame)) {
            if (mark.getPosition().getDistance(victim) <= HURT_MARK_MERGE_RADIUS) {
                mark.refresh(frame);
                return;
            }
        }
        hurtMarks.add(new HurtMark(victim, frame));
        ReachTelemetry.reachRaised(frame, null, -1, HURT_MARK_RADIUS, Source.HURTMARK, victim);
    }

    /**
     * Marks hit within the window, with the expired ones dropped.
     *
     * @param now current frame
     * @return the live marks
     */
    public List<HurtMark> liveHurtMarks(int now) {
        Iterator<HurtMark> it = hurtMarks.iterator();
        while (it.hasNext()) {
            if (it.next().isExpired(now)) {
                it.remove();
            }
        }
        return Collections.unmodifiableList(hurtMarks);
    }

    /**
     * Euclidean gap between two unit footprints, each a box around its center by the type's dimensions.
     *
     * @return gap in pixels rounded up, 0 when the boxes overlap
     */
    public static int footprintGap(UnitType a, Position aCenter, UnitType b, Position bCenter) {
        int dx = Math.max(0, Math.max(
                aCenter.getX() - a.dimensionLeft() - (bCenter.getX() + b.dimensionRight()),
                bCenter.getX() - b.dimensionLeft() - (aCenter.getX() + a.dimensionRight())));
        int dy = Math.max(0, Math.max(
                aCenter.getY() - a.dimensionUp() - (bCenter.getY() + b.dimensionDown()),
                bCenter.getY() - b.dimensionUp() - (aCenter.getY() + a.dimensionDown())));
        return (int) Math.ceil(Math.sqrt((double) dx * dx + (double) dy * dy));
    }

    static Position nearestWithin(Position from, Collection<Position> candidates, int radius) {
        Position best = null;
        double bestDistance = radius;
        for (Position candidate : candidates) {
            double distance = candidate.getDistance(from);
            if (distance <= bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Where one of our units was hit by something no known enemy accounts for, such as a shooter in fog or on high
     * ground.
     */
    public static final class HurtMark {
        private final Position position;
        private int frame;

        HurtMark(Position position, int frame) {
            this.position = position;
            this.frame = frame;
        }

        public Position getPosition() {
            return position;
        }

        public int getFrame() {
            return frame;
        }

        void refresh(int now) {
            frame = now;
        }

        boolean isExpired(int now) {
            return now - frame >= HURT_MARK_WINDOW;
        }

        /**
         * The ground not to stand on: a disc of {@link #HURT_MARK_RADIUS} around where the victim stood.
         *
         * @return the mark as a zone
         */
        public StaticDefenseZone toZone() {
            return new StaticDefenseZone(UnitType.None, position, HURT_MARK_RADIUS);
        }
    }
}
