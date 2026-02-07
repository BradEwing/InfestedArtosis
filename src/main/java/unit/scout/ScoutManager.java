package unit.scout;

import bwapi.Game;
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
import info.map.ScoutPath;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

    public ScoutManager(Game game, GameState gameState, InformationManager informationManager) {
        this.game = game;
        this.gameState = gameState;
        this.informationManager = informationManager;
    }

    public void onFrame() {
        for (ManagedUnit managedUnit: scouts) {
            if (managedUnit.getMovementTargetPosition() == null) {
                assignScoutMovementTarget(managedUnit);
            }
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
        ScoutData scoutData = gameState.getScoutData();

        TilePosition movementTarget = managedUnit.getMovementTargetPosition();
        HashSet<TilePosition> activeScoutTargets =  scoutData.getActiveScoutTargets();
        if (movementTarget != null && activeScoutTargets.contains(managedUnit.getMovementTargetPosition())) {
            activeScoutTargets.remove(movementTarget);
        }
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
        if (gameState.getBaseData().getMainEnemyBase() != null) {
            return true;
        }

        for (ManagedUnit managedUnit: droneScouts) {
            Unit unit = managedUnit.getUnit();
            if (unit.isUnderAttack()) {
                return true;
            }
        }

        return false;
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
        if (!baseData.knowEnemyMainBase()) {
            return findEnemyMain();
        }

        return scoutEnemyMain();
    }

    private TilePosition scoutEnemyMain() {
        BaseData baseData = gameState.getBaseData();
        Base enemyMain = baseData.getMainEnemyBase();
        TilePosition enemyMainTp = enemyMain.getLocation();
        if (enemyMainScoutPath == null) {
            ensureEnemyMainMovePoints(enemyMainTp);
        }

        return enemyMainScoutPath.next();
    }

    private void ensureEnemyMainMovePoints(TilePosition enemyMainTp) {
        GameMap gameMap = gameState.getGameMap();

        this.enemyMainScoutPath = gameMap.findScoutPath(enemyMainTp);
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
            final Base fathestBase = new ArrayList<>(baseSet)
                    .get(0);
            final Base farthestBase = fetchBaseFarthestFromScouts(baseSet);
            updateBaseScoutAssignments(farthestBase);
            return fathestBase.getLocation();
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
        if (managedUnit.getUnitType() == UnitType.Zerg_Drone) {
            target = this.pollDroneScoutTarget();
        } else {
            target = informationManager.pollScoutTarget(false);
        }
        
        if (target != null) {
            scoutData.setActiveScoutTarget(target);
            managedUnit.setMovementTargetPosition(target);
        }
    }
}
