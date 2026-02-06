package unit.managed;

import bwapi.Game;
import bwapi.Unit;
import info.map.GameMap;

public class Zergling extends ManagedUnit {
    private static final int FIGHT_UNREADY_FRAMES = 5;
    private static final int MELEE_ATTACK_RANGE = 64;

    public Zergling(Game game, Unit unit, UnitRole role, GameMap gameMap) {
        super(game, unit, role, gameMap);
    }

    @Override
    protected void fight() {
        if (unit.isAttackFrame()) {
            return;
        }
        setUnready(FIGHT_UNREADY_FRAMES);

        if (fightTarget != null) {
            int distanceToTarget = unit.getDistance(fightTarget);
            if (distanceToTarget < MELEE_ATTACK_RANGE) {
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

}
