package unit;

import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.Text;
import bwapi.TilePosition;
import bwapi.Unit;

import bwapi.UnitType;
import bwem.CPPath;
import lombok.Data;
import planner.PlanState;
import planner.PlannedItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static bwta.BWTA.getShortestPath;
import static util.Filter.closestHostileUnit;
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
    private TilePosition movementTargetPosition;

    private List<TilePosition> pathToTarget;
    private TilePosition currentStepToTarget;

    private Unit fightTarget;
    private Unit gatherTarget;
    // PlannedItem this unit is assigned to
    private PlannedItem plannedItem;

    private boolean canFight;
    private boolean isFlyer;

    private int unreadyUntilFrame = 0;
    private boolean isReady = true;

    public ManagedUnit(Game game, Unit unit, UnitRole role) {
        this.game = game;
        this.unit = unit;
        this.role = role;

        if (unit.getType() == UnitType.Zerg_Overlord) {
            this.canFight = false;
            this.isFlyer = true;
        } else {
            this.canFight = true;
            this.isFlyer = false;
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

        // TODO: Determine control flow here

        // For now, we MOVE if our current target is more than 100 away and it exists.
        // That number will certainly have to be tweaked!
        /*
        if (!isFlyer() && movementTargetPosition != null && unit.getDistance(movementTargetPosition.toPosition()) > 300) {
            move();
            return;
        }
         */

        // If we're close to our unit's target, execute action for role
        switch (role) {
            case SCOUT:
                scout();
                break;
            case FIGHT:
                fight();
                break;
            case GATHER:
                gather();
                break;
            case BUILD:
                build();
                break;
            case MORPH:
                morph();
                break;
            default:
                break;
        }
    }

    private void debugScout() {
        Position unitPosition = unit.getPosition();
        game.drawLineMap(unitPosition, movementTargetPosition.toPosition(), Color.White);
    }

    private void debugFight() {
        Position unitPosition = unit.getPosition();
        if (fightTarget != null) {
            game.drawLineMap(unitPosition, fightTarget.getPosition(), Color.Red);
        }
        if (movementTargetPosition != null) {
            game.drawLineMap(unitPosition, movementTargetPosition.toPosition(), Color.Orange);
        }
    }

    private void debugRole() {
        Position unitPosition = unit.getPosition();
        game.drawTextMap(unitPosition, String.format("%s", role), Text.Default);
    }

    private void debugPathToTarget() {
        for (int i = 0; i < pathToTarget.size() - 1; i++) {
            game.drawLineMap(pathToTarget.get(i).toPosition(), pathToTarget.get(i+1).toPosition(), Color.White);
        }
    }

    private void debugMove() {
        game.drawLineMap(unit.getPosition(), currentStepToTarget.toPosition(), Color.Grey);
    }

    private void move() {
        TilePosition currentPosition = unit.getTilePosition();
        // If we have a movement step target and we're far away, we do nothing
        if (currentStepToTarget != null && currentPosition.getDistance(currentStepToTarget) > 50) {
            return;
        }
        // Check to see if we have a path
        // If we don't see if can naive move to target
        if (pathToTarget == null || pathToTarget.size() < 1) {
            if (movementTargetPosition != null) {
                pathToTarget = null;
                unit.move(movementTargetPosition.toPosition());
            }
            return;
        }

        // Check to see if we're close to current step, or if we need to initialize one

        if (currentStepToTarget == null || currentPosition.getDistance(currentStepToTarget) <= 50) {
            currentStepToTarget = pathToTarget.remove(0);
        }

        debugMove();
        unit.move(currentStepToTarget.toPosition());
    }

    private void gather() {
        if (unit.isGatheringMinerals() || unit.isGatheringGas()) return;
        if (!isReady) return;

        if ((unit.isCarryingGas() || unit.isCarryingMinerals()) && unit.isIdle()) {
            setUnready();
            unit.returnCargo();
            return;
        }

        setUnready();
        unit.gather(gatherTarget);
    }

    // Attempt build or morph
    private void build() {
        if (unit.isBeingConstructed() || unit.isMorphing()) return;
        if (!isReady) return;

        UnitType plannedUnitType = plannedItem.getPlannedUnit();

        if (plannedItem.getBuildPosition() == null) {
            TilePosition buildLocation = game.getBuildLocation(plannedUnitType, unit.getTilePosition());
            plannedItem.setBuildPosition(buildLocation);
        }

        Position buildTarget = plannedItem.getBuildPosition().toPosition();
        if (unit.getDistance(buildTarget) > 200 && (!unit.isMoving() || unit.isGatheringMinerals())) {
            setUnready();
            unit.move(buildTarget);
            return;
        }

        if (game.canMake(plannedUnitType, unit)) {
            // Try to build
            // TODO: Maybe only check the units we assigned to build, after so many frames we can try to reassign
            //   - Maybe the PlannedItems are tracked in a higher level state, and their status is updated at the higher level
            //   - A PlannedItem marked as complete would then be removed from the bot (careful consideration to be sure it's removed everywhere)
            setUnready();
            boolean didBuild = unit.build(plannedUnitType, buildTarget.toTilePosition());
            // If we failed to build, try to morph
            if (!didBuild) {
                didBuild = unit.morph(plannedUnitType);
            }

            if (!didBuild) {
                // Try to get a new building location
                plannedItem.setBuildPosition(game.getBuildLocation(plannedUnitType, unit.getTilePosition()));
            }

            if (didBuild) {
                plannedItem.setState(PlanState.MORPHING);
            }
        }
    }

    private void morph() {
        if (!isReady) return;
        if (unit.isMorphing()) return;

        final UnitType unitType = plannedItem.getPlannedUnit();
        if (game.canMake(unitType, unit)) {
            setUnready();
            boolean didMorph = unit.morph(unitType);
            if (didMorph) {
                plannedItem.setState(PlanState.MORPHING);
            }

        }
    }

    private void setUnready() {
        isReady = false;
        unreadyUntilFrame = game.getFrameCount() + game.getLatencyFrames() + 11;
        return;
    }

    private void scout() {
        // Need to reassign movementTarget
        if (movementTargetPosition == null) {
            return;
        }

        debugScout();
        if (!isReady) {
            return;
        }

        if (game.isVisible(movementTargetPosition)) {
            role = UnitRole.IDLE;
            movementTargetPosition = null;
            return;
        }
        // TODO: micro / avoid enemies
        setUnready();
        unit.move(movementTargetPosition.toPosition());
    }

    // TODO: squads, gather before fighting
    // We keep flip flopping states when we have 0 visibility!
    // TODO: handle visibility
    private void fight() {
        debugFight();
        if (!isReady) {
            return;
        }
        if (unit.isAttackFrame()) {
            return;
        }
        setUnready();
        // Our fight target is no longer visible, drop in favor of fight target position
        if (fightTarget != null && fightTarget.getType() == UnitType.Unknown) {
            fightTarget = null;
        }
        if (fightTarget != null) {
            unit.attack(fightTarget);
            return;
        }

        if (movementTargetPosition != null && game.isVisible(movementTargetPosition)) {
            movementTargetPosition = null;
        }
        // TODO: determine if Units may get stuck infitely trying to approach fightTargetPosition
        if (movementTargetPosition != null) {
            unit.move(movementTargetPosition.toPosition());
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
            //System.out.printf("Invalid enemies passed to assignClosestEnemyAsFightTarget(), enemies: [%s]\n", enemies);
        }

        List<Unit> filtered = new ArrayList<>();

        for (Unit enemyUnit: enemies) {
            if (unit.canAttack(enemyUnit)) {
                filtered.add(enemyUnit);
            }
        }

        Unit closestEnemy = closestHostileUnit(unit, filtered);

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
        movementTargetPosition = closestEnemy.getTilePosition();
    }

    public void assignGather(Unit gatherTarget) {

    }

    public void assignBuilder(PlannedItem buildingPlan) {

    }

    // TODO: Refactor into debug role
    private void debugBuildingAssignments() {

        UnitType building = plannedItem.getPlannedUnit();
        Position unitPosition = unit.getPosition();
        game.drawTextMap(unitPosition, "BUILDER: " + building.toString(), Text.White);

    }
}
