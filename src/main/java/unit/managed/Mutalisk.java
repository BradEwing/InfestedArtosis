package unit.managed;

import bwapi.Game;
import bwapi.TilePosition;
import bwapi.Unit;

import java.util.ArrayList;
import java.util.List;

import static util.Filter.closestHostileUnit;

public class Mutalisk extends ManagedUnit {
    public Mutalisk(Game game, Unit unit, UnitRole role) {
        super(game, unit, role);
    }

    @Override
    public void assignClosestEnemyAsFightTarget(List<Unit> enemies, TilePosition backupScoutPosition) {
        Unit unit = this.getUnit();
        Unit fightTarget = this.getFightTarget();
        if (this.isLockedToTarget()) {
            return;
        }

        List<Unit> filtered = new ArrayList<>();

        // Attempt to find the closest enemy OUTSIDE fog of war
        for (Unit enemyUnit: enemies) {
            if (unit.canAttack(enemyUnit) && enemyUnit.isDetected()) {
                filtered.add(enemyUnit);
            }
        }

        if (filtered.size() > 0) {
            Unit closestEnemy = closestHostileUnit(unit, filtered);
            fightTarget = closestEnemy;
            movementTargetPosition = closestEnemy.getTilePosition();
            return;
        }


        movementTargetPosition = backupScoutPosition;
    }
}
