package unit.scout;

import bwapi.Game;
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
import telemetry.BaseChecks;
import telemetry.PerchAssignments;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ScoutManager {

    final int FRAME_DRONE_SCOUT = 1440; // 1m
    private  InformationManager informationManager;

    private Game game;
    private GameState gameState;

    private HashSet<ManagedUnit> scouts = new HashSet<>();
    private HashSet<ManagedUnit> droneScouts = new HashSet<>();
    private HashSet<ManagedUnit> zerglingScouts = new HashSet<>();

    private ScoutPath enemyMainScoutPath;

    private List<ManagedUnit> recalledOverlords = new ArrayList<>();

    private final Map<ManagedUnit, BaseCheck> baseChecks = new HashMap<>();
    private final List<ManagedUnit> releasedChecks = new ArrayList<>();
    private final Map<Base, Integer> retryAfterFrames = new HashMap<>();
    private final Map<Base, Integer> consecutiveFailures = new HashMap<>();

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

    public ScoutManager(Game game, GameState gameState, InformationManager informationManager) {
        this.game = game;
        this.gameState = gameState;
        this.informationManager = informationManager;
    }

    public void onFrame() {
        updateBaseChecks();

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
     * Picks the base the next check should go to: the stalest available base not already being checked, once
     * it has gone {@link BaseCheckScheduler#CHECK_INTERVAL_FRAMES} unseen.
     *
     * @return the base to check, or null when none is due
     */
    public Base nextBaseCheck() {
        BaseData baseData = gameState.getBaseData();
        ScoutData scoutData = gameState.getScoutData();
        Set<Base> candidates = baseData.availableBases();
        Map<Base, Integer> lastSeenFrames = new HashMap<>();
        Map<Base, Integer> groundDistances = new HashMap<>();
        for (Base base : candidates) {
            lastSeenFrames.put(base, scoutData.getBaseLastSeenFrame(base.getLocation()));
            GroundPath path = baseData.getBasePaths().get(base);
            if (path != null) {
                groundDistances.put(base, path.getGroundDistance());
            }
        }
        List<Base> excluded = new ArrayList<>();
        for (BaseCheck check : baseChecks.values()) {
            excluded.add(check.base);
        }
        int now = game.getFrameCount();
        for (Map.Entry<Base, Integer> retry : retryAfterFrames.entrySet()) {
            if (retry.getValue() > now) {
                excluded.add(retry.getKey());
            }
        }
        return BaseCheckScheduler.next(candidates, lastSeenFrames, groundDistances, excluded, now);
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
     * @param primary whether this unit's outcome is the check's telemetry row; the second zergling of a pair is
     *     not
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
            Unit unit = scout.getUnit();
            boolean seen = scoutData.getBaseLastSeenFrame(check.base.getLocation()) >= check.dispatchFrame;
            BaseCheckScheduler.Release reason = BaseCheckScheduler.releaseReason(seen, unit.getHitPoints(),
                    unit.getType().maxHitPoints(), check.dispatchFrame, now);
            if (reason == BaseCheckScheduler.Release.NONE && scout.getUnitType() == UnitType.Zerg_Overlord
                    && !routeClear(scout.getPosition(), check.base.getCenter())) {
                reason = BaseCheckScheduler.Release.THREAT;
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
        if (!check.primary) {
            return;
        }
        List<Position> enemyPositions = new ArrayList<>();
        for (Unit enemy : gameState.getVisibleEnemyUnits()) {
            enemyPositions.add(enemy.getPosition());
        }
        BaseChecks.checked(scout.getUnitID(), scout.getUnitType(), check.base.getLocation(), check.ageAtDispatch,
                check.dispatchFrame, game.getFrameCount(), outcome,
                BaseCheckScheduler.isOccupied(enemyPositions, check.base.getCenter()));
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
        if (framesSinceLastEnemy < 720) {
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
        return Math.max(0, maxScouts - currentScouts);
    }

    private TilePosition pollDroneScoutTarget() {
        BaseData baseData = gameState.getBaseData();
        if (baseData.knowEnemyMainBase()) {
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
            target = this.pollDroneScoutTarget();
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
