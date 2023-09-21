package unit.managed;

import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.Text;
import bwapi.TilePosition;
import bwapi.Unit;

import bwapi.UnitType;
import planner.PlanState;
import planner.Plan;

import java.util.ArrayList;
import java.util.List;

import static util.Filter.closestHostileUnit;

public class ManagedUnit {
    private static int LOCK_ENEMY_WITHIN_DISTANCE = 25;
    private Game game;

    private final int unitID; // debug
    private Unit unit;
    private UnitRole role;
    private UnitType unitType;


    // TODO: Consolidate/refactor
    private TilePosition rallyPoint;
    private TilePosition movementTargetPosition;
    private List<TilePosition> pathToTarget;
    private TilePosition retreatTarget;

    private Unit defendTarget;
    private Unit fightTarget;
    private Unit gatherTarget;

    private boolean hasNewGatherTarget;

    // Plan this unit is assigned to
    private Plan plan;
    private int buildAttemptFrame;

    private boolean canFight;

    private int unreadyUntilFrame = 0;
    private boolean isReady = true;

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

    @Override
    public int hashCode() {
        return this.unitID;
    }

    public int getUnitID() { return this.unitID; }

    public void setPlan(Plan plan) {
        this.plan = plan;
    }

    public void setRallyPoint(TilePosition tilePosition) { this.rallyPoint = tilePosition; }

    public Unit getUnit() { return this.unit; }

    public UnitRole getRole() { return this.role; }

    public void setRole(UnitRole role) { this.role = role; }

    public void setRetreatTarget(TilePosition tp) { this.retreatTarget = tp; }

    public Unit getDefendTarget() { return this.defendTarget; }
    public void setDefendTarget(Unit unit) { this.defendTarget = unit; }

    public boolean isReady() { return this.isReady; }

    public void setReady(boolean isReady) { this.isReady = isReady; }

    public int getUnreadyUntilFrame() { return this.unreadyUntilFrame; }

    public boolean canFight() { return this.canFight; }

    public void setCanFight(boolean canFight) { this.canFight = canFight; }

    public void setGatherTarget(Unit unit) { this.gatherTarget = unit; }

    public void hasNewGatherTarget(boolean hasNewGatherTarget) { this.hasNewGatherTarget = hasNewGatherTarget; }

    public TilePosition getMovementTargetPosition() { return this.movementTargetPosition; }
    public void setMovementTargetPosition(TilePosition tp) { movementTargetPosition = tp; }

    public UnitType getUnitType() { return this.unitType; }

    public void setUnitType(UnitType unitType) { this.unitType = unitType; }

    public Plan getPlan() { return this.plan; }

    public void execute() {
        debugRole();

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
            case RETREAT:
                retreat();
                break;
            case DEFEND:
                defend();
                break;
            case RALLY:
                rally();
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
            game.drawLineMap(unitPosition, movementTargetPosition.toPosition(), Color.White);
        }
    }

    private void debugBuild() {
        Position unitPosition = unit.getPosition();
        Position buildPosition = plan.getBuildPosition().toPosition();
        game.drawLineMap(unitPosition, buildPosition, Color.Cyan);
        game.drawTextMap(unitPosition.add(new Position(8,8)), String.format("Distance: %d", unit.getDistance(buildPosition)), Text.Cyan);
    }

    private void debugRole() {
        if (role == UnitRole.BUILDING) {
            return;
        }
        Position unitPosition = unit.getPosition();
        game.drawTextMap(unitPosition, String.format("%s", role), Text.Default);
    }

    private void rally() {
        if (!isReady) return;
        if (rallyPoint == null) return;

        if (unit.getDistance(rallyPoint.toPosition()) < 64) {
            return;
        }

        setUnready();
        unit.move(rallyPoint.toPosition());
    }

    private void gather() {
        if (!hasNewGatherTarget & (unit.isGatheringMinerals() || unit.isGatheringGas())) return;
        if (!isReady) return;

        if ((unit.isCarryingGas() || unit.isCarryingMinerals()) && unit.isIdle()) {
            setUnready();
            unit.returnCargo();
            return;
        }

        if (gatherTarget == null) {
            role = UnitRole.IDLE;
            return;
        }

        setUnready();
        unit.gather(gatherTarget);
        hasNewGatherTarget = false;
    }

    // Attempt build or morph
    private void build() {
        if (unit.isBeingConstructed() || unit.isMorphing()) return;
        if (plan.getBuildPosition() != null) {
            // NOTE: this assumes plan and buildPosition are NOT NULL
            debugBuild();
        }
        if (!isReady) return;

        UnitType plannedUnitType = plan.getPlannedUnit();

        // TODO: This should be determined with a building location planner
        // Should be assigned to the plan before the plan is assigned to the unit
        if (plan.getBuildPosition() == null) {
            //System.out.printf("plan buildPosition is null: [%s]\n", plan);
            TilePosition buildLocation = game.getBuildLocation(plannedUnitType, unit.getTilePosition());
            plan.setBuildPosition(buildLocation);
        }

        Position buildTarget = plan.getBuildPosition().toPosition().add(new Position(4,4));
        if (unit.getDistance(buildTarget) > 150 || (!unit.isMoving() || unit.isGatheringMinerals())) {
            setUnready();
            unit.move(buildTarget);
            return;
        }

        if (game.canMake(plannedUnitType, unit)) {
            // Try to build
            // TODO: Maybe only check the units we assigned to build, after so many frames we can try to reassign
            //   - Maybe the PlannedItems are tracked in a higher level state, and their status is updated at the higher level
            //   - A Plan marked as complete would then be removed from the bot (careful consideration to be sure it's removed everywhere)
            setUnready();
            boolean didBuild = unit.build(plannedUnitType, buildTarget.toTilePosition());
            // If we failed to build, try to morph
            // TODO: remove this morph cmd?
            if (!didBuild) {
                didBuild = unit.morph(plannedUnitType);
            }

            final int frameCount = game.getFrameCount();

            if (!didBuild && buildAttemptFrame == 0) {
                buildAttemptFrame = frameCount;
            }


            if (!didBuild) {
                // There is a race condition where didBuild returns false and a new building location is assigned while
                // the drone initiates the actual build. If the drone does not start the build animation in time, it's reassigned
                // and can get stuck bouncing around looking for a build location.
                //
                // This is a bit hacky but buys 150 frames to attempt to build at the given location before reassigning elsewhere
                if (buildAttemptFrame + 150 < frameCount) {
                    // Try to get a new building location
                    //System.out.printf("failed to build, getting new building location: [%s]\n", plan);
                    plan.setBuildPosition(game.getBuildLocation(plannedUnitType, unit.getTilePosition()));
                }

            }

            if (didBuild) {
                plan.setState(PlanState.MORPHING);
            }
        }
    }

    private void morph() {
        if (!isReady) return;
        if (unit.isMorphing()) return;

        final UnitType unitType = plan.getPlannedUnit();
        if (game.canMake(unitType, unit)) {
            setUnready();
            boolean didMorph = unit.morph(unitType);
            if (didMorph) {
                plan.setState(PlanState.MORPHING);
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
            //role = UnitRole.IDLE;
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
        if (fightTarget != null && (fightTarget.getType() == UnitType.Unknown || !fightTarget.isTargetable())) {
            fightTarget = null;
        }
        if (fightTarget != null) {
            unit.attack(fightTarget);
            return;
        }

        if (movementTargetPosition != null && game.isVisible(movementTargetPosition)) {
            movementTargetPosition = null;
        }
        // TODO: determine if Units may get stuck infinitely trying to approach fightTargetPosition
        if (movementTargetPosition != null) {
            unit.move(movementTargetPosition.toPosition());
            return;
        }

        //System.out.printf("FightTarget is null, frame: [%s], unitType: [%s]\n", game.getFrameCount(), unit.getType());
        role = UnitRole.IDLE;
        return;
    }

    private void retreat() {
        if (!isReady) {
            return;
        }
        if (retreatTarget == null) {
            role = UnitRole.IDLE;
            return;
        }

        setUnready();

        if (unit.getDistance(retreatTarget.toPosition()) < 250 || unit.isIdle()) {
            role = UnitRole.IDLE;
            return;
        }

        unit.move(retreatTarget.toPosition());
        return;
    }

    private void defend() {
        if (!isReady) {
            return;
        }
        if (unit.isAttackFrame()) {
            return;
        }
        setUnready();
        // Our fight target is no longer visible, drop in favor of fight target position
        if (defendTarget != null && defendTarget.getType() == UnitType.Unknown) {
            defendTarget = null;
        }
        if (defendTarget != null) {
            unit.attack(defendTarget);
            return;
        }
    }

    // TODO: This is potentially expensive?
    // Could potentially run this every so many frames, with a backoff
    // Maybe reassignment can become state based
    // n fighters * m enemies
    // worst case (200 * 200) = 40000 computations per frame
    public void assignClosestEnemyAsFightTarget(List<Unit> enemies, TilePosition backupScoutPosition) {
        // We bail out if we're close enough to the unit to avoid deadlocking on weird micro situations
        // Don't bail if it's a building though
        if (fightTarget != null && !fightTarget.getType().isBuilding() && fightTarget.getDistance(unit) < LOCK_ENEMY_WITHIN_DISTANCE) {
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
