package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import info.map.HarassHeatMap;
import util.Vec2;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * How an air harass deals with anti-air it has not seen: when a base's anti-air counts as scouted, the single
 * Mutalisk probe sent into a base whose sighting is stale, the exit on anti-air first seen inside the harass zone,
 * and the one point a flock retreats to when a harass ends.
 *
 * <p>A base's anti-air counts as sighted on a frame when any point of its core, the base center or the center of its
 * resources, is visible to us. A harass never strikes a base whose core was last sighted more than
 * {@link #STALE_SIGHTING_FRAMES} ago: the flock holds {@link #PROBE_HOLD_DISTANCE} short of it, or farther out where
 * known anti-air covers that point, while one Mutalisk flies to its resources and then over the strike point. The
 * flock strikes only once the probe has seen the center of the base's resources and sees the strike point, and a
 * tolerated strike point is left.
 *
 * <p>Every decision is a static function over plain values; the constants are tuning values, not Brood War facts.
 */
public final class AirHarassScouting {

    /** Tuning value: frames after which a base's anti-air sighting is stale, about 30 seconds of game time. */
    static final int STALE_SIGHTING_FRAMES = 720;
    /** Tuning value: pixels from the probed base's center at which the rest of the flock holds during a probe. */
    static final int PROBE_HOLD_DISTANCE = 640;
    /**
     * Tuning value: pixels past {@link #PROBE_HOLD_DISTANCE} the hold point may move out to, along the flock's side,
     * to leave the reach of known anti-air.
     */
    static final int PROBE_HOLD_SEARCH = 640;
    /** Tuning value: pixels between two hold points tried on the way out. */
    static final int PROBE_HOLD_STEP = 32;
    /** Tuning value: frames a probe may run without sighting the base's core before the harass gives up. */
    static final int PROBE_TIMEOUT_FRAMES = 720;
    /**
     * Tuning value: hit points the prober must fall below the most it has had during the probe for the probe to read
     * the base as defended, an eighth of a Mutalisk's; a single stray shot on the way in does not end the probe.
     */
    static final int PROBE_DAMAGE_HIT_POINTS = UnitType.Zerg_Mutalisk.maxHitPoints() / 8;
    /** Tuning value: pixels from a target base's center within which newly seen anti-air ends the harass. */
    static final int NEW_AA_ZONE = HarassHeatMap.RADIUS_TILES * 32;
    /** Tuning value: pixels past a threat's reach that still count as the flock standing at it. */
    static final int EXIT_MARGIN = 128;

    private AirHarassScouting() {
    }

    /**
     * What a probe has found so far.
     */
    public enum ProbeOutcome {
        WAIT,
        CLEAR,
        DEFENDED,
        TIMED_OUT
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
     * Whether a sighting is too old to strike on.
     *
     * @param age frames since the sighting
     * @return true past {@link #STALE_SIGHTING_FRAMES}
     */
    public static boolean stale(int age) {
        return age > STALE_SIGHTING_FRAMES;
    }

    /**
     * Turns an ENTER on a base whose anti-air sighting is stale into a PROBE. Every other verdict stands.
     *
     * @param verdict the entry gates' verdict
     * @param sightingAge frames since the chosen base's anti-air was sighted
     * @return PROBE for a stale ENTER, otherwise the verdict
     */
    public static AirHarassEvaluator.EntryVerdict entryMode(AirHarassEvaluator.EntryVerdict verdict,
                                                            int sightingAge) {
        if (verdict == AirHarassEvaluator.EntryVerdict.ENTER && stale(sightingAge)) {
            return AirHarassEvaluator.EntryVerdict.PROBE;
        }
        return verdict;
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
     * Where the probing Mutalisk flies: midway between the base center and its resources, or closer to the resources
     * when the midpoint would leave them farther than its sight range less a tile, so the probe always brings the
     * resources into sight.
     *
     * @param baseCenter the base's center
     * @param resourceCenter the center of the base's resources, or null when it has none
     * @param sightRange the prober's sight range in pixels
     * @return the probe point
     */
    public static Position probePoint(Position baseCenter, Position resourceCenter, int sightRange) {
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
     * Where the rest of the flock waits during a probe: {@link #PROBE_HOLD_DISTANCE} from the base center, on the
     * side the flock comes from, or where the flock is when it is already closer than that. When known anti-air
     * covers that point, see {@link #holdExposed}, the point moves out from the base in {@link #PROBE_HOLD_STEP}
     * steps, up to {@link #PROBE_HOLD_SEARCH} past the hold distance, to the first point no known anti-air covers.
     * When every point tried is covered, the flock holds at the one where the covering anti-air is weakest, the
     * farthest out on a tie.
     *
     * @param baseCenter the probed base's center
     * @param flockCenter the flock's center
     * @param threats every known anti-air threat
     * @return the hold point, not clamped to the map
     */
    public static Position holdPoint(Position baseCenter, Position flockCenter,
                                     Collection<AirHarassTargeting.AirThreat> threats) {
        Vec2 toFlock = Vec2.between(baseCenter, flockCenter);
        double flockDistance = toFlock.length();
        if (flockDistance == 0) {
            return flockCenter;
        }
        Position hold = flockDistance <= PROBE_HOLD_DISTANCE
                ? flockCenter
                : toFlock.normalizeToLength(PROBE_HOLD_DISTANCE).toPosition(baseCenter);
        double distance = Math.min(flockDistance, PROBE_HOLD_DISTANCE);
        double farthest = PROBE_HOLD_DISTANCE + PROBE_HOLD_SEARCH;
        Position weakest = hold;
        double weakestDefense = Double.MAX_VALUE;
        while (holdExposed(threats, hold)) {
            double defense = AirHarassTargeting.defenseAt(threats, hold, EXIT_MARGIN);
            if (defense <= weakestDefense) {
                weakest = hold;
                weakestDefense = defense;
            }
            if (distance + PROBE_HOLD_STEP > farthest) {
                return weakest;
            }
            distance += PROBE_HOLD_STEP;
            hold = toFlock.normalizeToLength(distance).toPosition(baseCenter);
        }
        return hold;
    }

    /**
     * Whether known anti-air covers a hold point: the point lies within a threat's reach plus {@link #EXIT_MARGIN}.
     *
     * @param threats every known anti-air threat
     * @param hold the hold point, or null
     * @return true when some threat covers it
     */
    public static boolean holdExposed(Collection<AirHarassTargeting.AirThreat> threats, Position hold) {
        if (hold == null) {
            return false;
        }
        for (AirHarassTargeting.AirThreat threat : threats) {
            if (threat.covers(hold, EXIT_MARGIN)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Picks the Mutalisk that probes: the one with the most hit points, the lowest id on a tie.
     *
     * @param hitPointsById hit points of each Mutalisk by unit id
     * @return the chosen unit id, or -1 with no Mutalisk
     */
    public static int chooseProber(Map<Integer, Integer> hitPointsById) {
        int best = -1;
        int bestHitPoints = -1;
        for (Map.Entry<Integer, Integer> entry : hitPointsById.entrySet()) {
            int id = entry.getKey();
            int hitPoints = entry.getValue();
            if (hitPoints > bestHitPoints || hitPoints == bestHitPoints && id < best) {
                best = id;
                bestHitPoints = hitPoints;
            }
        }
        return best;
    }

    /**
     * Whether a probe has seen the base's resources: already, or now. A base with no resources is judged on its
     * center instead. The base center alone never counts, since a base's anti-air stands by its resources.
     *
     * @param alreadySighted true once the probe has seen the resources
     * @param baseCenter the probed base's center
     * @param resourceCenter the center of the base's resources, or null when it has none
     * @param visible whether a point is visible to us now
     * @return true once the probe has seen the resources
     */
    public static boolean probeResourcesSighted(boolean alreadySighted, Position baseCenter, Position resourceCenter,
                                                Predicate<Position> visible) {
        return alreadySighted || visible.test(resourceCenter != null ? resourceCenter : baseCenter);
    }

    /**
     * Where the probing Mutalisk flies: the probe point until the base's resources are sighted, then the strike point,
     * so anti-air around the strike point is seen before the flock commits to it.
     *
     * @param resourcesSighted true once the probe has seen the base's resources, see {@link #probeResourcesSighted}
     * @param probePoint the probe point
     * @param strikePoint the strike point, or null when there is none
     * @return the destination
     */
    public static Position proberDestination(boolean resourcesSighted, Position probePoint, Position strikePoint) {
        return resourcesSighted && strikePoint != null ? strikePoint : probePoint;
    }

    /**
     * Whether a probe has seen enough to judge the base: the center of its resources since the probe started, and the
     * strike point now when there is one.
     *
     * @param resourcesSighted true once the probe has seen the base's resources, see {@link #probeResourcesSighted}
     * @param hasStrike true when the base has a tolerated strike point
     * @param strikeVisible true when that strike point is visible now
     * @return true when the probe can clear or refuse the base
     */
    public static boolean probeSighted(boolean resourcesSighted, boolean hasStrike, boolean strikeVisible) {
        return resourcesSighted && (!hasStrike || strikeVisible);
    }

    /**
     * The reason a harass ends with during a probe. Only the probe's own finding is relabelled: AA_ARRIVED because
     * the base keeps no tolerated strike point, while the flock itself stands outside anti-air it does not tolerate,
     * is the probe finding the base defended. AA_ARRIVED with the flock in anti-air stays AA_ARRIVED, and every other
     * reason stands.
     *
     * @param reason the generic exit reason, or null
     * @param probing true while the harass is probing
     * @param flockDefended true when the anti-air covering the flock's center exceeds its tolerance
     * @return PROBE_DEFENDED for the probe's own finding, otherwise the reason
     */
    public static AirHarassEvaluator.ExitReason probeExitReason(AirHarassEvaluator.ExitReason reason,
                                                                boolean probing, boolean flockDefended) {
        if (probing && !flockDefended && reason == AirHarassEvaluator.ExitReason.AA_ARRIVED) {
            return AirHarassEvaluator.ExitReason.PROBE_DEFENDED;
        }
        return reason;
    }

    /**
     * Whether a harassing Mutalisk zeroes the heat map around it. During a probe, a Mutalisk near the probed base
     * leaves the heat alone, so the probe does not cool the base the flock is about to strike; anywhere else it
     * cools as usual.
     *
     * @param probing true while the harass is probing
     * @param member the Mutalisk's position
     * @param probedBase the probed base's center, or null
     * @param sightRange the Mutalisk's sight range in pixels
     * @return true to zero the heat around it
     */
    public static boolean coolsHeat(boolean probing, Position member, Position probedBase, int sightRange) {
        return !probing || probedBase == null || member.getDistance(probedBase) > NEW_AA_ZONE + sightRange;
    }

    /**
     * What a probe has found, checked in order: the prober lost, or {@link #PROBE_DAMAGE_HIT_POINTS} or more below the
     * most hit points it has had during the probe, means the base is defended; a sighted base clears the strike when
     * a tolerated strike point is left and means defended otherwise; a probe running {@link #PROBE_TIMEOUT_FRAMES}
     * without a sighting times out.
     *
     * @param proberAlive true while the probing Mutalisk is still in the squad
     * @param proberHitPoints its hit points now
     * @param proberPeakHitPoints the most hit points it has had since the probe started
     * @param sighted true once the probe has seen enough, see {@link #probeSighted}
     * @param toleratedStrike true when the base still has a strike point the flock tolerates
     * @param now current frame
     * @param probeStartFrame frame the probe started
     * @return the outcome
     */
    public static ProbeOutcome probeOutcome(boolean proberAlive, int proberHitPoints, int proberPeakHitPoints,
                                            boolean sighted, boolean toleratedStrike, int now, int probeStartFrame) {
        if (!proberAlive || proberPeakHitPoints - proberHitPoints >= PROBE_DAMAGE_HIT_POINTS) {
            return ProbeOutcome.DEFENDED;
        }
        if (sighted) {
            return toleratedStrike ? ProbeOutcome.CLEAR : ProbeOutcome.DEFENDED;
        }
        if (now - probeStartFrame >= PROBE_TIMEOUT_FRAMES) {
            return ProbeOutcome.TIMED_OUT;
        }
        return ProbeOutcome.WAIT;
    }

    /**
     * Whether anti-air seen for the first time ends the harass: a newly seen anti-air building within
     * {@link #NEW_AA_ZONE} of the target base, or whose reach plus {@link #EXIT_MARGIN} covers the flock, where the
     * known anti-air covering it exceeds the flock's tolerance.
     *
     * @param newThreats anti-air seen for the first time this harass
     * @param threats every known anti-air threat
     * @param baseCenter the target base's center
     * @param flockCenter the flock's center
     * @param tolerance anti-air strength the flock accepts
     * @return true to leave
     */
    public static boolean newAntiAirExit(Collection<AirHarassTargeting.AirThreat> newThreats,
                                         Collection<AirHarassTargeting.AirThreat> threats, Position baseCenter,
                                         Position flockCenter, double tolerance) {
        for (AirHarassTargeting.AirThreat threat : newThreats) {
            if (!threat.getType().isBuilding()) {
                continue;
            }
            boolean inZone = baseCenter != null && threat.getPosition().getDistance(baseCenter) <= NEW_AA_ZONE
                    || flockCenter != null && threat.margin(flockCenter, EXIT_MARGIN) <= 0;
            if (inZone && AirHarassTargeting.defenseAt(threats, threat.getPosition(), AirHarassEvaluator.STRIKE_RADIUS)
                    > tolerance) {
                return true;
            }
        }
        return false;
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
