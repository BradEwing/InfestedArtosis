package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import info.map.HarassHeatMap;
import lombok.Getter;
import util.Vec2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * How an air harass deals with anti-air it has not seen: when a base's anti-air counts as scouted, the exit on
 * anti-air first seen at the harass zone or at the flock, and the one point a flock retreats to when a harass ends.
 *
 * <p>A base's anti-air counts as sighted on a frame when any point of its core, the base center or the center of its
 * resources, is visible to us. The flock enters a base whole whatever its sighting age; the age is recorded with the
 * entry. Anti-air first seen while the flock harasses, a building or a mobile unit, ends the harass on the frame it
 * comes into view when the anti-air covering it exceeds what the flock tolerates, see {@link #antiAirReaction}. A base
 * the harass left for that reason is not entered for {@link #DEFENDED_REFUSAL_FRAMES}.
 *
 * <p>Every decision is a static function over plain values; the constants are tuning values, not Brood War facts.
 */
public final class AirHarassScouting {

    /** Tuning value: pixels from a target base's center within which newly seen anti-air ends the harass. */
    static final int NEW_AA_ZONE = HarassHeatMap.RADIUS_TILES * 32;
    /** Tuning value: pixels past a threat's reach that still count as the flock standing at it. */
    static final int EXIT_MARGIN = 128;
    /**
     * Tuning value: frames a base the flock left on newly seen anti-air is not entered, about two minutes of game
     * time.
     */
    static final int DEFENDED_REFUSAL_FRAMES = 2880;

    private AirHarassScouting() {
    }

    /**
     * Frames since a base's anti-air was last sighted.
     *
     * @param lastSightedFrame frame the base's core was last seen, or negative when it never was
     * @param now current frame
     * @return the age, or {@code now} for a base never sighted
     */
    public static int sightingAge(int lastSightedFrame, int now) {
        return lastSightedFrame < 0 ? now : now - lastSightedFrame;
    }

    /**
     * Whether known anti-air structures cover a point of a base: one covers it, see
     * {@link AirHarassTargeting.AirThreat#covers}. Mobile anti-air does not count, since it may have moved on.
     *
     * @param threats every known anti-air threat
     * @param corePoint the base's core point, see {@link #corePoint}
     * @return true when a known anti-air structure covers the point
     */
    public static boolean knownAntiAirCovers(Collection<AirHarassTargeting.AirThreat> threats, Position corePoint) {
        for (AirHarassTargeting.AirThreat threat : threats) {
            if (threat.getType().isBuilding() && threat.covers(corePoint, 0)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether an exit refuses the harassed base for {@link #DEFENDED_REFUSAL_FRAMES}: only newly seen anti-air does.
     *
     * @param reason why the harass ended
     * @return true for NEW_AA
     */
    public static boolean refusesBase(AirHarassEvaluator.ExitReason reason) {
        return reason == AirHarassEvaluator.ExitReason.NEW_AA;
    }

    /**
     * The bases a harass may enter now: every base whose refusal, see {@link #refusesBase}, has run out.
     *
     * @param bases candidate bases
     * @param refusedUntil last refused frame of each refused base
     * @param now current frame
     * @param <B> base type
     * @return the bases not refused, in their original order
     */
    public static <B> List<B> unrefused(Collection<B> bases, Map<B, Integer> refusedUntil, int now) {
        List<B> kept = new ArrayList<>();
        for (B base : bases) {
            Integer until = refusedUntil.get(base);
            if (until == null || now > until) {
                kept.add(base);
            }
        }
        return kept;
    }

    /**
     * Whether a base's core is in sight: any of its core points is visible to us.
     *
     * @param samples the base's core points
     * @param visible whether a point is visible to us
     * @return true when at least one point is visible
     */
    public static boolean coreSighted(List<Position> samples, Predicate<Position> visible) {
        for (Position sample : samples) {
            if (sample != null && visible.test(sample)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A point at a base where its anti-air stands: midway between the base center and its resources, or closer to
     * the resources when the midpoint would leave them farther than the sight range less a tile.
     *
     * @param baseCenter the base's center
     * @param resourceCenter the center of the base's resources, or null when it has none
     * @param sightRange a Mutalisk's sight range in pixels
     * @return the core point
     */
    public static Position corePoint(Position baseCenter, Position resourceCenter, int sightRange) {
        if (resourceCenter == null) {
            return baseCenter;
        }
        Vec2 toResources = Vec2.between(baseCenter, resourceCenter);
        double length = toResources.length();
        double reach = Math.max(0, sightRange - 32);
        if (length / 2 <= reach) {
            return new Position((baseCenter.getX() + resourceCenter.getX()) / 2,
                    (baseCenter.getY() + resourceCenter.getY()) / 2);
        }
        return toResources.normalizeToLength(length - reach).toPosition(baseCenter);
    }

    /**
     * What the flock reads from anti-air entering the target zone or its own cover on one frame.
     */
    @Getter
    public static final class Reaction {
        private final AirHarassTargeting.AirThreat trigger;
        private final int seenFrame;
        private final int turnFrame;
        private final int hitPointsLost;
        private final boolean atTarget;

        Reaction(AirHarassTargeting.AirThreat trigger, int seenFrame, int turnFrame, int hitPointsLost,
                 boolean atTarget) {
            this.trigger = trigger;
            this.seenFrame = seenFrame;
            this.turnFrame = turnFrame;
            this.hitPointsLost = hitPointsLost;
            this.atTarget = atTarget;
        }
    }

    /**
     * The anti-air the flock reacts to: every threat within {@link #NEW_AA_ZONE} of the target base, or whose reach
     * plus {@link #EXIT_MARGIN} covers the flock. Interceptors are left out, since a Carrier makes new ones all the
     * time.
     *
     * @param threats every known anti-air threat
     * @param baseCenter the target base's center, or null with none
     * @param flockCenter the flock's center
     * @return the threats at the target or the flock
     */
    public static List<AirHarassTargeting.AirThreat> inReach(Collection<AirHarassTargeting.AirThreat> threats,
                                                             Position baseCenter, Position flockCenter) {
        List<AirHarassTargeting.AirThreat> inReach = new ArrayList<>();
        for (AirHarassTargeting.AirThreat threat : threats) {
            boolean atBase = baseCenter != null && threat.getPosition().getDistance(baseCenter) <= NEW_AA_ZONE;
            boolean atFlock = flockCenter != null && threat.margin(flockCenter, EXIT_MARGIN) <= 0;
            if ((atBase || atFlock) && threat.getType() != UnitType.Protoss_Interceptor) {
                inReach.add(threat);
            }
        }
        return inReach;
    }

    /**
     * One frame of the flock's reaction to anti-air. A threat is new to the harass the first frame it is within
     * reach, see {@link #inReach}, however long it was known elsewhere; it ends the harass when the anti-air covering
     * it exceeds the tolerance, see {@link #antiAirReaction}. Threats already in reach when the harass started or
     * moved on are accepted, see {@link AirHarassState#acceptAntiAir}, and never new.
     *
     * @param state the harass state
     * @param threats every known anti-air threat
     * @param baseCenter the target base's center, or null with none
     * @param flockCenter the flock's center
     * @param tolerance anti-air strength the flock accepts
     * @param now current frame
     * @param flockHitPoints summed hit points of the Mutalisks now
     * @return the reaction, or null to stay
     */
    public static Reaction react(AirHarassState state, Collection<AirHarassTargeting.AirThreat> threats,
                                 Position baseCenter, Position flockCenter, double tolerance, int now,
                                 int flockHitPoints) {
        List<AirHarassTargeting.AirThreat> fresh = state.learnAntiAir(inReach(threats, baseCenter, flockCenter), now,
                flockHitPoints);
        AirHarassTargeting.AirThreat trigger = antiAirReaction(fresh, threats, baseCenter, flockCenter, tolerance);
        if (trigger == null) {
            return null;
        }
        AirHarassState.AntiAirSighting seen = state.earliestSighting(contributors(trigger, threats));
        int seenFrame = seen == null ? now : seen.getFrame();
        int hitPointsLost = seen == null ? 0 : Math.max(0, seen.getFlockHitPoints() - flockHitPoints);
        boolean atTarget = baseCenter != null && trigger.getPosition().getDistance(baseCenter) <= NEW_AA_ZONE;
        return new Reaction(trigger, seenFrame, now, hitPointsLost, atTarget);
    }

    /**
     * The anti-air seen for the first time that ends the harass: the first one, a building or a mobile unit, within
     * {@link #NEW_AA_ZONE} of the target base, or whose reach plus {@link #EXIT_MARGIN} covers the flock, where the
     * known anti-air covering it exceeds the flock's tolerance. A group of Goliaths ends the harass with the
     * Goliath whose sighting brings the group over the tolerance.
     *
     * @param newThreats anti-air seen for the first time this frame
     * @param threats every known anti-air threat
     * @param baseCenter the target base's center, or null with none
     * @param flockCenter the flock's center
     * @param tolerance anti-air strength the flock accepts
     * @return the threat that ends the harass, or null to stay
     */
    public static AirHarassTargeting.AirThreat antiAirReaction(
            Collection<AirHarassTargeting.AirThreat> newThreats, Collection<AirHarassTargeting.AirThreat> threats,
            Position baseCenter, Position flockCenter, double tolerance) {
        for (AirHarassTargeting.AirThreat threat : newThreats) {
            boolean inZone = baseCenter != null && threat.getPosition().getDistance(baseCenter) <= NEW_AA_ZONE
                    || flockCenter != null && threat.margin(flockCenter, EXIT_MARGIN) <= 0;
            if (inZone && AirHarassTargeting.defenseAt(threats, threat.getPosition(), AirHarassEvaluator.STRIKE_RADIUS)
                    > tolerance) {
                return threat;
            }
        }
        return null;
    }

    /**
     * The ids of the anti-air that makes up the defense a reaction read at a threat's position: every threat whose
     * zone, grown by {@link AirHarassEvaluator#STRIKE_RADIUS}, covers it.
     *
     * @param trigger the threat that ended the harass
     * @param threats every known anti-air threat
     * @return the ids, the trigger's among them
     */
    public static List<Integer> contributors(AirHarassTargeting.AirThreat trigger,
                                             Collection<AirHarassTargeting.AirThreat> threats) {
        List<Integer> ids = new ArrayList<>();
        for (AirHarassTargeting.AirThreat threat : threats) {
            if (threat.covers(trigger.getPosition(), AirHarassEvaluator.STRIKE_RADIUS)) {
                ids.add(threat.getId());
            }
        }
        return ids;
    }

    /**
     * The one point every Mutalisk retreats to when a harass ends: away from the anti-air near the flock, as far past
     * the flock as the longest reach among it plus {@link #EXIT_MARGIN}. Anti-air counts as near when the flock
     * stands within its reach plus {@link #NEW_AA_ZONE}.
     *
     * @param flockCenter the flock's center
     * @param threats every known anti-air threat
     * @return the exit point, not clamped to the map, or null with no anti-air near or no way away from it
     */
    public static Position sharedExitPoint(Position flockCenter, Collection<AirHarassTargeting.AirThreat> threats) {
        if (flockCenter == null) {
            return null;
        }
        double sumX = 0;
        double sumY = 0;
        int count = 0;
        int longestReach = 0;
        for (AirHarassTargeting.AirThreat threat : threats) {
            if (threat.margin(flockCenter, NEW_AA_ZONE) > 0) {
                continue;
            }
            sumX += threat.getPosition().getX();
            sumY += threat.getPosition().getY();
            count++;
            longestReach = Math.max(longestReach, threat.getReach());
        }
        if (count == 0) {
            return null;
        }
        Vec2 away = Vec2.between(new Position((int) Math.round(sumX / count), (int) Math.round(sumY / count)),
                flockCenter);
        if (away.length() == 0) {
            return null;
        }
        return away.normalizeToLength(longestReach + EXIT_MARGIN).toPosition(flockCenter);
    }
}
