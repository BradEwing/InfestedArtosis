package unit.managed;

import bwapi.Game;
import bwapi.Unit;
import info.map.GameMap;
import util.Time;

public class Mutalisk extends ManagedUnit {
    private Time retreatUntilFrame = null;
    private static final int RETREAT_DURATION_FRAMES = 30;
    
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
            if (retreatTarget != null) {
                unit.move(retreatTarget);
            } else if (rallyPoint != null) {
                unit.move(rallyPoint);
            }
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

    /**
     * Moves to the harass destination when one is set, otherwise attacks the fight target. The role is never
     * changed here, so a Mutalisk without an order waits for the next one instead of going idle and being re-homed.
     */
    @Override
    protected void harass() {
        if (unit.isAttackFrame()) {
            return;
        }

        if (harassDestination != null) {
            setUnready(4);
            unit.move(harassDestination);
            return;
        }

        if (fightTarget != null) {
            setUnready(4);
            unit.attack(fightTarget);
        }
    }

    @Override
    protected int retreatScanRadius() {
        return 256;
    }

    @Override
    protected int retreatFleeDistance() {
        return 256;
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
