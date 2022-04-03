package unit;

import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.Text;
import bwapi.TilePosition;
import bwapi.Unit;

import bwapi.UnitType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

import static util.Filter.closestUnit;

// TODO: Rename to agent / agent manager?
@Data
public class ManagedUnit {
    private static int LOCK_ENEMY_WITHIN_DISTANCE = 25;
    private Game game;

    private int unitID; // debug
    private Unit unit;
    private UnitRole role;
    private UnitType unitType;
    private TilePosition movementTarget;

    private Unit fightTarget;
    private TilePosition fightTargetPosition;

    private boolean canFight;

    public ManagedUnit(Game game, Unit unit, UnitRole role) {
        this.game = game;
        this.unit = unit;
        this.role = role;

        if (unit.getType() == UnitType.Zerg_Overlord) {
            this.canFight = false;
        } else {
            this.canFight = true;
        }
        this.unitType = unit.getType();
        this.unitID = unit.getID();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof ManagedUnit)) {
            return false;
        }

        ManagedUnit u = (ManagedUnit) o;

        return unitID == u.getUnitID();
    }

    public void execute() {
        debugRole();
        switch (role) {
            case SCOUT:
                scout();
                break;
            case FIGHT:
                fight();
                break;
            default:
                break;
        }
    }

    private void debugScout() {
        Position unitPosition = unit.getPosition();
        game.drawLineMap(unitPosition, movementTarget.toPosition(), Color.White);
    }

    private void debugFight() {
        Position unitPosition = unit.getPosition();
        if (fightTarget != null) {
            game.drawLineMap(unitPosition, fightTarget.getPosition(), Color.Red);
        }
        if (fightTargetPosition != null) {
            game.drawLineMap(unitPosition, fightTargetPosition.toPosition(), Color.Orange);
        }
    }

    private void debugRole() {
        Position unitPosition = unit.getPosition();
        game.drawTextMap(unitPosition, String.format("%s", role), Text.Default);
    }

    // TODO: fix scout drawing on map
    private void scout() {
        // Need to reassign movementTarget
        if (movementTarget == null) {
            return;
        }

        debugScout();

        if (game.isVisible(movementTarget)) {
            role = UnitRole.IDLE;
            movementTarget = null;
            return;
        }
        // TODO: micro / avoid enemies
        unit.move(movementTarget.toPosition());
    }

    // TODO: squads, gather before fighting
    // We keep flip flopping states when we have 0 visibility!
    // TODO: handle visibility
    private void fight() {
        debugFight();
        // Our fight target is no longer visible, drop in favor of fight target position
        if (fightTarget != null && fightTarget.getType() == UnitType.Unknown) {
            fightTarget = null;
        }
        if (fightTarget != null) {
            unit.attack(fightTarget);
            return;
        }

        if (game.isVisible(fightTargetPosition)) {
            fightTargetPosition = null;
        }
        // TODO: determine if Units may get stuck infitely trying to approach fightTargetPosition
        if (fightTargetPosition != null) {
            return;
        }

        //System.out.printf("FightTarget is null, frame: [%s], unitType: [%s]\n", game.getFrameCount(), unit.getType());
        role = UnitRole.IDLE;
        return;
    }

    // TODO: This is potentially expensive?
    // Could potentially run this every so many frames, with a backoff
    // Maybe reassignment can become state based
    // n fighters * m enemies
    // worst case (200 * 200) = 40000 computations per frame
    // TODO: Break out ManagedUnit from ManagedUnit
    //  - TODO: Better name for this relationship
    //  - Managed units can have micro/behavior per unit
    //  - For example, zerglings should not target overlords!
    public void assignClosestEnemyAsFightTarget(List<Unit> enemies) {
        // We bail out if we're close enough to the unit to avoid deadlocking on weird micro situations
        // Don't bail if it's a building though
        if (fightTarget != null && !fightTarget.getType().isBuilding() && fightTarget.getDistance(unit) < LOCK_ENEMY_WITHIN_DISTANCE) {
            return;
        }

        if (enemies.size() < 1) {
            System.out.printf("Invalid enemies passed to assignClosestEnemyAsFightTarget(), enemies: [%s]\n", enemies);
        }

        List<Unit> filtered = new ArrayList<>();
        if (unit.getType() == UnitType.Zerg_Zergling) {
            for (Unit enemyUnit: enemies) {
                if (unit.canAttack(enemyUnit)) {
                    filtered.add(enemyUnit);
                } else if (!enemyUnit.isVisible() && enemyUnit.getType().isBuilding()) {
                    filtered.add(enemyUnit);
                }
            }
        } else {
            filtered = enemies;
        }
        Unit closestEnemy = closestUnit(unit, filtered);

        // TODO: Ensure that this can never be null
        // Somehow the closestUnit is null, bail out and assign to scout
        if (closestEnemy == null) {
            //System.out.printf("Closest enemies is null, enemies: [%s], filtered: [", enemies);
            //for (Unit enemy: enemies) {
                //System.out.printf("%s, ", enemy.getType());
            //}
            //System.out.printf("]\n", enemies);

            role = UnitRole.SCOUT;
            return;
        }
        fightTarget = closestEnemy;
        fightTargetPosition = closestEnemy.getTilePosition();
    }
}
