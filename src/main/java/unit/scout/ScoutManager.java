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
import info.map.MapTile;
import info.map.ScoutPath;
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

    public ScoutManager(Game game, GameState gameState, InformationManager informationManager) {
        this.game = game;
        this.gameState = gameState;
        this.informationManager = informationManager;
    }

    public void onFrame() {
        for (ManagedUnit managedUnit: scouts) {
            if (managedUnit.getRole() == UnitRole.PERCH) {
                if (isPerchThreatened(managedUnit)) {
                    recalledOverlords.add(managedUnit);
                }
                continue;
            }

            if (managedUnit.getUnitType() == UnitType.Zerg_Overlord && isEnemyBaseLocated()) {
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
     * Drains and returns the overlords recalled off their perch this frame because an enemy
     * unit came within threatening range.
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
     * Attempts to move an overlord scout to a perch watching the best-known enemy location.
     *
     * @param overlord the overlord to perch
     * @return true if a perch was found and assigned
     */
    public boolean tryPerch(ManagedUnit overlord) {
        Position watchTarget = perchWatchTarget(overlord);
        if (watchTarget == null) {
            return false;
        }

        GameMap gameMap = gameState.getGameMap();
        MapTile perch = gameMap.findPerchNear(watchTarget);
        if (perch == null) {
            return false;
        }

        releaseActiveScoutTarget(overlord);
        overlord.setPerchPosition(perch.getTile().toPosition().add(new Position(16, 16)));
        overlord.setMovementTargetPosition(null);
        overlord.setRole(UnitRole.PERCH);
        return true;
    }

    private Position perchWatchTarget(ManagedUnit overlord) {
        BaseData baseData = gameState.getBaseData();
        if (baseData.knowEnemyMainBase()) {
            return baseData.getMainEnemyBase().getLocation().toPosition();
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
    public boolean endZerglingScout() {
        for (ManagedUnit managedUnit: zerglingScouts) {
            Unit unit = managedUnit.getUnit();
            if (unit.getHitPoints() < unit.getType().maxHitPoints() * 0.5) {
                return true;
            }
        }

        if (isEnemyBaseLocated() && zerglingScouts.size() >= this.getMaxZerglingScouts()) {
            return true;
        }

        return false;
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
        int currentScouts = zerglingScouts.size();
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
