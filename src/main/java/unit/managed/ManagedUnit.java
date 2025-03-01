package unit.managed;

import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.Text;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WeaponType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import planner.Plan;
import planner.PlanState;

import java.util.List;

public class ManagedUnit {
    protected static int LOCK_ENEMY_WITHIN_DISTANCE = 25;
    protected Game game;

    @Getter
    protected final int unitID; // debug
    @Getter
    protected Unit unit;
    @Setter(AccessLevel.PUBLIC) @Getter(AccessLevel.PUBLIC)
    protected UnitRole role;
    @Setter @Getter
    protected UnitType unitType;


    @Setter
    protected TilePosition rallyPoint;
    @Setter @Getter
    protected TilePosition movementTargetPosition;
    protected List<TilePosition> pathToTarget;
    @Setter
    protected TilePosition retreatTarget;

    @Setter @Getter
    protected Unit defendTarget;
    protected Unit fightTarget;
    @Setter
    protected Unit gatherTarget;

    protected boolean hasNewGatherTarget;

    @Setter @Getter
    protected Plan plan;
    protected int buildAttemptFrame;

    @Setter
    protected boolean canFight = true;

    @Getter
    protected int unreadyUntilFrame = 0;
    protected boolean isReady = true;

    public ManagedUnit(Game game, Unit unit, UnitRole role) {
        this.game = game;
        this.unit = unit;
        this.role = role;

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

    public boolean isReady() { return this.isReady; }

    public void setReady(boolean isReady) { this.isReady = isReady; }

    public boolean canFight() { return this.canFight; }

    public void setNewGatherTarget(boolean hasNewGatherTarget) { this.hasNewGatherTarget = hasNewGatherTarget; }

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
            case REGROUP:
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

    protected void rally() {
        if (!isReady) return;
        if (rallyPoint == null) return;

        if (role == UnitRole.RALLY) {
            if (unit.getDistance(rallyPoint.toPosition()) < 32) {
                return;
            }
        }

        if (unit.getDistance(rallyPoint.toPosition()) < 16) {
            return;
        }

        setUnready();
        unit.move(rallyPoint.toPosition());
    }

    protected void gather() {}

    // Attempt build or morph
    protected void build() {
        if (unit.isBeingConstructed() || unit.isMorphing()) return;
        if (plan.getBuildPosition() != null) {
            // NOTE: this assumes plan and buildPosition are NOT NULL
            debugBuild();
        }
        if (!isReady) return;

        if (unit.isCarrying()) {
            unit.returnCargo();
            setUnready();
            return;
        }

        UnitType plannedUnitType = plan.getPlannedUnit();

        // TODO: This should be determined with a building location planner
        // Should be assigned to the plan before the plan is assigned to the unit
        if (plan.getBuildPosition() == null) {
            TilePosition buildLocation = game.getBuildLocation(plannedUnitType, unit.getTilePosition());
            plan.setBuildPosition(buildLocation);
        }
        UnitType buildingType = plan.getPlannedUnit();
        Position buildTarget = getBuilderMoveLocation(buildingType, plan.getBuildPosition());
        if (unit.getDistance(buildTarget) > 150 || unit.isGatheringMinerals()) {
            setUnready();
            unit.move(buildTarget);
            return;
        }

        if (game.canMake(plannedUnitType, unit)) {
            // Try to build
            setUnready();
            boolean didBuild = unit.build(plannedUnitType, plan.getBuildPosition());
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
                    plan.setBuildPosition(game.getBuildLocation(plannedUnitType, unit.getTilePosition()));
                }

            }

            if (didBuild) {
                plan.setState(PlanState.MORPHING);
            }
        }
    }

    private Position getBuilderMoveLocation(UnitType building, TilePosition buildTarget) {
        int height = building.tileHeight() * 16;
        int width = building.tileWidth() * 16;

        return buildTarget.toPosition().add(new Position(width, height));
    }

    protected void morph() {
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

    protected void setUnready() {
        isReady = false;
        unreadyUntilFrame = game.getFrameCount() + game.getLatencyFrames() + 11;
    }

    protected void scout() {
        // Need to reassign movementTarget
        if (movementTargetPosition == null) {
            return;
        }

        debugScout();
        if (!isReady) {
            return;
        }

        if (game.isVisible(movementTargetPosition)) {
            movementTargetPosition = null;
            return;
        }
        setUnready();
        unit.move(movementTargetPosition.toPosition());
    }

    protected void fight() {
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
            if (canKite(fightTarget)) {
                kiteEnemy(fightTarget);
            } else {
                unit.attack(fightTarget.getPosition());
            }
            return;
        }

        if (movementTargetPosition != null && game.isVisible(movementTargetPosition)) {
            movementTargetPosition = null;
        }

        if (movementTargetPosition != null) {
            unit.move(movementTargetPosition.toPosition());
            return;
        }

        role = UnitRole.IDLE;
    }

    protected void retreat() {
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
    }

    protected void defend() {}

    /**
     * Assigns the closest enemy as a fight target.
     * @param newFightTarget new target
     */
    public void setFightTarget(Unit newFightTarget) {
        // We bail out if we're close enough to the unit to avoid deadlocking on weird micro situations
        // Don't bail if it's a building though
        if (fightTarget != null && !fightTarget.getType().isBuilding() && fightTarget.getDistance(unit) < LOCK_ENEMY_WITHIN_DISTANCE) {
            return;
        }
        fightTarget = newFightTarget;
        movementTargetPosition = newFightTarget.getTilePosition();
    }

    private void kiteEnemy(Unit enemy) {
        if (enemy == null || !enemy.exists() || !enemy.isVisible()) {
            return;
        }

        boolean isEnemyAir = enemy.isFlying();
        WeaponType weapon = isEnemyAir ? unit.getType().airWeapon() : unit.getType().groundWeapon();
        WeaponType enemyWeapon = unit.isFlying() ? enemy.getType().airWeapon() : enemy.getType().groundWeapon();

        if (weapon == null) {
            unit.attack(enemy.getPosition());
            return;
        }

        int attackRange = weapon.maxRange();
        int cooldown = isEnemyAir ? unit.getAirWeaponCooldown() : unit.getGroundWeaponCooldown();

        Position enemyPos = enemy.getPosition();
        Position myPos = unit.getPosition();
        double distance = myPos.getDistance(enemyPos);
        double kiteThreshold = weapon.maxRange() * 0.9;
        if (weapon.maxRange() > enemyWeapon.maxRange()) {
            kiteThreshold = enemyWeapon.maxRange() + 1;
        }

        final boolean inAttackRange = distance <= attackRange;
        final boolean outsideKiteThreshold = distance >= kiteThreshold;

        if (cooldown == 0) {
            if (inAttackRange && outsideKiteThreshold) {
                unit.attack(enemy);
            } else {
                unit.attack(enemyPos);
            }
        } else {
            int moveDistance = 64; // Adjust this value as needed
            double dx = myPos.x - enemyPos.x;
            double dy = myPos.y - enemyPos.y;
            double length = Math.sqrt(dx * dx + dy * dy);
            if (length == 0) {
                unit.move(new Position(myPos.x + moveDistance, myPos.y));
                return;
            }
            dx /= length;
            dy /= length;
            Position kitePosition = new Position(
                    (int)(myPos.x + dx * moveDistance),
                    (int)(myPos.y + dy * moveDistance)
            );
            unit.move(kitePosition);
        }
    }

    private boolean canKite(Unit enemy) {
        if (enemy == null || !enemy.exists() || !enemy.isVisible()) {
            return false;
        }
        boolean isEnemyAir = enemy.isFlying();
        WeaponType weapon = isEnemyAir ? unit.getType().airWeapon() : unit.getType().groundWeapon();
        return weapon != null && weapon.maxRange() > 32;
    }
}
