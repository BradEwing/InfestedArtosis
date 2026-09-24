package unit.managed;

import bwapi.Game;
import bwapi.Unit;
import info.map.GameMap;
import util.Filter;

import java.util.List;
import java.util.stream.Collectors;

public class Zergling extends ManagedUnit {
    static final int FIGHT_ATTACK_RADIUS = 128;

    public Zergling(Game game, Unit unit, UnitRole role, GameMap gameMap) {
        super(game, unit, role, gameMap);
    }

    /**
     * Attacks the fight target once it is within {@link #FIGHT_ATTACK_RADIUS}, otherwise moves to its tile. Inside
     * that radius the game's attack order, not a move to the shared tile, steers each ling onto the target.
     */
    @Override
    protected void fight() {
        if (unit.isAttackFrame()) {
            return;
        }
        setUnready(5);

        if (fightTarget != null) {
            int distanceToTarget = unit.getDistance(fightTarget);
            if (distanceToTarget < FIGHT_ATTACK_RADIUS) {
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

    /**
     * Moves to the runby destination when one is set, otherwise closes on the fight target and attacks it
     * within 64 pixels. Moving rather than attack-moving keeps the ling from stopping on units it was not
     * told to hit. The role is never changed here, so a ling without an order waits for the next one instead
     * of going idle and being re-homed.
     */
    @Override
    protected void runby() {
        if (unit.isAttackFrame()) {
            return;
        }

        if (runbyDestination != null) {
            setUnready(5);
            unit.move(runbyDestination);
            return;
        }

        if (fightTarget != null) {
            setUnready(5);
            if (unit.getDistance(fightTarget) < 64) {
                unit.attack(fightTarget);
                return;
            }
            unit.move(fightTarget.getPosition());
        }
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
