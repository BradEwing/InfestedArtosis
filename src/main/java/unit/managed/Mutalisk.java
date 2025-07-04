package unit.managed;

import bwapi.Game;
import bwapi.Unit;

public class Mutalisk extends ManagedUnit {
    public Mutalisk(Game game, Unit unit, UnitRole role) {
        super(game, unit, role);
    }

    @Override
    protected void fight() {
        if (unit.isAttackFrame()) {
            return;
        }
        setUnready(11);

        if (fightTarget != null) {
            unit.attack(fightTarget);
            return;
        }

        if (movementTargetPosition != null) {
            unit.move(movementTargetPosition.toPosition());
            return;
        }

        role = UnitRole.IDLE;
    }

    @Override
    protected void rally() {
        if (rallyPoint == null) return;

        if (role == UnitRole.RALLY) {
            if (unit.getDistance(rallyPoint) < 16) {
                return;
            }
        }

        if (unit.getDistance(rallyPoint) < 4) {
            return;
        }

        setUnready();
        unit.move(rallyPoint);
    }
}
