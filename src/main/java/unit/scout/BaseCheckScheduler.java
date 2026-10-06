package unit.scout;

import bwapi.Position;
import bwapi.UnitType;
import util.Time;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pure rules for re-checking bases that nothing has looked at for a while. Nothing here touches
 * {@code Game}: every input is a frame number, a distance or a position already measured by the caller.
 */
public final class BaseCheckScheduler {

    /**
     * A base is due for a check once it has gone this long unseen. A tuning value, one minute of game time,
     * kept apart from the 720 frame constant the air harass uses to age an anti-air sighting.
     */
    public static final int CHECK_INTERVAL_FRAMES = new Time(1, 0).getFrames();

    /** Tuning value: no base is checked before this frame, when the army is out and lings are spare. */
    public static final int FIRST_CHECK_FRAME = new Time(4, 0).getFrames();

    /** Tuning value: frames after which a check that has not seen its base is abandoned. */
    public static final int CHECK_TIMEOUT_FRAMES = 2 * CHECK_INTERVAL_FRAMES;

    /** Tuning value: a scout under this share of its hit points is recalled. */
    public static final double RECALL_HIT_POINT_SHARE = 0.5;

    /** Tuning value: an enemy this close to a checked base, in pixels, makes it occupied. */
    public static final int OCCUPIED_RADIUS_PIXELS = 320;

    /** Tuning value: ling checks, each to its own base, that may be out at once. */
    public static final int MAX_LING_CHECKS = 3;

    /** Tuning value: overlord checks that may be out at once. */
    public static final int MAX_OVERLORD_CHECKS = 1;

    /** Tuning value: the longest a base is left alone after failed checks, in check intervals. */
    public static final int MAX_BACKOFF_INTERVALS = 8;

    /** Tuning value: how long a scout's death site is remembered when routing checks, in frames. */
    public static final int DEATH_MEMORY_FRAMES = 4 * CHECK_INTERVAL_FRAMES;

    /** Tuning value: a route passing this close to a remembered death site, in pixels, is avoided. */
    public static final int DEATH_AVOID_RADIUS_PIXELS = 288;

    /**
     * Tuning value: no base other than a possible enemy main is checked before this frame. From here the
     * scheduler probes every available base that has gone unseen for {@link #CHECK_INTERVAL_FRAMES}.
     */
    public static final int PERIODIC_PROBE_START_FRAME = new Time(10, 0).getFrames();

    /** Tuning value: frames between zerglings sent to an enemy main that has never been seen. */
    public static final int SEARCH_DISPATCH_GAP_FRAMES = 240;

    /** Tuning value: a death site is matched to its anchoring defence within this many pixels. */
    public static final int ANCHOR_MATCH_PIXELS = 16;

    /** Zerglings sent to a base when Spider Mines are known, so one can trigger a mine for the other. */
    public static final int LINGS_WITH_MINES = 2;

    /** Zerglings sent to a base otherwise. */
    public static final int LINGS_WITHOUT_MINES = 1;

    /** Share of maximum hit points a unit needs to be picked for a check; the recall line is lower. */
    public static final double PICK_HIT_POINT_SHARE = 0.8;

    /** Frames after an HP recall within which the scout's death makes the check LOST rather than recalled. */
    public static final int RECALL_DEATH_WINDOW_FRAMES = 120;

    /** What became of a recalled scout: still undecided, died, or came home alive. */
    public enum RecallFate {
        PENDING,
        DIED,
        SURVIVED
    }

    /** Why a check ended; LOST is a scout that died, ABORTED one taken off the check some other way. */
    public enum Release {
        NONE,
        SEEN,
        HP_RECALL,
        TIMEOUT,
        THREAT,
        LOST,
        ABORTED
    }

    /** An enemy unit or building the caller has sighted, for judging whether a route is safe. */
    public static final class Sighting {
        private final UnitType type;
        private final Position position;

        public Sighting(UnitType type, Position position) {
            this.type = type;
            this.position = position;
        }

        public UnitType getType() {
            return type;
        }

        public Position getPosition() {
            return position;
        }
    }

    private BaseCheckScheduler() {
    }

    /**
     * @param lastSeenFrame the frame the base was last seen, or a negative value if never
     * @param now the current frame
     * @return frames since the base was last seen; a base never seen is as stale as can be
     */
    public static int age(int lastSeenFrame, int now) {
        if (lastSeenFrame < 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, now - lastSeenFrame);
    }

    /**
     * @param age frames since the base was last seen
     * @return true once the base has gone {@link #CHECK_INTERVAL_FRAMES} unseen
     */
    public static boolean isDue(int age) {
        return age >= CHECK_INTERVAL_FRAMES;
    }

    /**
     * Picks the base to check next: a start location never seen first, then the stalest due base, ties broken
     * by the shorter ground path. Bases already being checked are skipped. Returns null before
     * {@link #FIRST_CHECK_FRAME} or when no base is due.
     *
     * @param candidates the bases that may be checked
     * @param lastSeenFrames the frame each base was last seen; a base missing from the map was never seen
     * @param groundDistances ground path length to each base; a base missing from the map sorts last
     * @param inFlight bases a check is already under way for
     * @param now the current frame
     * @return the base to check, or null
     */
    public static <B> B next(Collection<B> candidates, Map<B, Integer> lastSeenFrames,
                             Map<B, Integer> groundDistances, Collection<B> inFlight, int now) {
        return next(candidates, lastSeenFrames, groundDistances, inFlight, Collections.emptySet(), now);
    }

    /**
     * As {@link #next(Collection, Map, Map, Collection, int)}, with the start locations named so that one never
     * seen is checked before any other base, however stale.
     *
     * @param startLocations the candidates that are start locations
     */
    public static <B> B next(Collection<B> candidates, Map<B, Integer> lastSeenFrames,
                             Map<B, Integer> groundDistances, Collection<B> inFlight,
                             Collection<B> startLocations, int now) {
        if (now < FIRST_CHECK_FRAME) {
            return null;
        }
        List<B> due = candidates.stream()
                .filter(base -> !inFlight.contains(base))
                .filter(base -> isDue(age(lastSeenFrames.getOrDefault(base, -1), now)))
                .collect(Collectors.toList());
        return due.stream()
                .min(Comparator
                        .comparing((B base) -> isUnscoutedStart(base, lastSeenFrames, startLocations) ? 0 : 1)
                        .thenComparing((B base) -> age(lastSeenFrames.getOrDefault(base, -1), now),
                                Comparator.reverseOrder())
                        .thenComparing(base -> groundDistances.getOrDefault(base, Integer.MAX_VALUE)))
                .orElse(null);
    }

    private static <B> boolean isUnscoutedStart(B base, Map<B, Integer> lastSeenFrames,
                                                Collection<B> startLocations) {
        return startLocations.contains(base) && lastSeenFrames.getOrDefault(base, -1) < 0;
    }

    /**
     * Whether a scout may be sent to a base the enemy is known to hold: not while a failed check still holds
     * it back, and not while the base has been seen within {@link #CHECK_INTERVAL_FRAMES}.
     *
     * @param age frames since the base was last seen
     * @param retryAfterFrame the first frame a failed check allows another, or 0 when none failed
     * @param now the current frame
     * @return true when a scout may be dispatched
     */
    public static boolean mayDispatchToHeldBase(int age, int retryAfterFrame, int now) {
        return isDue(age) && retryAfterFrame <= now;
    }

    /**
     * @param type an enemy unit or building type
     * @return true for a Bunker, Photon Cannon or Sunken Colony
     */
    public static boolean isStaticDefence(UnitType type) {
        return type == UnitType.Terran_Bunker || type == UnitType.Protoss_Photon_Cannon
                || type == UnitType.Zerg_Sunken_Colony;
    }

    /**
     * Where to remember a scout's death: the nearest known static defence within
     * {@link #DEATH_AVOID_RADIUS_PIXELS} of the place it died. A death with none near is not remembered, so a
     * scout killed by a mobile army or near our own bases blocks no route.
     *
     * @param death where the scout died
     * @param sightings enemy units and buildings sighted
     * @return the position of that defence, or null
     */
    public static Position deathSiteAnchor(Position death, Collection<Sighting> sightings) {
        Position nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Sighting sighting : sightings) {
            double distance = sighting.getPosition().getDistance(death);
            if (isStaticDefence(sighting.getType()) && distance <= DEATH_AVOID_RADIUS_PIXELS
                    && distance < nearestDistance) {
                nearest = sighting.getPosition();
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /**
     * @param deathFrame the frame the scout died
     * @param now the current frame
     * @param anchorAlive whether the defence the site is anchored on is still known to stand
     * @return true while the anchoring defence stands, and for {@link #DEATH_MEMORY_FRAMES} after the scout
     *     died otherwise
     */
    public static boolean isDeathRemembered(int deathFrame, int now, boolean anchorAlive) {
        return anchorAlive || now - deathFrame < DEATH_MEMORY_FRAMES;
    }

    /**
     * @param anchor the position of the defence a death site is anchored on
     * @param sightings enemy units and buildings still known to stand
     * @return true when a static defence stands within {@link #ANCHOR_MATCH_PIXELS} of the anchor
     */
    public static boolean isAnchorAlive(Position anchor, Collection<Sighting> sightings) {
        for (Sighting sighting : sightings) {
            if (isStaticDefence(sighting.getType())
                    && sighting.getPosition().getDistance(anchor) <= ANCHOR_MATCH_PIXELS) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param sightings enemy units and buildings sighted
     * @return the positions of the Bunkers, Photon Cannons and Sunken Colonies among them
     */
    public static List<Position> staticDefencePositions(Collection<Sighting> sightings) {
        List<Position> positions = new ArrayList<>();
        for (Sighting sighting : sightings) {
            if (isStaticDefence(sighting.getType())) {
                positions.add(sighting.getPosition());
            }
        }
        return positions;
    }

    /**
     * @param route points along the ground route to a base
     * @param defences positions of known static defence
     * @return the defences within {@link #DEATH_AVOID_RADIUS_PIXELS} of any route point
     */
    public static List<Position> defencesNearRoute(Collection<Position> route, Collection<Position> defences) {
        List<Position> near = new ArrayList<>();
        for (Position defence : defences) {
            for (Position point : route) {
                if (point.getDistance(defence) <= DEATH_AVOID_RADIUS_PIXELS) {
                    near.add(defence);
                    break;
                }
            }
        }
        return near;
    }

    /**
     * Whether two routes run past the same static defence, so two scouts sent along them would meet it
     * together.
     *
     * @param route points along the ground route of a candidate check
     * @param otherRoute points along the ground route of a check already out
     * @param defences positions of known static defence
     * @return the first defence both routes pass within {@link #DEATH_AVOID_RADIUS_PIXELS}, or null
     */
    public static Position sharedDefence(Collection<Position> route, Collection<Position> otherRoute,
                                         Collection<Position> defences) {
        List<Position> other = defencesNearRoute(otherRoute, defences);
        for (Position defence : defencesNearRoute(route, defences)) {
            if (other.contains(defence)) {
                return defence;
            }
        }
        return null;
    }

    /**
     * Which bases a check may go to now. Until {@link #PERIODIC_PROBE_START_FRAME} the only checks are the
     * search for the enemy main: a start location never seen, while the enemy main is not known. From then on
     * every candidate may be probed.
     *
     * @param candidates the bases that may be checked
     * @param lastSeenFrames the frame each base was last seen; a base missing from the map was never seen
     * @param startLocations the candidates that are start locations
     * @param enemyMainKnown whether the enemy main has been identified
     * @param now the current frame
     * @return the candidates a check may go to
     */
    public static <B> List<B> checkable(Collection<B> candidates, Map<B, Integer> lastSeenFrames,
                                        Collection<B> startLocations, boolean enemyMainKnown, int now) {
        if (now >= PERIODIC_PROBE_START_FRAME) {
            return new ArrayList<>(candidates);
        }
        if (enemyMainKnown) {
            return new ArrayList<>();
        }
        return candidates.stream()
                .filter(base -> isUnscoutedStart(base, lastSeenFrames, startLocations))
                .collect(Collectors.toList());
    }

    /**
     * Whether a death site still lies on the way a scout has to go, so that recalling the scout turns it away
     * from the site rather than back through it.
     *
     * @param scout where the scout is, or null when unknown
     * @param base the centre of the base the scout is sent to
     * @param site the death site
     * @return true when the site is nearer the base than the scout is
     */
    public static boolean isSiteAhead(Position scout, Position base, Position site) {
        return scout == null || scout.getDistance(base) > site.getDistance(base);
    }

    /**
     * How many zerglings to send to an enemy main now. One at a time while the main has never been seen, the next
     * only {@link #SEARCH_DISPATCH_GAP_FRAMES} after the last, so a defended main costs one zergling per gap.
     *
     * @param neverSeen whether the enemy main has never been seen
     * @param lastDispatchFrame the frame a zergling was last sent to it, or a negative value if none was
     * @param now the current frame
     * @param needed how many zerglings the scouting rules ask for
     * @return the number to send
     */
    public static int searchLingsToSend(boolean neverSeen, int lastDispatchFrame, int now, int needed) {
        if (!neverSeen) {
            return needed;
        }
        if (lastDispatchFrame >= 0 && now - lastDispatchFrame < SEARCH_DISPATCH_GAP_FRAMES) {
            return 0;
        }
        return Math.min(needed, 1);
    }

    /**
     * @param lastSeenFrame the frame the known enemy main was last seen, or a negative value if never
     * @param now the current frame
     * @return true when the enemy main is known but its tile has never been in sight and checks are allowed,
     *     so a scout is sent however recently an enemy unit was in sight elsewhere
     */
    public static boolean mustFindEnemyMain(int lastSeenFrame, int now) {
        return lastSeenFrame < 0 && now >= FIRST_CHECK_FRAME;
    }

    /**
     * Whether a route passes within {@link #DEATH_AVOID_RADIUS_PIXELS} of a place a scout died on a check.
     *
     * @param route points along the ground route to a base
     * @param deathSites where scouts died on earlier checks
     * @return true when any route point is that close to any death site
     */
    public static boolean routePassesDeathSite(Collection<Position> route, Collection<Position> deathSites) {
        return deathSiteOnRoute(route, deathSites) != null;
    }

    /**
     * @param route points along the ground route to a base
     * @param deathSites where scouts died on earlier checks
     * @return the first death site within {@link #DEATH_AVOID_RADIUS_PIXELS} of a route point, or null
     */
    public static Position deathSiteOnRoute(Collection<Position> route, Collection<Position> deathSites) {
        for (Position death : deathSites) {
            for (Position point : route) {
                if (point.getDistance(death) <= DEATH_AVOID_RADIUS_PIXELS) {
                    return death;
                }
            }
        }
        return null;
    }

    /**
     * The frame a base may next be checked after a check of it failed to see it. The wait is one check interval
     * after the first failure and doubles with each consecutive failure, up to {@link #MAX_BACKOFF_INTERVALS}
     * intervals.
     *
     * @param now the frame the check failed
     * @param consecutiveFailures failed checks of this base in a row, counting this one
     * @return the first frame the base may be checked again
     */
    public static int retryFrame(int now, int consecutiveFailures) {
        int intervals = 1 << Math.max(0, Math.min(consecutiveFailures - 1, 30));
        return now + CHECK_INTERVAL_FRAMES * Math.min(intervals, MAX_BACKOFF_INTERVALS);
    }

    /**
     * @param inFlight checks of this kind already out
     * @param overlord whether the check would use an overlord rather than zerglings
     * @return true while the cap for that kind has room
     */
    public static boolean mayStartCheck(int inFlight, boolean overlord) {
        return inFlight < (overlord ? MAX_OVERLORD_CHECKS : MAX_LING_CHECKS);
    }

    /**
     * @param hitPoints the scout's current hit points
     * @param maxHitPoints the scout's maximum hit points
     * @return false for a unit the recall would send home the moment it was pulled
     */
    public static boolean isHealthy(int hitPoints, int maxHitPoints) {
        return hitPoints >= maxHitPoints * RECALL_HIT_POINT_SHARE;
    }

    /**
     * @param hitPoints the unit's current hit points
     * @param maxHitPoints the unit's maximum hit points
     * @return true when the unit has recovered well past the recall line, so one regenerated hit point never
     *     puts a just-recalled unit back out
     */
    public static boolean isFitToScout(int hitPoints, int maxHitPoints) {
        return hitPoints >= maxHitPoints * PICK_HIT_POINT_SHARE;
    }

    /**
     * @param spiderMinesKnown whether the enemy is known to have Spider Mines
     * @return zerglings to send to one base
     */
    public static int lingsPerCheck(boolean spiderMinesKnown) {
        return spiderMinesKnown ? LINGS_WITH_MINES : LINGS_WITHOUT_MINES;
    }

    /**
     * Why a check should end now. Seeing the base ends it, so a scout is never held after its job is done;
     * the recall and the timeout end it without. Nothing else does: how many scouts are out is not a reason.
     *
     * @param baseSeen whether the base has been in sight since the check was dispatched
     * @param hitPoints the scout's current hit points
     * @param maxHitPoints the scout's maximum hit points
     * @param dispatchFrame the frame the check was dispatched
     * @param now the current frame
     * @return the reason to end the check, or {@link Release#NONE}
     */
    public static Release releaseReason(boolean baseSeen, int hitPoints, int maxHitPoints, int dispatchFrame,
                                        int now) {
        if (baseSeen) {
            return Release.SEEN;
        }
        if (!isHealthy(hitPoints, maxHitPoints)) {
            return Release.HP_RECALL;
        }
        if (now - dispatchFrame >= CHECK_TIMEOUT_FRAMES) {
            return Release.TIMEOUT;
        }
        return Release.NONE;
    }

    /**
     * Settles an HP recall: a scout that dies within {@link #RECALL_DEATH_WINDOW_FRAMES} of the recall was lost
     * on the check, one still alive after the window was recalled.
     *
     * @param alive whether the scout still exists
     * @param recallFrame the frame the scout was recalled
     * @param now the current frame
     * @return the fate, {@link RecallFate#PENDING} while the window is open and the scout lives
     */
    public static RecallFate recallFate(boolean alive, int recallFrame, int now) {
        if (!alive) {
            return RecallFate.DIED;
        }
        return now - recallFrame >= RECALL_DEATH_WINDOW_FRAMES ? RecallFate.SURVIVED : RecallFate.PENDING;
    }

    /**
     * Whether a zergling scouting for the enemy base should stop: once the base is located, or when it is
     * recalled at low hit points. How many zerglings are scouting is not a reason.
     *
     * @param hitPoints the zergling's current hit points
     * @param maxHitPoints the zergling's maximum hit points
     * @param enemyBaseLocated whether the enemy base is known
     */
    public static boolean endsZerglingScout(int hitPoints, int maxHitPoints, boolean enemyBaseLocated) {
        return enemyBaseLocated || hitPoints < maxHitPoints * RECALL_HIT_POINT_SHARE;
    }

    /**
     * Whether an overlord may fly a check: Pneumatized Carapace done, its route clear of anti-air, and
     * the enemy not yet something an overlord must stay away from (the Terran Marine veto and its kin).
     *
     * @param speedDone whether Pneumatized Carapace has finished
     * @param routeClear whether {@link #routeClear} holds for the overlord's route
     * @param overlordsMayScout whether ScoutData still lets overlords scout against this enemy
     */
    public static boolean overlordMayCheck(boolean speedDone, boolean routeClear, boolean overlordsMayScout) {
        return speedDone && routeClear && overlordsMayScout;
    }

    /**
     * Whether no sighted enemy would recall an overlord flying the straight route.
     *
     * @param from where the overlord starts
     * @param to the base it flies to
     * @param sightings enemy units and buildings sighted
     * @return true if {@link PerchThreat#threatens} holds for none of them, measured from its distance to the route
     */
    public static boolean routeClear(Position from, Position to, Collection<Sighting> sightings) {
        for (Sighting sighting : sightings) {
            double distance = distanceToSegment(sighting.getPosition(), from, to);
            if (PerchThreat.threatens(sighting.getType(), distance)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param enemyPositions positions of enemies sighted
     * @param baseCenter the center of the base checked
     * @return true if any enemy stands within {@link #OCCUPIED_RADIUS_PIXELS} of it
     */
    public static boolean isOccupied(Collection<Position> enemyPositions, Position baseCenter) {
        for (Position enemy : enemyPositions) {
            if (enemy.getDistance(baseCenter) <= OCCUPIED_RADIUS_PIXELS) {
                return true;
            }
        }
        return false;
    }

    static double distanceToSegment(Position point, Position start, Position end) {
        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared == 0) {
            return point.getDistance(start);
        }
        double t = ((point.getX() - start.getX()) * dx + (point.getY() - start.getY()) * dy) / lengthSquared;
        t = Math.max(0, Math.min(1, t));
        double nearestX = start.getX() + t * dx;
        double nearestY = start.getY() + t * dy;
        return Math.hypot(point.getX() - nearestX, point.getY() - nearestY);
    }
}
