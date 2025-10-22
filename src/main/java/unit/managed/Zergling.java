package unit.managed;

import bwapi.Game;
import bwapi.Unit;

public class Zergling extends ManagedUnit {
    public Zergling(Game game, Unit unit, UnitRole role) {
        super(game, unit, role);
    }

    @Override
    protected void fight() {
        if (unit.isAttackFrame()) {
            return;
        }
        setUnready(11);

        if (fightTarget != null) {
            if (unit.getDistance(fightTarget) < 16) {
                unit.attack(fightTarget);
                return;
            }
            unit.attack(fightTarget.getTargetPosition());
            return;
        }

        if (movementTargetPosition != null) {
            unit.move(movementTargetPosition.toPosition());
            return;
        }

        role = UnitRole.IDLE;
    }

}
