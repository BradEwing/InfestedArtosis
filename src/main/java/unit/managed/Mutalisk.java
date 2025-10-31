package unit.managed;

import bwapi.Game;
import bwapi.Unit;
import info.map.GameMap;
import util.Time;

public class Mutalisk extends ManagedUnit {
    private Time retreatUntilFrame = null;
    private static final int RETREAT_DURATION_FRAMES = 24; // 1 second retreat duration
    
    public Mutalisk(Game game, Unit unit, UnitRole role, GameMap gameMap) {
        super(game, unit, role, gameMap);
    }

    @Override
    protected void fight() {
        if (unit.isAttackFrame()) {
            // Set retreat timer when attack frame is detected
            retreatUntilFrame = new Time(game.getFrameCount() + RETREAT_DURATION_FRAMES);
            return;
        }
        setUnready(4);

        if (retreatUntilFrame != null && game.getFrameCount() < retreatUntilFrame.getFrames()) {
            if (retreatTarget == null) {
                retreatTarget = getRetreatPosition();
            }
            unit.move(retreatTarget);
            return;
        } 

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
