package unit.managed;

import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.Text;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WalkPosition;
import bwapi.WeaponType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import macro.plan.Plan;
import macro.plan.PlanState;

import java.util.List;
import java.util.stream.Collectors;

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


    @Setter @Getter
    protected Position rallyPoint;
    @Setter @Getter
    protected TilePosition movementTargetPosition;
    protected List<TilePosition> pathToTarget;

    @Setter
    protected Position retreatTarget;
    private Position lastRetreatPosition;
    private int framesStuck = 0;

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
        debug();

        updateState();

        if (!isReady) {
            return;
        }

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

    public Position getRetreatPosition() {
        int currentX = unit.getX();
        int currentY = unit.getY();
        List<Unit> enemies = game.getUnitsInRadius(currentX, currentY, 128)
                .stream()
                .filter(u -> u.getPlayer() != game.self())
                .collect(Collectors.toList());


        if (enemies.isEmpty()) {
            return new Position(currentX, currentY);
        }

        int sumDx = 0;
        int sumDy = 0;

        for (Unit enemy : enemies) {
            sumDx += enemy.getX() - currentX;
            sumDy += enemy.getY() - currentY;
        }

        int retreatDx = -sumDx;
        int retreatDy = -sumDy;

        double length = Math.sqrt(retreatDx * retreatDx + retreatDy * retreatDy);
        if (length == 0) {
            return new Position(currentX, currentY);
        }

        double scale = 128.0 / length;
        int newX = currentX + (int)(retreatDx * scale);
        int newY = currentY + (int)(retreatDy * scale);

        return new Position(newX, newY);
    }

    private void debug() {
        Position unitPosition = unit.getPosition();
        // Draw role text
        if (role != null) {
            game.drawTextMap(unitPosition, role.toString(), Text.Default);
        }
        if (role == UnitRole.BUILDING) {
            return;
        }

        if (movementTargetPosition != null) {
            Position movementPos = movementTargetPosition.toPosition();
            game.drawLineMap(unitPosition, movementPos, Color.White);
        }

        if (retreatTarget != null) {
            game.drawLineMap(unitPosition, retreatTarget, Color.Purple);
        }

        if (fightTarget != null) {
            Position fightTargetPos = fightTarget.getPosition();
            if (fightTargetPos != null) {
                game.drawLineMap(unitPosition, fightTargetPos, Color.Red);
            }
        }

        // Draw build lines and text
        if (plan != null && plan.getBuildPosition() != null ) {

            Position buildPosition = plan.getBuildPosition().toPosition();
            game.drawLineMap(unitPosition, buildPosition, Color.Cyan);

            int distance = unit.getDistance(buildPosition);
            String distanceText = String.format("Distance: %d", distance);
            Position textPosition = unitPosition.add(new Position(8, 8));
            game.drawTextMap(textPosition, distanceText, Text.Cyan);

        }


    }

    protected void rally() {
        if (rallyPoint == null) return;

        if (role == UnitRole.RALLY) {
            if (unit.getDistance(rallyPoint) < 32) {
                return;
            }
        }

        if (unit.getDistance(rallyPoint) < 16) {
            return;
        }

        setUnready();
        unit.move(rallyPoint);
    }

    protected void gather() {}

    // Attempt build or morph
    protected void build() {
        if (unit.isBeingConstructed() || unit.isMorphing()) return;

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
        setUnready(11);
    }

    protected void setUnready(int unreadyFrames) {
        isReady = false;
        unreadyUntilFrame = game.getFrameCount() + game.getLatencyFrames() + unreadyFrames;
    }

    protected void scout() {
        // Need to reassign movementTarget
        if (movementTargetPosition == null) {
            return;
        }

        setUnready();
        unit.move(movementTargetPosition.toPosition());
    }

    private void updateState() {
        // Check if movementTargetPosition should be set to null
        if (movementTargetPosition != null) {
            if (role == UnitRole.SCOUT && game.isVisible(movementTargetPosition)) {
                movementTargetPosition = null;
            } else if (role == UnitRole.FIGHT && game.isVisible(movementTargetPosition)) {
                movementTargetPosition = null;
            }
        }

        // Check if retreatTarget should be set to null
        if (retreatTarget != null) {
            if (unit.getDistance(retreatTarget) < 16 || unit.isIdle() || (!game.isWalkable(new WalkPosition(retreatTarget)))) {
                retreatTarget = null;
                lastRetreatPosition = null;
                framesStuck = 0;
            }
        }

        // Check if fightTarget should be set to null
        if (fightTarget != null) {
            if (!fightTarget.exists() ||
                    !fightTarget.isTargetable() ||
                    fightTarget.getType() == UnitType.Resource_Vespene_Geyser) {
                fightTarget = null;
            }
        }
    }

    protected void fight() {
        if (unit.isAttackFrame()) {
            return;
        }
        setUnready(11);

        if (fightTarget != null) {
            if (canKite(fightTarget)) {
                kiteEnemy(fightTarget);
            } else {
                unit.attack(fightTarget);
            }
            return;
        }

        if (movementTargetPosition != null) {
            unit.move(movementTargetPosition.toPosition());
            return;
        }

        role = UnitRole.IDLE;
    }

    protected void retreat() {
        if (retreatTarget == null) {
            role = UnitRole.IDLE;
            return;
        }

        setUnready(4);

        // Detect if unit is stuck
        Position currentPosition = unit.getPosition();
        if (lastRetreatPosition != null && currentPosition.getDistance(lastRetreatPosition) < 1.0) {
            framesStuck++;
        } else {
            lastRetreatPosition = currentPosition;
            framesStuck = 0;
        }

        if (framesStuck >= 12) {
            setRetreatTarget(null);
            role = UnitRole.IDLE;
            return;
        }

        unit.move(retreatTarget);
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
        TilePosition targetTile = newFightTarget.getTilePosition();
        if (isValidTilePosition(targetTile)) {
            movementTargetPosition = targetTile;
        } else {
            movementTargetPosition = null;
        }
    }

    protected int weaponRange(Unit enemy) {
        boolean isEnemyAir = enemy.isFlying();
        WeaponType weapon = isEnemyAir ? unit.getType().airWeapon() : unit.getType().groundWeapon();
        return weapon.maxRange();
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

        int cooldown = isEnemyAir ? unit.getAirWeaponCooldown() : unit.getGroundWeaponCooldown();

        Position enemyPos = enemy.getPosition();
        Position myPos = unit.getPosition();
        double distance = myPos.getDistance(enemyPos);
        double kiteThreshold = this.weaponRange(enemy) * 0.9;
        if (this.weaponRange(enemy) > enemyWeapon.maxRange()) {
            kiteThreshold = enemyWeapon.maxRange();
        }

        final boolean outsideKiteThreshold = distance >= kiteThreshold;

        if (enemy.getType().isBuilding()) {
            unit.attack(enemy);
            return;
        }

        if (cooldown == 0) {
            if (outsideKiteThreshold) {
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

    protected void handleNoTarget() {
        if (movementTargetPosition != null && isValidTilePosition(movementTargetPosition)) {
            unit.move(movementTargetPosition.toPosition());
            return;
        }
        role = UnitRole.IDLE;
    }

    private boolean isValidTilePosition(TilePosition tp) {
        return tp.getX() >= 0 && tp.getY() >= 0 && 
               tp.getX() < game.mapWidth() && tp.getY() < game.mapHeight();
    }

    /**
     * Finds the closest enemy unit within the unit's attack range.
     */
    protected Unit findClosestEnemyInRange() {
        Unit closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (Unit enemy : game.getUnitsInRadius(unit.getPosition(), weaponRange(unit))) {
            if (enemy.getPlayer().isEnemy(game.self()) && enemy.isTargetable() && 
                !util.Filter.isLowPriorityCombatTarget(enemy.getType())) {
                double enemyDistance = unit.getDistance(enemy);
                if (enemyDistance < closestDistance) {
                    closest = enemy;
                    closestDistance = enemyDistance;
                }
            }
        }
        return closest;
    }

    protected boolean canFightBack(Unit enemy) {
        if (enemy == null || !enemy.exists()) {
            return false;
        }
        WeaponType enemyWeapon = unit.isFlying() ? enemy.getType().airWeapon() : enemy.getType().groundWeapon();
        return enemyWeapon != null && enemyWeapon.maxRange() > 0;
    }

    protected Unit findThreateningEnemy() {
        for (Unit enemy : game.getUnitsInRadius(unit.getPosition(), weaponRange(unit) + 64)) {
            if (enemy.getPlayer().isEnemy(game.self()) && enemy.isTargetable() && canFightBack(enemy) &&
                !util.Filter.isLowPriorityCombatTarget(enemy.getType())) {
                return enemy; // Return the first dangerous enemy found
            }
        }
        return null;
    }
}
