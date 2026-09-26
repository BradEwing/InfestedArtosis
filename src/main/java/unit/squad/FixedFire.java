package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import util.StaticDefenseZone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Ground an enemy fires on from where it stands, and the cooldown on targets inside it.
 *
 * <p>Fixed fire is the reach of a building, a sieged tank or a Lurker; a hurt mark is not fixed fire. When one of
 * our units is hurt inside a fixed fire zone, the zone cools for {@link #COOLDOWN_FRAMES} frames from the last hurt
 * inside it. While it cools, a fighter whose squad is not committing to the fight skips any target inside it that
 * stands out of the fighter's own range, see {@link #skipsTarget}. A zone is matched across frames by its type and a
 * centre within {@link #ZONE_MATCH_RADIUS}, since a sieged tank's last known position is where it is.
 */
final class FixedFire {

    /**
     * Tuning value: frames a fixed fire zone cools after the last hurt inside it. Matches the hurt mark window
     * ({@link info.tracking.EnemyReachMemory#HURT_MARK_WINDOW}), the time the reach memory already keeps an
     * unexplained hit as ground not to stand on.
     */
    static final int COOLDOWN_FRAMES = 480;

    /**
     * Tuning value: pixels between two zone centres of the same type for them to be read as one zone, allowing for a
     * last known position that moves by a few pixels as the unit is seen again.
     */
    static final int ZONE_MATCH_RADIUS = 32;

    /**
     * Tuning value: evade rings stepped outward from a unit when looking for a point out of fire. Each ring reaches
     * 128 px, so six reach past a sieged tank's learned reach from anywhere inside it.
     */
    static final int MAX_HOLD_STEPS = 6;

    private final List<CoolingZone> cooling = new ArrayList<>();
    private final Map<Long, Integer> skipsLogged = new HashMap<>();

    /**
     * Whether a zone of this type is fixed fire.
     *
     * @param type type the zone was built around, {@link UnitType#None} for a hurt mark
     * @return true for a building, a sieged tank or a Lurker
     */
    static boolean firesFromWhereItStands(UnitType type) {
        return type.isBuilding() || type == UnitType.Terran_Siege_Tank_Siege_Mode || type == UnitType.Zerg_Lurker;
    }

    /**
     * The fixed fire zones among the ground threat zones.
     *
     * @param groundThreatZones every ground threat zone, at the reach learned over the game
     * @return the zones around buildings, sieged tanks and Lurkers
     */
    static List<StaticDefenseZone> fixedFireZones(Collection<StaticDefenseZone> groundThreatZones) {
        List<StaticDefenseZone> kept = new ArrayList<>();
        for (StaticDefenseZone zone : groundThreatZones) {
            if (firesFromWhereItStands(zone.getStructure())) {
                kept.add(zone);
            }
        }
        return kept;
    }

    /**
     * Records one of our units hurt at a position: every zone covering it cools from this frame.
     *
     * @param victim where the unit stood
     * @param zones fixed fire zones that outrange the unit
     * @param padding pixels added to each zone's reach, covering the unit's extent and a margin
     * @param now current frame
     * @return the zones that were not already cooling
     */
    List<StaticDefenseZone> recordHurt(Position victim, Collection<StaticDefenseZone> zones, int padding, int now) {
        List<StaticDefenseZone> started = new ArrayList<>();
        for (StaticDefenseZone zone : zones) {
            if (!zone.covers(victim, padding)) {
                continue;
            }
            CoolingZone match = match(zone, now);
            if (match != null) {
                match.frame = now;
                continue;
            }
            cooling.add(new CoolingZone(zone.getStructure(), zone.getCenter(), now));
            started.add(zone);
        }
        return started;
    }

    /**
     * Whether a zone is cooling on this frame.
     *
     * @param zone the zone
     * @param now current frame
     * @return true when a hurt inside it was recorded within {@link #COOLDOWN_FRAMES}
     */
    boolean isCooling(StaticDefenseZone zone, int now) {
        return match(zone, now) != null;
    }

    /**
     * The zones that are cooling on this frame.
     *
     * @param zones zones to test
     * @param now current frame
     * @return the cooling ones
     */
    List<StaticDefenseZone> coolingZones(Collection<StaticDefenseZone> zones, int now) {
        List<StaticDefenseZone> kept = new ArrayList<>();
        for (StaticDefenseZone zone : zones) {
            if (isCooling(zone, now)) {
                kept.add(zone);
            }
        }
        return kept;
    }

    /**
     * Whether a skipped target is new for this attacker since the cooldown that skips it began, so the skip is
     * written once per attacker and target per cooldown.
     *
     * @param attackerId the fighter
     * @param targetId the target skipped
     * @param now current frame
     * @return true the first time the pair is skipped within {@link #COOLDOWN_FRAMES}
     */
    boolean firstSkip(int attackerId, int targetId, int now) {
        long key = (long) attackerId << 32 | targetId & 0xffffffffL;
        Integer logged = skipsLogged.get(key);
        if (logged != null && now - logged < COOLDOWN_FRAMES) {
            return false;
        }
        skipsLogged.put(key, now);
        return true;
    }

    /**
     * Drops the cooldowns and skip records that have run out.
     *
     * @param now current frame
     */
    void expire(int now) {
        cooling.removeIf(zone -> now - zone.frame >= COOLDOWN_FRAMES);
        Iterator<Map.Entry<Long, Integer>> it = skipsLogged.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() >= COOLDOWN_FRAMES) {
                it.remove();
            }
        }
    }

    private CoolingZone match(StaticDefenseZone zone, int now) {
        for (CoolingZone candidate : cooling) {
            if (candidate.type == zone.getStructure() && now - candidate.frame < COOLDOWN_FRAMES
                    && candidate.center.getDistance(zone.getCenter()) <= ZONE_MATCH_RADIUS) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Whether a fighter skips a target. A squad committing to the fight skips nothing, and neither does a fighter
     * that already has the target within its own range, since reaching it asks no step further into the fire.
     * Otherwise a target inside a cooling zone is skipped.
     *
     * @param target where the target stands
     * @param targetDistance edge distance from the fighter to the target
     * @param ownRange the fighter's ground weapon range
     * @param coolingZones cooling zones that outrange the fighter
     * @param padding pixels added to each zone's reach
     * @param committing whether the fighter's squad is committing to the fight
     * @return the cooling zone that skips the target, or null when it is kept
     */
    static StaticDefenseZone skippingZone(Position target, double targetDistance, int ownRange,
                                          Collection<StaticDefenseZone> coolingZones, int padding,
                                          boolean committing) {
        if (committing || targetDistance <= ownRange) {
            return null;
        }
        for (StaticDefenseZone zone : coolingZones) {
            if (zone.covers(target, padding)) {
                return zone;
            }
        }
        return null;
    }

    /**
     * The zone covering a position with the longest reach.
     *
     * @param position the position
     * @param zones zones to test
     * @param padding pixels added to each zone's reach
     * @return the zone, or null when none covers the position
     */
    static StaticDefenseZone coveringZone(Position position, Collection<StaticDefenseZone> zones, int padding) {
        StaticDefenseZone longest = null;
        for (StaticDefenseZone zone : zones) {
            if (zone.covers(position, padding) && (longest == null || zone.getReach() > longest.getReach())) {
                longest = zone;
            }
        }
        return longest;
    }

    /**
     * A point out of every zone plus the padding, found by stepping the evade ring outward from the unit: each step
     * moves to the point of {@link RunbyTargeting#findEvadePoint} farthest out of the zones, until one is clear, a
     * step gains no ground, or {@link #MAX_HOLD_STEPS} steps are taken.
     *
     * @param from the unit's position
     * @param zones the zones to leave
     * @param padding pixels added to each zone's reach
     * @param allowed points the unit may move to
     * @return the first clear point, else the farthest out point reached, or null when no step gains ground
     */
    static Position holdPoint(Position from, Collection<StaticDefenseZone> zones, int padding,
                              Predicate<Position> allowed) {
        Position current = from;
        double margin = RunbyTargeting.zoneMargin(from, zones, padding);
        for (int step = 0; step < MAX_HOLD_STEPS; step++) {
            Position next = RunbyTargeting.findEvadePoint(current, zones, padding, allowed, null);
            double nextMargin = next == null ? Double.NEGATIVE_INFINITY : RunbyTargeting.zoneMargin(next, zones,
                    padding);
            if (nextMargin <= margin) {
                break;
            }
            current = next;
            margin = nextMargin;
            if (margin >= 0) {
                break;
            }
        }
        return current == from ? null : current;
    }

    private static final class CoolingZone {
        private final UnitType type;
        private final Position center;
        private int frame;

        CoolingZone(UnitType type, Position center, int frame) {
            this.type = type;
            this.center = center;
            this.frame = frame;
        }
    }
}
