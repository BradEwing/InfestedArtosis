package unit.managed;

import bwapi.Game;
import bwapi.Position;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WalkPosition;
import bwapi.WeaponType;
import info.map.GameMap;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import macro.plan.Plan;
import macro.plan.PlanState;
import util.Filter;
import util.Vec2;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ManagedUnit {
    protected static int LOCK_ENEMY_WITHIN_DISTANCE = 25;
    protected static int THREE_SECONDS = 72;
    protected static int FIVE_SECONDS = 120;
    protected Game game;
    protected GameMap gameMap;

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
    @Setter @Getter
    protected Position containPosition;
    protected List<TilePosition> pathToTarget;

    @Setter
    public Position retreatTarget;
    private Position lastRetreatPosition;
    private int framesStuck = 0;
    private int retreatStartFrame = 0;

    @Setter @Getter
    protected Unit defendTarget;
    public Unit fightTarget;
    @Setter
    protected Unit gatherTarget;

    protected boolean hasNewGatherTarget;

    @Setter @Getter
    protected Plan plan;
    protected int buildAttemptFrame;
    protected Unit blockingMineral;

    @Setter
    protected boolean canFight = true;

    @Getter
    protected int unreadyUntilFrame = 0;
    protected boolean isReady = true;

    public ManagedUnit(Game game, Unit unit, UnitRole role, GameMap gameMap) {
        this.game = game;
        this.unit = unit;
        this.role = role;
        this.gameMap = gameMap;

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

    public Position getPosition() { 
        return unit.getPosition(); 
    }

    public boolean isReady() { 
        return this.isReady; 
    }

    public void setReady(boolean isReady) { 
        this.isReady = isReady; 
    }

    public boolean canFight() {
        return this.canFight; 
    }

    public void setNewGatherTarget(boolean hasNewGatherTarget) { 
        this.hasNewGatherTarget = hasNewGatherTarget; 
    }

    public void execute() {
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
            case CONTAIN:
                contain();
                break;
            default:
                break;
        }
    }

    public Position getRetreatPosition() {
        return getRetreatPosition(null);
    }

    public Position getRetreatPosition(Set<Position> stormPositions) {
        int currentX = unit.getX();
        int currentY = unit.getY();
        Position currentPos = new Position(currentX, currentY);

        if (stormPositions != null && !stormPositions.isEmpty()) {
            Position nearestStorm = null;
            double nearestStormDistance = Double.MAX_VALUE;

            for (Position stormPos : stormPositions) {
                double distance = currentPos.getDistance(stormPos);
                if (distance < nearestStormDistance) {
                    nearestStorm = stormPos;
                    nearestStormDistance = distance;
                }
            }

            if (nearestStorm != null && nearestStormDistance <= 128) {
                Vec2 away = Vec2.between(nearestStorm, currentPos);
                if (away.length() > 0) {
                    return away.normalizeToLength(192).toPosition(currentPos);
                }
            }
        }

        List<Unit> enemies = getEnemiesInRadius(currentX, currentY);

        if (enemies.isEmpty()) {
            return null;
        }

        double sumDx = 0;
        double sumDy = 0;

        for (Unit enemy : enemies) {
            sumDx += enemy.getX() - currentX;
            sumDy += enemy.getY() - currentY;
        }

        Vec2 away = new Vec2(-sumDx, -sumDy);
        if (away.length() == 0) {
            return null;
        }

        Position retreatPos = away.normalizeToLength(128).toPosition(currentPos);

        if (!isRetreatPathWalkable(currentPos, retreatPos)) {
            retreatPos = findAlternativeRetreatPosition(currentPos, retreatPos);
        }

        return retreatPos;
    }

    protected Position getSimpleRetreatPosition() {
        int currentX = unit.getX();
        int currentY = unit.getY();
        Position currentPos = new Position(currentX, currentY);
        List<Unit> enemies = getEnemiesInRadius(currentX, currentY);
        if (enemies.isEmpty()) {
            return null;
        }

        double sumDx = 0;
        double sumDy = 0;
        for (Unit enemy : enemies) {
            sumDx += enemy.getX() - currentX;
            sumDy += enemy.getY() - currentY;
        }

        Vec2 away = new Vec2(-sumDx, -sumDy);
        if (away.length() == 0) {
            return null;
        }

        return away.normalizeToLength(128).clampToMap(game, currentPos);
    }

    protected List<Unit> getEnemiesInRadius(int currentX, int currentY) {
        List<Unit> enemies = game.getUnitsInRadius(currentX, currentY, 128)
                .stream()
                .filter(u -> u.getPlayer() != game.self())
                .filter(u -> !u.getType().isBuilding() || Filter.isHostileBuildingToGround(u.getType()))
                .collect(Collectors.toList());
        return enemies;
    }

    public boolean doesMovementIntersectStorm(Position targetPos, Set<Position> stormPositions) {
        if (stormPositions == null || stormPositions.isEmpty() || targetPos == null) {
            return false;
        }

        Position currentPos = unit.getPosition();
        Vec2 dir = Vec2.between(currentPos, targetPos);
        double distance = dir.length();

        if (distance == 0) {
            return false;
        }

        Vec2 normalized = dir.normalize();
        int numSamples = Math.min((int)(distance / 16.0), 20);

        for (int i = 0; i <= numSamples; i++) {
            double progress = numSamples > 0 ? (double)i / numSamples : 0;
            int checkX = currentPos.getX() + (int)(normalized.x * distance * progress);
            int checkY = currentPos.getY() + (int)(normalized.y * distance * progress);
            Position checkPos = new Position(checkX, checkY);

            for (Position stormCenter : stormPositions) {
                double distToStorm = checkPos.getDistance(stormCenter);
                if (distToStorm <= 96) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks if the direct path from current position to retreat target intersects any non-walkable terrain.
     * Uses GameMap's accessible WalkPositions to account for neutral barriers and terrain obstacles.
     * Samples WalkPositions along the path for efficiency while ensuring thorough coverage.
     */
    private boolean isRetreatPathWalkable(Position currentPos, Position retreatTarget) {
        Vec2 dir = Vec2.between(currentPos, retreatTarget);
        double distance = dir.length();

        if (distance == 0) {
            return true;
        }

        Vec2 normalized = dir.normalize();

        Set<WalkPosition> accessibleWalkPositions = gameMap.getAccessibleWalkPositions();
        if (accessibleWalkPositions.isEmpty()) {
            return isBasicPathWalkable(currentPos, normalized, distance);
        }

        int numSteps = Math.min((int) Math.ceil(distance / 4.0), 8);

        for (int i = 1; i <= numSteps; i++) {
            double progress = (double) i / numSteps;
            int checkX = currentPos.getX() + (int)(normalized.x * distance * progress);
            int checkY = currentPos.getY() + (int)(normalized.y * distance * progress);
            WalkPosition walkPos = new WalkPosition(new Position(checkX, checkY));

            if (!accessibleWalkPositions.contains(walkPos)) {
                return false;
            }
        }

        return true;
    }

    private boolean isBasicPathWalkable(Position currentPos, Vec2 normalized, double distance) {
        int numSteps = Math.min((int) Math.ceil(distance / 4.0), 8);

        for (int i = 1; i <= numSteps; i++) {
            double progress = (double) i / numSteps;
            int checkX = currentPos.getX() + (int)(normalized.x * distance * progress);
            int checkY = currentPos.getY() + (int)(normalized.y * distance * progress);

            if (!game.isWalkable(new WalkPosition(new Position(checkX, checkY)))) {
                return false;
            }
        }

        return true;
    }
    
    private Position findAlternativeRetreatPosition(Position currentPos, Position originalRetreat) {
        List<Unit> enemies = game.getUnitsInRadius(currentPos.getX(), currentPos.getY(), 256)
                .stream()
                .filter(u -> u.getPlayer() != game.self())
                .filter(u -> !u.getType().isBuilding() || Filter.isHostileBuildingToGround(u.getType()))
                .collect(Collectors.toList());

        if (enemies.isEmpty()) {
            return originalRetreat;
        }

        Position bestPosition = null;
        double bestMinEnemyDistance = -1;

        int[] distances = {128, 96, 64, 48, 32};
        for (int distance : distances) {
            for (int angle = 0; angle < 360; angle += 30) {
                double rad = Math.toRadians(angle);
                int testX = Math.max(0, Math.min(currentPos.getX() + (int)(Math.cos(rad) * distance), game.mapWidth() * 32 - 1));
                int testY = Math.max(0, Math.min(currentPos.getY() + (int)(Math.sin(rad) * distance), game.mapHeight() * 32 - 1));
                Position candidatePos = new Position(testX, testY);

                if (!game.isWalkable(new WalkPosition(candidatePos)) ||
                    !isRetreatPathWalkable(currentPos, candidatePos)) {
                    continue;
                }

                double minDistToEnemy = Double.MAX_VALUE;
                for (Unit enemy : enemies) {
                    double dist = candidatePos.getDistance(enemy.getPosition());
                    if (dist < minDistToEnemy) {
                        minDistToEnemy = dist;
                    }
                }

                if (minDistToEnemy > bestMinEnemyDistance
                    || minDistToEnemy == bestMinEnemyDistance
                     && bestPosition != null
                     && candidatePos.getDistance(originalRetreat) < bestPosition.getDistance(originalRetreat)) {
                    bestMinEnemyDistance = minDistToEnemy;
                    bestPosition = candidatePos;
                }
            }
        }

        if (bestPosition != null) {
            return bestPosition;
        }
        return rallyPoint != null ? rallyPoint : currentPos;
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

    protected void contain() {
        if (containPosition == null) {
            role = UnitRole.IDLE;
            return;
        }

        Unit nearbyEnemy = findClosestEnemyInRange();
        if (nearbyEnemy != null) {
            setUnready(6);
            unit.attack(nearbyEnemy);
            return;
        }

        if (unit.getDistance(containPosition) < 24) {
            setUnready(6);
            unit.holdPosition();
            return;
        }

        setUnready(6);
        unit.move(containPosition);
    }

    protected void gather() {}

    protected void build() {
        if (unit.isBeingConstructed() || unit.isMorphing()) return;

        if (unit.isCarrying()) {
            unit.returnCargo();
            setUnready();
            return;
        }

        if (blockingMineral != null) {
            if (unit.isGatheringMinerals()) {
                return;
            }
            if (blockingMineral.exists()) {
                gatherBlockerMineral();
                return;
            }
            gameMap.removeBlockingMineral(blockingMineral);
            Unit nextBlocker = gameMap.findNearbyBlockingMineral(unit.getPosition(), 32);
            if (nextBlocker != null) {
                blockingMineral = nextBlocker;
                gatherBlockerMineral();
                return;
            }
            blockingMineral = null;
        }

        final int currentFrame = game.getFrameCount();
        if (plan.getPredictedReadyFrame() > 0 && currentFrame > plan.getPredictedReadyFrame() + FIVE_SECONDS) {
            Unit nearbyBlocker = gameMap.findNearbyBlockingMineral(unit.getPosition(), 256);
            if (nearbyBlocker != null) {
                blockingMineral = nearbyBlocker;
                gatherBlockerMineral();
                return;
            }
        }

        UnitType plannedUnitType = plan.getPlannedUnit();

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
            setUnready();
            boolean didBuild = unit.build(plannedUnitType, plan.getBuildPosition());
            if (!didBuild) {
                didBuild = unit.morph(plannedUnitType);
            }

            final int frameCount = game.getFrameCount();

            if (!didBuild && buildAttemptFrame == 0) {
                buildAttemptFrame = frameCount;
            }

            if (!didBuild) {
                if (buildAttemptFrame + 150 < frameCount) {
                    plan.setBuildPosition(game.getBuildLocation(plannedUnitType, unit.getTilePosition()));
                }
            }

            if (didBuild) {
                plan.setState(PlanState.MORPHING);
            }
        }
    }

    private void gatherBlockerMineral() {
        setUnready(THREE_SECONDS);
        unit.gather(blockingMineral);
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
        if (movementTargetPosition == null) {
            return;
        }

        Position current = unit.getPosition();
        List<Unit> threats = getEnemiesInRadius(current.getX(), current.getY());

        if (!threats.isEmpty()) {
            Position retreatPos = getSimpleRetreatPosition();
            if (retreatPos == null) retreatPos = current;
            Vec2 awayDir = Vec2.between(current, retreatPos).normalize();
            Vec2 targetDir = Vec2.between(current, movementTargetPosition.toPosition()).normalize();

            double dot = awayDir.x * targetDir.x + awayDir.y * targetDir.y;
            Vec2 blended = dot > 0
                    ? new Vec2(awayDir.x + 0.5 * targetDir.x, awayDir.y + 0.5 * targetDir.y).normalize()
                    : awayDir;

            setUnready();
            unit.move(blended.normalizeToLength(128).clampToMap(game, current));
            return;
        }

        setUnready();
        unit.move(movementTargetPosition.toPosition());
    }

    private void updateState() {
        if (movementTargetPosition != null) {
            if (role == UnitRole.SCOUT) {
                TilePosition targetTile = movementTargetPosition;
                Position targetPos = targetTile.toPosition();
                double distance = unit.getPosition().getDistance(targetPos);
                if (distance < 128 || game.isVisible(targetTile)) {
                    movementTargetPosition = null;
                }
            } else if (role == UnitRole.FIGHT && game.isVisible(movementTargetPosition)) {
                movementTargetPosition = null;
            }
        }

        if (retreatTarget != null) {
            if (unit.getDistance(retreatTarget) < 16 || unit.isIdle() || !game.isWalkable(new WalkPosition(retreatTarget))) {
                retreatTarget = null;
                lastRetreatPosition = null;
                framesStuck = 0;
            }
        }

        if (fightTarget != null) {
            if (!fightTarget.exists() ||
                    !fightTarget.isTargetable() ||
                    fightTarget.getType() == UnitType.Resource_Vespene_Geyser) {
                fightTarget = null;
            }
        }

        if (defendTarget != null) {
            if (!defendTarget.exists()) {
                defendTarget = null;
                movementTargetPosition = null;
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
            Position next = getRetreatPosition();
            if (next != null) {
                setRetreatTarget(next);
            } else {
                fallbackToRally();
                return;
            }
        }

        setUnready(4);

        Position currentPosition = unit.getPosition();
        if (lastRetreatPosition != null && currentPosition.getDistance(lastRetreatPosition) < 1.0) {
            framesStuck++;
        } else {
            lastRetreatPosition = currentPosition;
            framesStuck = 0;
        }

        if (framesStuck >= 12) {
            setRetreatTarget(null);
            fallbackToRally();
            return;
        }

        if (unit.getDistance(retreatTarget) < getRetreatArrivalDistance()) {
            Position next = getRetreatPosition();
            if (next != null) {
                setRetreatTarget(next);
            } else {
                fallbackToRally();
                return;
            }
        }

        unit.move(retreatTarget);
    }

    private void fallbackToRally() {
        if (rallyPoint != null) {
            role = UnitRole.RALLY;
        } else {
            role = UnitRole.IDLE;
        }
    }

    /**
     * Returns the distance threshold for recomputing retreat position.
     * Override in subclasses to customize behavior.
     */
    protected int getRetreatArrivalDistance() {
        return 16;
    }

    public void markRetreatStart(int frame) {
        if (retreatStartFrame == 0) {
            retreatStartFrame = frame;
        }
    }

    public Integer getRetreatStartFrame() {
        return retreatStartFrame;
    }

    public void clearRetreatStart() {
        retreatStartFrame = 0;
    }

    protected void defend() {}

    /**
     * Assigns the closest enemy as a fight target.
     * @param newFightTarget new target
     */
    public void setFightTarget(Unit newFightTarget) {
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
            int moveDistance = 64;
            Vec2 away = Vec2.between(enemyPos, myPos);
            if (away.length() == 0) {
                unit.move(new Position(myPos.x + moveDistance, myPos.y));
                return;
            }
            unit.move(away.normalizeToLength(moveDistance).toPosition(myPos));
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
                return enemy;
            }
        }
        return null;
    }
}
