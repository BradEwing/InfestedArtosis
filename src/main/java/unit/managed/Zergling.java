package unit.managed;

import bwapi.Game;
import bwapi.Unit;
import info.map.GameMap;
import util.Filter;

import java.util.List;
import java.util.stream.Collectors;

public class Zergling extends ManagedUnit {
    public Zergling(Game game, Unit unit, UnitRole role, GameMap gameMap) {
        super(game, unit, role, gameMap);
    }

    @Override
    protected void fight() {
        if (unit.isAttackFrame()) {
            return;
        }
        setUnready(5);

        if (fightTarget != null) {
            int distanceToTarget = unit.getDistance(fightTarget);
            if (distanceToTarget < 64) {
                unit.attack(fightTarget);
                return;
            } 
            movementTargetPosition = fightTarget.getTilePosition();
        }

        if (movementTargetPosition != null) {
            unit.move(movementTargetPosition.toPosition());
            return;
        }

        role = UnitRole.IDLE;
    }

    @Override
    protected List<Unit> getEnemiesInRadius(int currentX, int currentY) {
        return game.getUnitsInRadius(currentX, currentY, 128)
                .stream()
                .filter(u -> u.getPlayer() != game.self())
                .filter(u -> Filter.isGroundThreat(u.getType()))
                .collect(Collectors.toList());
    }
}
