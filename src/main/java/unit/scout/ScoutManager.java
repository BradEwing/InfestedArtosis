package unit.scout;

import bwapi.Game;
import bwapi.TilePosition;
import info.InformationManager;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;

import java.util.HashSet;

public class ScoutManager {
    private Game game;
    private InformationManager informationManager;

    private HashSet<ManagedUnit> scouts = new HashSet<>();

    public ScoutManager(Game game, InformationManager informationManager) {
        this.game = game;
        this.informationManager = informationManager;
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
