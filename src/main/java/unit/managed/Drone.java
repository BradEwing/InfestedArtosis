package unit.managed;

import bwapi.Game;
import bwapi.Unit;
import info.map.GameMap;
import util.Filter;

import java.util.List;
import java.util.stream.Collectors;

public class Drone extends ManagedUnit {

    public Drone(Game game, Unit unit, UnitRole role, GameMap gameMap) {
        super(game, unit, role, gameMap);
    }

    @Override
    protected void gather() {
        if (!this.hasNewGatherTarget & (unit.isGatheringMinerals() || unit.isGatheringGas())) return;
        if (!isReady) return;

        if (unit.isCarrying()) {
            setUnready();
            unit.returnCargo();
            return;
        }

        if (gatherTarget == null) {
            role = UnitRole.IDLE;
            return;
        }

        setUnready();
        boolean didGather = unit.gather(gatherTarget);
        if (didGather) {
            hasNewGatherTarget = false;
        }
    }

    @Override
    protected void defend() {
        if (!isReady) {
            return;
        }
        if (unit.isAttackFrame()) {
            return;
        }
        setUnready();

        if (defendTarget != null && defendTarget.isVisible()) {
            unit.attack(defendTarget.getPosition());
            return;
        }
        if (movementTargetPosition != null) {
            unit.move(movementTargetPosition.toPosition());
            return;
        }
        if (defendTarget != null) {
            unit.attack(defendTarget.getPosition());
        }
    }

    @Override
    protected List<Unit> getEnemiesInRadius(int currentX, int currentY) {
        return game.getUnitsInRadius(currentX, currentY, 128)
                .stream()
                .filter(u -> u.getPlayer() != game.self())
                .filter(u -> Filter.isGroundThreat(u.getType()))
                .filter(u -> !Filter.isWorkerType(u.getType()) || u.isAttacking())
                .collect(Collectors.toList());
    }
}
