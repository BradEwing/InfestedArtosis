package unit.scout;

import bwapi.Game;
import config.Config;
import bwapi.Position;
import bwapi.Race;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwem.Base;
import info.BaseData;
import info.GameState;
import info.InformationManager;
import info.ScoutData;
import info.map.GameMap;
import info.map.GroundPath;
import info.map.MapTile;
import info.tracking.ObservedUnit;
import info.map.ScoutPath;
import telemetry.BaseCheckEnd;
import telemetry.BaseCheckSkip;
import telemetry.BaseChecks;
import info.tracking.StrategyTracker;
import telemetry.PerchAssignments;
import telemetry.PlanEvents;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;
import util.TileFootprint;
import util.Time;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ScoutManager {

    final int FRAME_DRONE_SCOUT = 1440; // 1m
    static final int SKIP_LOG_INTERVAL_FRAMES = 240;
    static final int WALL_RECOMPUTE_FRAMES = 120;
    private  InformationManager informationManager;

    private Game game;
    private GameState gameState;

    private HashSet<ManagedUnit> scouts = new HashSet<>();
    private HashSet<ManagedUnit> droneScouts = new HashSet<>();
    private HashSet<ManagedUnit> zerglingScouts = new HashSet<>();

    private ScoutPath enemyMainScoutPath;

    private final Set<BunkerScoutGate.Destination> bunkerSkipsRecorded =
            EnumSet.noneOf(BunkerScoutGate.Destination.class);

    private List<ManagedUnit> recalledOverlords = new ArrayList<>();

    private final Map<ManagedUnit, BaseCheck> baseChecks = new HashMap<>();
    private final List<ManagedUnit> releasedChecks = new ArrayList<>();
    private final Map<ManagedUnit, RecalledCheck> recalledChecks = new HashMap<>();
    private final Map<Base, Integer> retryAfterFrames = new HashMap<>();
    private final Map<Base, Integer> consecutiveFailures = new HashMap<>();
    private final List<DeathSite> deathSites = new ArrayList<>();
    private final Map<String, Integer> skipLoggedFrames = new HashMap<>();
    private final Map<Base, Integer> lastDispatchFrames = new HashMap<>();
    private int[][] openDistances;
    private int[][] wallDistances;
    private int wallComputedFrame = -WALL_RECOMPUTE_FRAMES;

    private static final class DeathSite {
        private final Position position;
        private final int frame;

        private DeathSite(Position position, int frame) {
            this.position = position;
            this.frame = frame;
        }
    }

    private static final class BaseCheck {
        private final Base base;
        private final int dispatchFrame;
        private final int ageAtDispatch;
        private final boolean primary;

        private BaseCheck(Base base, int dispatchFrame, int ageAtDispatch, boolean primary) {
            this.base = base;
            this.dispatchFrame = dispatchFrame;
            this.ageAtDispatch = ageAtDispatch;
            this.primary = primary;
        }
    }

    private static final class RecalledCheck {
        private final BaseCheck check;
        private final BaseCheckScheduler.Release outcome;
        private final int recallFrame;
        private final boolean occupied;

        private RecalledCheck(BaseCheck check, BaseCheckScheduler.Release outcome, int recallFrame,
                              boolean occupied) {
            this.check = check;
            this.outcome = outcome;
            this.recallFrame = recallFrame;
            this.occupied = occupied;
        }
    }

    public ScoutManager(Game game, GameState gameState, InformationManager informationManager) {
        this.game = game;
        this.gameState = gameState;
        this.informationManager = informationManager;
    }

    public void onFrame() {
        updateBaseChecks();
        settleRecalledChecks();

        for (ManagedUnit managedUnit: scouts) {
            if (baseChecks.containsKey(managedUnit) || releasedChecks.contains(managedUnit)) {
                continue;
            }

            if (managedUnit.getRole() == UnitRole.PERCH) {
                if (isPerchThreatened(managedUnit)) {
                    recalledOverlords.add(managedUnit);
                }
                continue;
            }

            if (managedUnit.getUnitType() == UnitType.Zerg_Overlord && isEnemyBaseLocated()) {
                if (hasPerchedOverlord()) {
                    recalledOverlords.add(managedUnit);
                    continue;
                }
                if (tryPerch(managedUnit)) {
                    continue;
                }
            }

            if (managedUnit.getMovementTargetPosition() == null) {
                assignScoutMovementTarget(managedUnit);
            }
        }
    }

    /**
     * Picks the base the next check should go to. Before
     * {@link BaseCheckScheduler#PERIODIC_PROBE_START_FRAME} only a start location never seen is a candidate, and
     * only while the enemy main is unknown; from then on the stalest available base once it has gone
     * {@link BaseCheckScheduler#CHECK_INTERVAL_FRAMES} unseen, a start location never seen first. Bases already
     * being checked, behind a held Bunker, walled off from our side by known enemy buildings, held back after a
     * failed check, reached by a route passing a remembered static-defence death or any known Bunker, Cannon or
     * Sunken, or reached by a route past a static defence that a check already out also passes are skipped, and
     * a skip of the base the scheduler would have chosen is logged at most once per
     * {@link #SKIP_LOG_INTERVAL_FRAMES} and only when {@code logSkips} says a dispatch was otherwise possible.
     * Overlord checks do not count toward a shared defence.
     *
     * @param logSkips whether a check could have started now, so that a skip is a real one
     * @return the base to check, or null when none is due
     */
    public Base nextBaseCheck(boolean logSkips) {
        BaseData baseData = gameState.getBaseData();
        ScoutData scoutData = gameState.getScoutData();
        Set<Base> candidates = baseData.availableBases();
        Map<Base, Integer> lastSeenFrames = new HashMap<>();
        Map<Base, Integer> groundDistances = new HashMap<>();
        Set<Base> startLocations = new HashSet<>();
        for (Base base : candidates) {
            lastSeenFrames.put(base, scoutData.getBaseLastSeenFrame(base.getLocation()));
            GroundPath path = baseData.getBasePaths().get(base);
            if (path != null) {
                groundDistances.put(base, path.getGroundDistance());
            }
            if (baseData.isStartingBase(base)) {
                startLocations.add(base);
            }
        }
        int now = game.getFrameCount();
        List<Base> checkable = BaseCheckScheduler.checkable(candidates, lastSeenFrames, startLocations,
                baseData.getMainEnemyBase() != null, now);
        if (checkable.isEmpty()) {
            return null;
        }
        List<Base> blocked = new ArrayList<>();
        for (BaseCheck check : baseChecks.values()) {
            blocked.add(check.base);
        }
        for (Map.Entry<Base, Integer> retry : retryAfterFrames.entrySet()) {
            if (retry.getValue() > now) {
                blocked.add(retry.getKey());
            }
        }
        List<BaseCheckScheduler.Sighting> sightings = knownSightings();
        List<Position> recentDeaths = recentDeathSites(now, sightings);
        List<Position> defences = BaseCheckScheduler.staticDefencePositions(sightings);
        Map<Base, BaseCheckSkip> skipReasons = new HashMap<>();
        Map<Base, Position> skipSites = new HashMap<>();
        Set<Base> bunkerHeld = new HashSet<>();
        for (Base base : checkable) {
            if (blocked.contains(base)
                    || !BaseCheckScheduler.isDue(BaseCheckScheduler.age(lastSeenFrames.get(base), now))) {
                continue;
            }
            if (!mayScoutBase(base)) {
                bunkerHeld.add(base);
                continue;
            }
            BaseCheckSkip reason = BaseCheckSkip.WALLED;
            Position site = isWalledOff(base) ? base.getCenter() : null;
            if (site == null) {
                site = BaseCheckScheduler.deathSiteOnRoute(routePositions(base), recentDeaths);
                reason = BaseCheckSkip.DEATH_ROUTE;
            }
            if (site == null) {
                site = staticDefenceVeto(base, defences);
                reason = BaseCheckSkip.STATIC_DEFENCE;
            }
            if (site == null) {
                site = defenceSharedWithCheckInFlight(base, defences);
                reason = BaseCheckSkip.SHARED_DEFENCE;
            }
            if (site != null) {
                skipReasons.put(base, reason);
                skipSites.put(base, site);
            }
        }
        Base wanted = BaseCheckScheduler.next(checkable, lastSeenFrames, groundDistances, blocked,
                startLocations, now);
        List<Base> excluded = new ArrayList<>(blocked);
        excluded.addAll(skipReasons.keySet());
        excluded.addAll(bunkerHeld);
        Base chosen = BaseCheckScheduler.next(checkable, lastSeenFrames, groundDistances, excluded,
                startLocations, now);
        if (logSkips && wanted != null && !wanted.equals(chosen)) {
            if (bunkerHeld.contains(wanted)) {
                recordBunkerSkip(wanted);
            } else if (skipReasons.containsKey(wanted)) {
                logSkip(wanted, skipReasons.get(wanted), skipSites.get(wanted), now);
            }
        }
        return chosen;
    }

    /**
     * Whether a zergling may be sent to the enemy main. It has not been seen within
     * {@link BaseCheckScheduler#CHECK_INTERVAL_FRAMES}, no failed check still holds it back, and the route to
     * it does not pass where a scout recently died. While the main has never been seen, a death site lasts
     * {@link BaseCheckScheduler#DEATH_MEMORY_FRAMES} even if its defence still stands, so the search is
     * retried, and a check already out for another base does not hold it back. Once the main has been seen it
     * also shares no static defence on its route with a check already out. A refusal for either route reason
     * is logged when {@code logRefusals} says a zergling was available.
     *
     * @param logRefusals whether a spare zergling was available, so a refusal is a real one
     */
    public boolean mayCheckEnemyMain(Base enemyMain, boolean logRefusals) {
        int now = game.getFrameCount();
        int age = BaseCheckScheduler.age(gameState.getScoutData().getBaseLastSeenFrame(enemyMain.getLocation()),
                now);
        if (!BaseCheckScheduler.mayDispatchToHeldBase(age, retryAfterFrames.getOrDefault(enemyMain, 0), now)) {
            return false;
        }
        if (isWalledOff(enemyMain)) {
            if (logRefusals) {
                logSkip(enemyMain, BaseCheckSkip.WALLED, enemyMain.getCenter(), now);
            }
            return false;
        }
        boolean searching = mustFindEnemyMain();
        List<BaseCheckScheduler.Sighting> sightings = knownSightings();
        List<Position> sites = searching ? deathSitesWithinMemory(now) : recentDeathSites(now, sightings);
        Position death = BaseCheckScheduler.deathSiteOnRoute(routePositions(enemyMain), sites);
        if (death != null) {
            if (logRefusals) {
                logSkip(enemyMain, BaseCheckSkip.DEATH_ROUTE, death, now);
            }
            return false;
        }
        if (searching) {
            return true;
        }
        List<Position> defences = BaseCheckScheduler.staticDefencePositions(sightings);
        Position defence = staticDefenceVeto(enemyMain, defences);
        if (defence != null) {
            if (logRefusals) {
                logSkip(enemyMain, BaseCheckSkip.STATIC_DEFENCE, defence, now);
            }
            return false;
        }
        Position shared = defenceSharedWithCheckInFlight(enemyMain, defences);
        if (shared != null) {
            if (logRefusals) {
                logSkip(enemyMain, BaseCheckSkip.SHARED_DEFENCE, shared, now);
            }
            return false;
        }
        return true;
    }

    private Position staticDefenceVeto(Base base, List<Position> defences) {
        if (defences.isEmpty()) {
            return null;
        }
        List<Position> route = routePositions(base);
        route.add(base.getCenter());
        return BaseCheckScheduler.staticDefenceOnRoute(route, defences);
    }

    private List<Position> deathSitesWithinMemory(int now) {
        List<Position> positions = new ArrayList<>();
        for (DeathSite site : deathSites) {
            if (BaseCheckScheduler.isDeathRemembered(site.frame, now, false)) {
                positions.add(site.position);
            }
        }
        return positions;
    }

    /**
     * Whether known enemy buildings seal every ground route from our main to a base, so a zergling sent there
     * would meet the wall. Measured on tile steps with the enemy buildings other than town halls that stand
     * grounded, or were last seen lifted near where they stood, blocked, and refreshed every
     * {@link #WALL_RECOMPUTE_FRAMES} frames. A base the ground walk reaches with no buildings blocked, and not with
     * them, is walled; one terrain alone cuts off, or with no wall known, is not.
     *
     * @param base the base a ground check would go to
     * @return true when no ground route reaches the base
     */
    public boolean isWalledOff(Base base) {
        refreshWallDistances();
        TilePosition source = gameState.getBaseData().mainBasePosition();
        return BaseReachability.isWalledOff(openDistances, wallDistances, source, base.getLocation());
    }

    private void refreshWallDistances() {
        int now = game.getFrameCount();
        if (wallDistances != null && now - wallComputedFrame < WALL_RECOMPUTE_FRAMES) {
            return;
        }
        TilePosition source = gameState.getBaseData().mainBasePosition();
        if (openDistances == null) {
            openDistances = gameState.getGameMap().groundStepDistances(source, tile -> false);
        }
        List<TileFootprint> footprints = gameState.getObservedUnitTracker()
                .getBlockingFootprints(type -> type.isBuilding() && !type.isResourceDepot(), new Time(now));
        wallDistances = gameState.getGameMap().groundStepDistances(source,
                BaseReachability.blockedTiles(footprints)::contains);
        wallComputedFrame = now;
    }

    private boolean isOnOurSideOfWall(ManagedUnit scout) {
        refreshWallDistances();
        return scout.getPosition() != null
                && BaseReachability.reachesNear(wallDistances, scout.getPosition().toTilePosition());
    }

    /**
     * @return true when the enemy main is known but its tile has never been seen and checks are allowed, so
     *     the search for it does not wait for a lull in enemy sightings; never while base checks are switched off
     */
    public boolean mustFindEnemyMain() {
        Base enemyMain = gameState.getBaseData().getMainEnemyBase();
        return Config.baseChecks && enemyMain != null && BaseCheckScheduler.mustFindEnemyMain(
                gameState.getScoutData().getBaseLastSeenFrame(enemyMain.getLocation()), game.getFrameCount());
    }

    private Position defenceSharedWithCheckInFlight(Base base, List<Position> defences) {
        if (defences.isEmpty() || baseChecks.isEmpty()) {
            return null;
        }
        List<Position> route = routePositions(base);
        for (Map.Entry<ManagedUnit, BaseCheck> entry : baseChecks.entrySet()) {
            BaseCheck check = entry.getValue();
            if (check.base.equals(base) || entry.getKey().getUnitType() == UnitType.Zerg_Overlord) {
                continue;
            }
            Position shared = BaseCheckScheduler.sharedDefence(route, routePositions(check.base), defences);
            if (shared != null) {
                return shared;
            }
        }
        return null;
    }

    private void logSkip(Base base, BaseCheckSkip reason, Position site, int now) {
        String key = base.getLocation() + ":" + reason;
        Integer logged = skipLoggedFrames.get(key);
        if (logged != null && now - logged < SKIP_LOG_INTERVAL_FRAMES) {
            return;
        }
        skipLoggedFrames.put(key, now);
        BaseChecks.skipped(now, base.getLocation(), reason, site);
    }

    private List<Position> recentDeathSites(int now, List<BaseCheckScheduler.Sighting> sightings) {
        deathSites.removeIf(site -> !BaseCheckScheduler.isDeathRemembered(site.frame, now,
                BaseCheckScheduler.isAnchorAlive(site.position, sightings)));
        List<Position> positions = new ArrayList<>();
        for (DeathSite site : deathSites) {
            positions.add(site.position);
        }
        return positions;
    }

    private List<Position> routePositions(Base base) {
        List<Position> route = new ArrayList<>();
        GroundPath path = gameState.getBaseData().getBasePaths().get(base);
        if (path != null) {
            for (MapTile tile : path.getPath()) {
                route.add(tile.getTile().toPosition());
            }
        }
        return route;
    }

    private void recordDeathSite(ManagedUnit scout, int frame) {
        Position position = scout.getPosition();
        if (position == null || scout.getUnitType() != UnitType.Zerg_Zergling) {
            return;
        }
        Position anchor = BaseCheckScheduler.deathSiteAnchor(position, knownSightings());
        if (anchor != null) {
            deathSites.add(new DeathSite(anchor, frame));
            recallChecksPast(anchor, frame);
        }
    }

    private void recallChecksPast(Position site, int now) {
        List<Position> sites = Collections.singletonList(site);
        for (Map.Entry<ManagedUnit, BaseCheck> entry : new ArrayList<>(baseChecks.entrySet())) {
            ManagedUnit scout = entry.getKey();
            BaseCheck check = entry.getValue();
            if (!BaseCheckScheduler.routePassesDeathSite(routePositions(check.base), sites)
                    || !BaseCheckScheduler.isSiteAhead(scout.getPosition(), check.base.getCenter(), site)) {
                continue;
            }
            finishBaseCheck(scout, check, BaseCheckScheduler.Release.THREAT);
            releasedChecks.add(scout);
            BaseChecks.skipped(now, check.base.getLocation(), BaseCheckSkip.DEATH_RECALL, site);
        }
    }

    private List<BaseCheckScheduler.Sighting> knownSightings() {
        List<BaseCheckScheduler.Sighting> sightings = new ArrayList<>();
        for (ObservedUnit observed : gameState.getObservedUnitTracker().getLivingObservedUnits()) {
            Position position = observed.getCurrentOrLastKnownPosition();
            if (position != null) {
                sightings.add(new BaseCheckScheduler.Sighting(observed.getUnitType(), position));
            }
        }
        return sightings;
    }

    /**
     * Whether another check of this kind may start: at most {@link BaseCheckScheduler#MAX_LING_CHECKS} ling
     * checks and {@link BaseCheckScheduler#MAX_OVERLORD_CHECKS} overlord check are out at once. Lings headed
     * for the enemy main do not count.
     */
    public boolean mayStartCheck(boolean overlord) {
        Base enemyMain = gameState.getBaseData().getMainEnemyBase();
        int inFlight = 0;
        for (Map.Entry<ManagedUnit, BaseCheck> entry : baseChecks.entrySet()) {
            BaseCheck check = entry.getValue();
            boolean isOverlord = entry.getKey().getUnitType() == UnitType.Zerg_Overlord;
            if (check.primary && isOverlord == overlord && !check.base.equals(enemyMain)) {
                inFlight++;
            }
        }
        return BaseCheckScheduler.mayStartCheck(inFlight, overlord);
    }

    public int lingsPerCheck() {
        return BaseCheckScheduler.lingsPerCheck(gameState.enemyUnitCount(UnitType.Terran_Vulture_Spider_Mine) > 0);
    }

    /**
     * Whether an overlord may fly a check to this base: Pneumatized Carapace is done, no sighted enemy would
     * recall it along the route, and the enemy is not one overlords are kept away from.
     */
    public boolean mayOverlordCheck(ManagedUnit overlord, Base base) {
        return BaseCheckScheduler.overlordMayCheck(gameState.getTechProgression().isOverlordSpeed(),
                routeClear(overlord.getPosition(), base.getCenter()), overlordsMayScout());
    }

    private boolean overlordsMayScout() {
        List<UnitType> livingTypes = new ArrayList<>();
        for (ObservedUnit observed : gameState.getObservedUnitTracker().getLivingObservedUnits()) {
            livingTypes.add(observed.getUnitType());
        }
        return gameState.getScoutData().shouldOverlordsContinueScouting(gameState.getOpponentRace(), livingTypes);
    }

    private boolean routeClear(Position from, Position to) {
        List<BaseCheckScheduler.Sighting> sightings = new ArrayList<>();
        for (ObservedUnit observed : gameState.getObservedUnitTracker().getLivingObservedUnits()) {
            Position position = observed.getCurrentOrLastKnownPosition();
            if (position != null) {
                sightings.add(new BaseCheckScheduler.Sighting(observed.getUnitType(), position));
            }
        }
        return BaseCheckScheduler.routeClear(from, to, sightings);
    }

    /**
     * @return true while a released scout's check is held back awaiting word on whether it survived
     */
    public boolean hasPendingRecall(ManagedUnit managedUnit) {
        return recalledChecks.containsKey(managedUnit);
    }

    public boolean isBaseCheckScout(ManagedUnit managedUnit) {
        return baseChecks.containsKey(managedUnit);
    }

    public boolean isScout(ManagedUnit managedUnit) {
        return scouts.contains(managedUnit);
    }

    /**
     * Sends a unit to see a base and holds it there until the base has been seen, the unit is recalled at low
     * hit points, or the check times out.
     *
     * @param primary whether this unit's outcome is the check's primary telemetry row; the second zergling of a
     *     pair writes a row marked not primary and does not count toward the in-flight cap
     */
    public void beginBaseCheck(ManagedUnit managedUnit, Base base, boolean primary) {
        int now = game.getFrameCount();
        int lastSeen = gameState.getScoutData().getBaseLastSeenFrame(base.getLocation());
        releaseActiveScoutTarget(managedUnit);
        managedUnit.setRole(UnitRole.SCOUT);
        managedUnit.setMovementTargetPosition(base.getLocation());
        scouts.add(managedUnit);
        if (managedUnit.getUnitType() == UnitType.Zerg_Zergling) {
            zerglingScouts.add(managedUnit);
        }
        baseChecks.put(managedUnit, new BaseCheck(base, now, BaseCheckScheduler.age(lastSeen, now), primary));
        lastDispatchFrames.put(base, now);
    }

    /**
     * Drains and returns the units whose base check ended this frame. They are still scouts until the caller
     * removes them.
     */
    public List<ManagedUnit> drainReleasedChecks() {
        List<ManagedUnit> drained = new ArrayList<>(releasedChecks);
        releasedChecks.clear();
        return drained;
    }

    private void updateBaseChecks() {
        int now = game.getFrameCount();
        ScoutData scoutData = gameState.getScoutData();
        for (Map.Entry<ManagedUnit, BaseCheck> entry : new ArrayList<>(baseChecks.entrySet())) {
            ManagedUnit scout = entry.getKey();
            BaseCheck check = entry.getValue();
            if (baseChecks.get(scout) != check) {
                continue;
            }
            Unit unit = scout.getUnit();
            scout.setRole(UnitRole.SCOUT);
            boolean seen = scoutData.getBaseLastSeenFrame(check.base.getLocation()) >= check.dispatchFrame;
            BaseCheckScheduler.Release reason = BaseCheckScheduler.releaseReason(seen, unit.getHitPoints(),
                    unit.getType().maxHitPoints(), check.dispatchFrame, now);
            if (reason == BaseCheckScheduler.Release.NONE && scout.getUnitType() == UnitType.Zerg_Overlord
                    && !routeClear(scout.getPosition(), check.base.getCenter())) {
                reason = BaseCheckScheduler.Release.THREAT;
            }
            if (reason == BaseCheckScheduler.Release.NONE && scout.getUnitType() != UnitType.Zerg_Overlord
                    && isWalledOff(check.base) && isOnOurSideOfWall(scout)) {
                reason = BaseCheckScheduler.Release.THREAT;
                BaseChecks.skipped(now, check.base.getLocation(), BaseCheckSkip.WALLED, check.base.getCenter());
            }
            if (reason == BaseCheckScheduler.Release.NONE && scout.getUnitType() != UnitType.Zerg_Overlord
                    && !mayScoutBase(check.base)) {
                reason = BaseCheckScheduler.Release.THREAT;
                recordBunkerSkip(check.base);
            }
            if (reason != BaseCheckScheduler.Release.NONE) {
                finishBaseCheck(scout, check, reason);
                releasedChecks.add(scout);
            }
        }
    }

    private void finishBaseCheck(ManagedUnit scout, BaseCheck check, BaseCheckScheduler.Release outcome) {
        baseChecks.remove(scout);
        int now = game.getFrameCount();
        if (outcome == BaseCheckScheduler.Release.SEEN) {
            retryAfterFrames.remove(check.base);
            consecutiveFailures.remove(check.base);
        } else if (retryAfterFrames.getOrDefault(check.base, 0) <= now) {
            int failures = consecutiveFailures.getOrDefault(check.base, 0) + 1;
            consecutiveFailures.put(check.base, failures);
            retryAfterFrames.put(check.base, BaseCheckScheduler.retryFrame(now, failures));
        }
        if (outcome == BaseCheckScheduler.Release.LOST) {
            recordDeathSite(scout, now);
        }
        List<Position> enemyPositions = new ArrayList<>();
        for (Unit enemy : gameState.getVisibleEnemyUnits()) {
            enemyPositions.add(enemy.getPosition());
        }
        boolean occupied = BaseCheckScheduler.isOccupied(enemyPositions, check.base.getCenter());
        if (outcome == BaseCheckScheduler.Release.HP_RECALL || outcome == BaseCheckScheduler.Release.TIMEOUT
                || outcome == BaseCheckScheduler.Release.THREAT) {
            RecalledCheck earlier = recalledChecks.put(scout, new RecalledCheck(check, outcome, now, occupied));
            if (earlier != null) {
                writeCheck(scout, earlier.check, earlier.recallFrame, earlier.outcome, earlier.occupied, -1);
            }
            return;
        }
        writeCheck(scout, check, now, outcome, occupied, outcome == BaseCheckScheduler.Release.LOST ? now : -1);
    }

    private void settleRecalledChecks() {
        int now = game.getFrameCount();
        for (Map.Entry<ManagedUnit, RecalledCheck> entry : new ArrayList<>(recalledChecks.entrySet())) {
            ManagedUnit scout = entry.getKey();
            RecalledCheck recalled = entry.getValue();
            BaseCheckScheduler.RecallFate fate = BaseCheckScheduler.recallFate(scout.getUnit().exists(),
                    recalled.recallFrame, now);
            if (fate == BaseCheckScheduler.RecallFate.PENDING) {
                continue;
            }
            recalledChecks.remove(scout);
            if (fate == BaseCheckScheduler.RecallFate.DIED) {
                recordDeathSite(scout, now);
                writeCheck(scout, recalled.check, recalled.recallFrame, BaseCheckScheduler.Release.LOST,
                        recalled.occupied, now);
            } else {
                writeCheck(scout, recalled.check, recalled.recallFrame, recalled.outcome,
                        recalled.occupied, -1);
            }
        }
    }

    private void writeCheck(ManagedUnit scout, BaseCheck check, int endFrame, BaseCheckScheduler.Release outcome,
                            boolean occupied, int diedFrame) {
        BaseChecks.checked(scout.getUnitID(), scout.getUnitType(), check.base.getLocation(), check.ageAtDispatch,
                check.dispatchFrame, new BaseCheckEnd(endFrame, outcome, occupied, diedFrame, check.primary));
    }

    private boolean hasPerchedOverlord() {
        for (ManagedUnit scout : scouts) {
            if (scout.getRole() == UnitRole.PERCH) {
                return true;
            }
        }
        return false;
    }

    /**
     * Drains and returns the overlords sent home this frame: perched overlords an enemy unit
     * came within threatening range of, and scouting overlords no longer needed because another
     * overlord already watches the located enemy base.
     */
    public List<ManagedUnit> drainRecalledOverlords() {
        List<ManagedUnit> drained = new ArrayList<>(recalledOverlords);
        recalledOverlords.clear();
        return drained;
    }

    private boolean isPerchThreatened(ManagedUnit overlord) {
        Position overlordPosition = overlord.getPosition();
        for (Unit enemy : gameState.getVisibleEnemyUnits()) {
            double distance = enemy.getPosition().getDistance(overlordPosition);
            if (PerchThreat.threatens(enemy.getType(), distance)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Moves an overlord scout to a perch watching the best-known enemy location. The perch is ranked
     * from where the overlord stands, so it takes the nearest safe perch rather than the one closest
     * to the enemy. Against Zerg, whose early units cannot shoot up, or on a map with no perch, the
     * overlord holds over the watch target itself instead of falling back into map scouting; the
     * perch leave predicate still recalls it when air or hydralisk tech appears.
     *
     * @param overlord the overlord to perch
     * @return true if a watch target exists and the overlord was assigned to hold there
     */
    public boolean tryPerch(ManagedUnit overlord) {
        Position watchTarget = perchWatchTarget(overlord);
        if (watchTarget == null) {
            return false;
        }

        Position perchPosition = watchTarget;
        boolean usedPerchTile = false;
        if (gameState.getOpponentRace() != Race.Zerg) {
            MapTile perch = gameState.getGameMap().findPerchNear(watchTarget, overlord.getPosition());
            if (perch != null) {
                perchPosition = perch.getTile().toPosition().add(new Position(16, 16));
                usedPerchTile = true;
            }
        }

        PerchAssignments.assigned(overlord, perchPosition, watchTarget, usedPerchTile);
        releaseActiveScoutTarget(overlord);
        overlord.setPerchPosition(perchPosition);
        overlord.setMovementTargetPosition(null);
        overlord.setRole(UnitRole.PERCH);
        return true;
    }

    private Position perchWatchTarget(ManagedUnit overlord) {
        BaseData baseData = gameState.getBaseData();
        if (baseData.knowEnemyMainBase()) {
            return baseData.getMainEnemyBase().getCenter();
        }

        ScoutData scoutData = gameState.getScoutData();
        HashSet<TilePosition> enemyBuildingPositions = scoutData.getEnemyBuildingPositions();
        if (!enemyBuildingPositions.isEmpty()) {
            return enemyBuildingPositions.iterator().next().toPosition();
        }

        TilePosition movementTarget = overlord.getMovementTargetPosition();
        if (movementTarget != null) {
            return movementTarget.toPosition();
        }

        return null;
    }

    private void releaseActiveScoutTarget(ManagedUnit managedUnit) {
        TilePosition movementTarget = managedUnit.getMovementTargetPosition();
        HashSet<TilePosition> activeScoutTargets = gameState.getScoutData().getActiveScoutTargets();
        if (movementTarget != null && activeScoutTargets.contains(movementTarget)) {
            activeScoutTargets.remove(movementTarget);
        }
    }

    public boolean isDroneScout(Unit unit) {
        return droneScouts.stream().anyMatch(mu -> mu.getUnit().equals(unit));
    }

    public void addScout(ManagedUnit managedUnit) {
        managedUnit.setRole(UnitRole.SCOUT);
        assignScoutMovementTarget(managedUnit);
        scouts.add(managedUnit);
        if (managedUnit.getUnitType() == UnitType.Zerg_Drone) {
            droneScouts.add(managedUnit);
        } else if (managedUnit.getUnitType() == UnitType.Zerg_Zergling) {
            zerglingScouts.add(managedUnit);
        }
    }

    public void removeScout(ManagedUnit managedUnit) {
        if (managedUnit == null) {
            return;
        }

        BaseCheck openCheck = baseChecks.get(managedUnit);
        if (openCheck != null) {
            boolean lost = !managedUnit.getUnit().exists();
            finishBaseCheck(managedUnit, openCheck,
                    lost ? BaseCheckScheduler.Release.LOST : BaseCheckScheduler.Release.ABORTED);
        }
        releasedChecks.remove(managedUnit);
        releaseActiveScoutTarget(managedUnit);
        managedUnit.setPerchPosition(null);
        scouts.remove(managedUnit);
        droneScouts.remove(managedUnit);
        zerglingScouts.remove(managedUnit);
    }

    /**
     * Determine if a drone scout is needed. By default, send a new drone at 1m (frame 1440)
     *
     * Does not send a drone scout in ZvZ.
     *
     * TODO: Consider early scout if opponent model predicts rush/cheese
     * TODO: Consider early scout if opponent is random
     * TODO: Track enemy tech to predict enemy build
     * TODO: Change strategy / unit composition to best counter enemy build
     * TODO: Consider multiple drone scouts case
     * @return
     */
    public boolean needDroneScout() {
        if (Objects.equals(gameState.getActiveBuildOrder().getName(), "4Pool")) {
            return false;
        }

        if (gameState.getBaseData().getMainEnemyBase() != null) {
            return false;
        }

        if (game.enemy().getRace() == Race.Zerg) {
            return false;
        }

        if (game.getFrameCount() < FRAME_DRONE_SCOUT) {
            return false;
        }

        if (droneScouts.size() > 0) {
            return false;
        }

        return true;
    }

    public boolean endDroneScout() {
        for (ManagedUnit managedUnit: droneScouts) {
            Unit unit = managedUnit.getUnit();
            if (unit.isUnderAttack() || unit.getHitPoints() < unit.getType().maxHitPoints() * 0.5) {
                return true;
            }
        }

        Set<Position> basePositions = gameState.getBaseData().getMyBasePositions();
        Set<Unit> nearbyWorkers = gameState.getObservedUnitTracker()
                .getWorkerUnitsNearPositions(basePositions, 512);
        if (nearbyWorkers.size() >= 3) {
            return true;
        }

        return false;
    }

    /**
     * Zergling scouts are looking for the enemy base, so they are only recalled once it has been
     * located. Sighting an enemy unit is not enough: an enemy Overlord crossing the map on its own
     * scout says nothing about where the enemy lives.
     */
    public boolean endZerglingScout(ManagedUnit managedUnit) {
        Unit unit = managedUnit.getUnit();
        return BaseCheckScheduler.endsZerglingScout(unit.getHitPoints(), unit.getType().maxHitPoints(),
                isEnemyBaseLocated());
    }

    /**
     * Whether a ground scout may route to the enemy main: not while a detected Bunker holds the natural or the main,
     * see {@link BunkerScoutGate}. Opening the route re-arms {@link #recordBunkerSkip}.
     */
    boolean mayScoutEnemyMain() {
        Base enemyMain = gameState.getBaseData().getMainEnemyBase();
        if (enemyMain == null) {
            return true;
        }
        return mayScoutBase(enemyMain);
    }

    /**
     * Whether a ground scout may route to a base: not while a detected Bunker holds the way to it, see
     * {@link BunkerScoutGate#mayRouteToBase}. Opening the route re-arms {@link #recordBunkerSkip}.
     *
     * @param base the base a ground scout would go to
     * @return false while a held Bunker closes the route to it
     */
    public boolean mayScoutBase(Base base) {
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        if (strategyTracker == null) {
            return true;
        }
        TilePosition main = locationOf(gameState.getBaseData().getMainEnemyBase());
        TilePosition natural = locationOf(gameState.getBaseData().getEnemyNaturalBase());
        boolean may = BunkerScoutGate.mayRouteToBase(strategyTracker.isBunkerNaturalHeld(),
                strategyTracker.isBunkerMainHeld(), base.getLocation(), main, natural);
        if (may) {
            bunkerSkipsRecorded.remove(BunkerScoutGate.destination(base.getLocation(), main, natural));
        }
        return may;
    }

    private static TilePosition locationOf(Base base) {
        return base == null ? null : base.getLocation();
    }

    /**
     * Writes a SCOUT_SKIPPED row for a ground scout withheld from, or recalled off, a base by a held Bunker, once
     * per destination until the route opens again.
     */
    private void recordBunkerSkip(Base base) {
        BunkerScoutGate.Destination destination = BunkerScoutGate.destination(base.getLocation(),
                locationOf(gameState.getBaseData().getMainEnemyBase()),
                locationOf(gameState.getBaseData().getEnemyNaturalBase()));
        if (bunkerSkipsRecorded.add(destination)) {
            PlanEvents.scoutSkipped(BunkerScoutGate.skipLabel(destination));
        }
    }

    private boolean isEnemyBaseLocated() {
        return gameState.getBaseData().knowEnemyMainBase()
                || gameState.getScoutData().isEnemyBuildingLocationKnown();
    }

    public int getMaxZerglingScouts() {
        if (gameState.getEnemyBuildings().isEmpty()) {
            return 3;
        }

        return 1;
    }

    public int needZerglingScouts(int currentFrame, int lastEnemySeenFrame) {
        if (gameState.getBaseData().getMainEnemyBase() == null) {
            return 0;
        }

        int framesSinceLastEnemy = currentFrame - lastEnemySeenFrame;
        if (framesSinceLastEnemy < 720 && !mustFindEnemyMain()) {
            return 0;
        }

        int maxScouts = getMaxZerglingScouts();
        int currentScouts = 0;
        Base enemyMain = gameState.getBaseData().getMainEnemyBase();
        for (ManagedUnit scout : zerglingScouts) {
            BaseCheck check = baseChecks.get(scout);
            if (check == null || check.base.equals(enemyMain)) {
                currentScouts++;
            }
        }
        int toSend = BaseCheckScheduler.searchLingsToSend(neverSeen(enemyMain),
                lastDispatchFrames.getOrDefault(enemyMain, -1), currentFrame, Math.max(0, maxScouts - currentScouts));
        if (toSend > 0 && !mayScoutEnemyMain()) {
            recordBunkerSkip(enemyMain);
            return 0;
        }
        return toSend;
    }

    private boolean neverSeen(Base enemyMain) {
        return Config.baseChecks && gameState.getScoutData().getBaseLastSeenFrame(enemyMain.getLocation()) < 0;
    }

    private TilePosition pollDroneScoutTarget(boolean bunkerGated) {
        BaseData baseData = gameState.getBaseData();
        if (baseData.knowEnemyMainBase()) {
            if (bunkerGated && !mayScoutEnemyMain()) {
                recordBunkerSkip(baseData.getMainEnemyBase());
                return null;
            }
            return scoutEnemyMain();
        }
        if (baseData.isEnemyMainBaseFound()) {
            return gameState.pollScoutTarget();
        }
        return findEnemyMain();
    }

    private TilePosition scoutEnemyMain() {
        BaseData baseData = gameState.getBaseData();
        Base enemyMain = baseData.getMainEnemyBase();
        if (enemyMain == null) {
            enemyMainScoutPath = null;
            return gameState.pollScoutTarget();
        }
        TilePosition enemyMainTp = enemyMain.getLocation();
        if (enemyMainScoutPath == null) {
            ensureEnemyMainMovePoints(enemyMainTp);
        }

        return enemyMainScoutPath.next();
    }

    private void ensureEnemyMainMovePoints(TilePosition enemyMainTp) {
        GameMap gameMap = gameState.getGameMap();

        this.enemyMainScoutPath = gameMap.computeScoutPerimeter(enemyMainTp);
    }

    /**
     * Determine best base to scout with drone
     *
     * 3 unknown locations (4P map): Scout diagonal (overlord goes to the closest natural/main)
     * 2 unknown locations (3P map, 4P map with 1 scouted): Scout base that is furthest from overlords
     * 1 unknown location: trivial case
     * @return
     */
    private TilePosition findEnemyMain() {
        BaseData baseData = gameState.getBaseData();
        ScoutData scoutData = gameState.getScoutData();
        Set<Base> baseSet = scoutData.getScoutingBaseSet();

        final int unscountedMainBases = baseSet.size();

        if (unscountedMainBases == 3) {
            final Base farthestBase = baseData.findFarthestStartingBaseByGround();
            updateBaseScoutAssignments(farthestBase);
            return farthestBase.getLocation();
        } else if (unscountedMainBases == 2) {
            final Base farthestBase = fetchBaseFarthestFromScouts(baseSet);
            updateBaseScoutAssignments(farthestBase);
            return farthestBase.getLocation();
        } else {
            final Base farthestBase = fetchBaseFarthestFromScouts(baseSet);
            updateBaseScoutAssignments(farthestBase);
            return farthestBase.getLocation();
        }
    }

    private void updateBaseScoutAssignments(Base base) {
        ScoutData scoutData = gameState.getScoutData();
        int assignments = scoutData.getScoutsAssignedToBase(base);
        scoutData.updateBaseScoutAssignment(base, assignments);
    }

    private Base fetchBaseFarthestFromScouts(Set<Base> mainBases) {
        Map<Base, Double> baseDistance = new HashMap<>();
        mainBases.stream().forEach(b -> baseDistance.put(b, Double.MAX_VALUE));
        for (Base b: mainBases) {
            for (ManagedUnit scout: scouts) {
                final double distance = b.getLocation().getDistance(scout.getUnit().getTilePosition());
                if (distance < baseDistance.get(b)) {
                    baseDistance.put(b, distance);
                }
            }
        }

        Base farthest = null;
        for (Map.Entry<Base, Double> entry: baseDistance.entrySet()) {
            if (farthest == null) {
                farthest = entry.getKey();
                continue;
            }
            if (entry.getValue() > baseDistance.get(farthest)) {
                farthest = entry.getKey();
            }
        }

        return farthest;
    }

    private void assignScoutMovementTarget(ManagedUnit managedUnit) {
        if (managedUnit.getMovementTargetPosition() != null) {
            if (!game.isVisible(managedUnit.getMovementTargetPosition())) {
                return;
            }
            managedUnit.setMovementTargetPosition(null);
        }

        ScoutData scoutData = gameState.getScoutData();
        TilePosition target = null;
        if (managedUnit.getUnitType() == UnitType.Zerg_Drone ||
            managedUnit.getUnitType() == UnitType.Zerg_Zergling) {
            target = this.pollDroneScoutTarget(managedUnit.getUnitType() == UnitType.Zerg_Zergling);
        } else {
            target = gameState.pollScoutTarget();
        }

        if (managedUnit.getUnitType() == UnitType.Zerg_Overlord && isEnemyBaseLocated() && tryPerch(managedUnit)) {
            return;
        }

        if (target != null) {
            scoutData.setActiveScoutTarget(target);
            managedUnit.setMovementTargetPosition(target);
        }
    }
}
