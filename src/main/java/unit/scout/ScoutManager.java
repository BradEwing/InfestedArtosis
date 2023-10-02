package unit.scout;

import bwapi.Game;
import bwapi.Race;
import bwapi.TilePosition;
import bwapi.UnitType;
import bwem.Base;
import info.BaseData;
import info.GameState;
import info.InformationManager;
import info.map.GameMap;
import info.map.MapTile;
import strategy.openers.OpenerName;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;

import java.util.ArrayList;
import java.util.HashSet;

public class ScoutManager {

    final int FRAME_DRONE_SCOUT = 1440;

    private Game game;
    private InformationManager informationManager;
    private GameState gameState;

    private HashSet<ManagedUnit> scouts = new HashSet<>();
    private HashSet<ManagedUnit> droneScouts = new HashSet<>();

    public ScoutManager(Game game, InformationManager informationManager, GameState gameState) {
        this.game = game;
        this.informationManager = informationManager;
        this.gameState = gameState;
    }

    public void onFrame() {
        for (ManagedUnit managedUnit: scouts) {
            if (managedUnit.getMovementTargetPosition() == null) {
                assignScoutMovementTarget(managedUnit);
            }
        }
    }

    public void addScout(ManagedUnit managedUnit) {
        managedUnit.setRole(UnitRole.SCOUT);
        assignScoutMovementTarget(managedUnit);
        scouts.add(managedUnit);
        if (managedUnit.getUnitType() == UnitType.Zerg_Drone) {
            droneScouts.add(managedUnit);
        }
    }

    public void removeScout(ManagedUnit managedUnit) {
        if (managedUnit == null) {
            return;
        }

        TilePosition movementTarget = managedUnit.getMovementTargetPosition();
        HashSet<TilePosition> activeScoutTargets =  informationManager.getActiveScoutTargets();
        if (movementTarget != null && activeScoutTargets.contains(managedUnit.getMovementTargetPosition())) {
            activeScoutTargets.remove(movementTarget);
        }
        scouts.remove(managedUnit);
        droneScouts.remove(managedUnit);
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
        if (gameState.getActiveOpener().getName() == OpenerName.FOUR_POOL) {
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

    private void assignScoutMovementTarget(ManagedUnit managedUnit) {
        if (managedUnit.getMovementTargetPosition() != null) {
            if (!game.isVisible(managedUnit.getMovementTargetPosition())) {
                return;
            }
            managedUnit.setMovementTargetPosition(null);
        }

        TilePosition target = informationManager.pollScoutTarget(false);
        informationManager.setActiveScoutTarget(target);
        managedUnit.setMovementTargetPosition(target);
    }
}
