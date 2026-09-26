package unit.managed;

import bwapi.Game;
import bwapi.Order;
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
import telemetry.PlanEvents;
import util.Filter;
import util.MeleeOverflowGate;
import util.Vec2;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ManagedUnit {
    protected static int THREE_SECONDS = 72;
    public static final int MELEE_MARGIN = 16;
    /**
     * How far, in pixels, an attack-move already under way may point from its destination before it is issued again,
     * and how far the fight target may move from where the destination was measured before it is measured again.
     */
    static final int ATTACK_MOVE_REISSUE_DISTANCE = 64;
    /**
     * How far, in pixels, past the fight target along the unit's approach an overflow attack-move is aimed. Three
     * tiles clears the ring of melee attackers already on the target, so the unit walks through or around the stack
     * and its auto-acquire finds whatever is beside or behind the target, instead of stopping at the back of the
     * stack. A judgment call, not measured.
     */
    static final int OVERFLOW_PAST_DISTANCE = 96;
    static final int OUTRANGED_EVADE_FRAMES = 12;
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
    @Setter @Getter
    protected Position perchPosition;
    @Setter @Getter
    protected Position runbyDestination;
    protected List<TilePosition> pathToTarget;

    @Setter
    public Position retreatTarget;
    private Position lastRetreatPosition;
    private int framesStuck = 0;
    private int retreatStartFrame = 0;

    @Setter @Getter
    protected Unit defendTarget;
    public Unit fightTarget;
    /**
     * True when the fight target is saturated with melee attackers and the unit attack-moves past it
     * instead of attacking it, so the game's auto-acquire picks what it hits.
     */
    @Getter
    private boolean attackMoving;
    @Getter
    private final MeleeOverflowGate overflowGate = new MeleeOverflowGate();
    private Position attackMoveDestination;
    private Position attackMoveAnchor;
    @Setter
    protected Unit gatherTarget;

    protected boolean hasNewGatherTarget;

    @Getter
    protected Plan plan;
    protected int buildAttemptFrame;
    private final BuilderStall<Unit> builderStall = new BuilderStall<>();

    @Setter
    protected boolean canFight = true;

    @Getter
    protected int unreadyUntilFrame = 0;
    protected boolean isReady = true;

    private int lastHitPoints = -1;
    private UnitType lastHitPointsType;
    @Getter
    private int hitPointsBefore = -1;
    private int hitFrame = -1;
    @Getter
    private int attacksStarted;
    @Getter
    private int lastAttackStartFrame = -1;

    private Position evadePosition;
    private int evadeFrame = -1;

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

    /**
     * Assigns the plan. A change of plan, including eviction to null, clears the builder's blocker
     * and walk progress so the next plan does not resume the old one's wall.
     */
    public void setPlan(Plan plan) {
        builderStall.onPlanChange(this.plan, plan);
        this.plan = plan;
    }

    /** Whether the builder has been diverted to mine out a mineral blocking its walk. */
    public boolean isClearingBlocker() {
        return builderStall.getBlocker() != null;
    }

    public boolean isIrradiated() {
        return unit.isIrradiated();
    }

    public void setNewGatherTarget(boolean hasNewGatherTarget) { 
        this.hasNewGatherTarget = hasNewGatherTarget; 
    }

    /**
     * Executes the unit's role. An evade ordered this frame is issued first and ignores the ready gate, so a unit
     * hit by something it cannot answer moves on the frame the hit is seen.
     */
    public void execute() {
        updateState();

        if (evadePosition != null && evadeFrame == game.getFrameCount()) {
            issueEvade();
            return;
        }

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
                rally();
                break;
            case CONTAIN:
                contain();
                break;
            case PERCH:
                perch();
                break;
            case RUNBY:
                runby();
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

        int scanRadius = retreatScanRadius();
        List<Unit> enemies = game.getUnitsInRadius(currentX, currentY, scanRadius)
                .stream()
                .filter(u -> u.getPlayer() != game.self())
                .filter(u -> !u.getType().isBuilding() || Filter.isHostileBuilding(u.getType()))
                .collect(Collectors.toList());

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

        if (unit.isFlying()) {
            away = applyBorderRepulsion(away, currentX, currentY);
        }

        Position retreatPos = away.normalizeToLength(retreatFleeDistance()).clampToMap(game, currentPos);

        if (!unit.isFlying() && !isRetreatPathWalkable(currentPos, retreatPos)) {
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
                .filter(u -> !u.getType().isBuilding() || Filter.isHostileBuilding(u.getType()))
                .collect(Collectors.toList());
        return enemies;
    }

    private static final int BORDER_REPULSION_DISTANCE = 64;

    private Vec2 applyBorderRepulsion(Vec2 flee, int currentX, int currentY) {
        int mapWidth = game.mapWidth() * 32;
        int mapHeight = game.mapHeight() * 32;
        double bx = 0;
        double by = 0;
        if (currentX < BORDER_REPULSION_DISTANCE) {
            bx = BORDER_REPULSION_DISTANCE - currentX;
        } else if (currentX > mapWidth - BORDER_REPULSION_DISTANCE) {
            bx = (mapWidth - BORDER_REPULSION_DISTANCE) - currentX;
        }
        if (currentY < BORDER_REPULSION_DISTANCE) {
            by = BORDER_REPULSION_DISTANCE - currentY;
        } else if (currentY > mapHeight - BORDER_REPULSION_DISTANCE) {
            by = (mapHeight - BORDER_REPULSION_DISTANCE) - currentY;
        }
        if (bx == 0 && by == 0) {
            return flee;
        }
        Vec2 border = new Vec2(bx / BORDER_REPULSION_DISTANCE, by / BORDER_REPULSION_DISTANCE);
        Vec2 fleeNorm = flee.normalize();
        return new Vec2(fleeNorm.x + border.x, fleeNorm.y + border.y);
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
                .filter(u -> !u.getType().isBuilding() || Filter.isHostileBuilding(u.getType()))
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

    protected void perch() {
        if (perchPosition == null) {
            role = UnitRole.IDLE;
            return;
        }

        Position current = unit.getPosition();
        List<Unit> threats = getEnemiesInRadius(current.getX(), current.getY());
        if (!threats.isEmpty()) {
            Position flee = getSimpleRetreatPosition();
            if (flee != null) {
                setUnready();
                unit.move(flee);
                return;
            }
        }

        if (unit.getDistance(perchPosition) < 24) {
            if (!unit.isHoldingPosition()) {
                setUnready(6);
                unit.holdPosition();
            }
            return;
        }

        setUnready();
        unit.move(perchPosition);
    }

    protected void gather() {}

    protected void build() {
        if (unit.isBeingConstructed() || unit.isMorphing()) return;

        if (unit.isCarrying()) {
            unit.returnCargo();
            setUnready();
            return;
        }

        Unit blockingMineral = builderStall.getBlocker();
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
                builderStall.divertTo(nextBlocker);
                gatherBlockerMineral();
                return;
            }
            builderStall.clearBlocker();
        }

        UnitType plannedUnitType = plan.getPlannedUnit();

        if (plan.getBuildPosition() == null) {
            TilePosition buildLocation = game.getBuildLocation(plannedUnitType, unit.getTilePosition());
            plan.setBuildPosition(buildLocation);
        }
        UnitType buildingType = plan.getPlannedUnit();
        Position buildTarget = getBuilderMoveLocation(buildingType, plan.getBuildPosition());
        int distanceToTarget = unit.getDistance(buildTarget);
        boolean harvesting = unit.isGatheringMinerals() || unit.isGatheringGas();
        Unit stallBlocker = builderStall.divertIfStalled(unit.getPosition(), buildTarget, distanceToTarget, harvesting,
                game.getFrameCount(), gameMap::findNearbyBlockingMineral);
        if (stallBlocker != null) {
            PlanEvents.blockerDiverted(plan, stallBlocker.getPosition());
            gatherBlockerMineral();
            return;
        }
        if (distanceToTarget > BuilderStall.ARRIVAL_DISTANCE || unit.isGatheringMinerals()) {
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
        unit.gather(builderStall.getBlocker());
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

    /**
     * Reads the unit's hit points for this frame, before any role runs. A change of type, such as a morph, starts
     * the reading over, so it never reads as a hit.
     *
     * @param frame current frame
     */
    public void observeHitPoints(int frame) {
        int hitPoints = unit.getHitPoints();
        UnitType type = unit.getType();
        hitPointsBefore = type == lastHitPointsType ? lastHitPoints : hitPoints;
        lastHitPoints = hitPoints;
        lastHitPointsType = type;
        if (hitPoints < hitPointsBefore) {
            hitFrame = frame;
        }
    }

    /**
     * Counts an attack when the unit is starting one on this frame, and remembers the frame. Read every frame, so
     * {@link #getAttacksStarted} is the number of attacks the unit has started since it was first managed.
     *
     * @param frame current frame
     */
    public void observeAttack(int frame) {
        if (unit.isStartingAttack()) {
            attacksStarted++;
            lastAttackStartFrame = frame;
        }
    }

    /**
     * @param frame current frame
     * @return true when the unit lost hit points on the frame
     */
    public boolean wasHitOn(int frame) {
        return hitFrame == frame;
    }

    /**
     * Whether a unit was hit by something it cannot answer: it lost hit points this frame, it holds a role that
     * stands in or walks through enemy fire, and no enemy is within its own range plus {@link #MELEE_MARGIN}. A unit
     * actually in melee is fighting back, and regeneration only ever raises hit points.
     *
     * @param hpBefore hit points on the previous frame
     * @param hpNow hit points now
     * @param role the unit's role
     * @param enemyInOwnRange true when an enemy is within the unit's own range plus the melee margin
     * @return true for an outranged hit
     */
    public static boolean isOutrangedHit(int hpBefore, int hpNow, UnitRole role, boolean enemyInOwnRange) {
        if (hpNow >= hpBefore || enemyInOwnRange) {
            return false;
        }
        return role == UnitRole.CONTAIN || role == UnitRole.RALLY || role == UnitRole.FIGHT
                || role == UnitRole.RUNBY;
    }

    /**
     * Whether an outranged hit moves the unit, given the role it holds once its squad has decided. A containing,
     * rallying or running by unit steps out. A fighting unit closing on its target keeps closing: it was sent into
     * that fire by a fight verdict, and stepping out on every hit would stop it ever reaching the target. Any other
     * role, such as a retreat ordered this frame, already moves the unit.
     *
     * @param role the unit's role
     * @param closingOnTarget true when the unit has a live fight target
     * @return true when the unit evades
     */
    public static boolean evadesOutrangedHit(UnitRole role, boolean closingOnTarget) {
        if (role == UnitRole.FIGHT) {
            return !closingOnTarget;
        }
        return role == UnitRole.CONTAIN || role == UnitRole.RALLY || role == UnitRole.RUNBY;
    }

    /**
     * Whether the unit can step out of fire right now. A burrowed unit, or a type that cannot move, keeps its
     * position and goes on attacking from its role instead.
     *
     * @param burrowed true when the unit is burrowed
     * @param typeCanMove true when the unit's type can move
     * @return true when an evade move can be carried out
     */
    public static boolean canStepOut(boolean burrowed, boolean typeCanMove) {
        return !burrowed && typeCanMove;
    }

    /**
     * @return true when the unit can carry out an evade move this frame
     */
    public boolean canStepOutNow() {
        return canStepOut(unit.isBurrowed(), unitType.canMove());
    }

    /**
     * @return true when the unit has a fight target that still exists
     */
    public boolean isClosingOnTarget() {
        return fightTarget != null && fightTarget.exists();
    }

    /**
     * Whether an enemy the unit would attack stands within its own ground range plus the margin, edge to edge.
     *
     * @param margin pixels added to the unit's ground range
     * @return true when such an enemy is there
     */
    public boolean hasEnemyWithinReach(int margin) {
        WeaponType weapon = unitType.groundWeapon();
        int reach = (weapon == null || weapon == WeaponType.None ? 0 : weapon.maxRange()) + margin;
        int searchRadius = reach + Math.max(Math.max(unitType.dimensionLeft(), unitType.dimensionRight()),
                Math.max(unitType.dimensionUp(), unitType.dimensionDown())) + 32;
        for (Unit enemy : game.getUnitsInRadius(unit.getPosition(), searchRadius)) {
            UnitType enemyType = enemy.getType();
            if (!enemy.getPlayer().isEnemy(game.self()) || !enemy.isTargetable()
                    || Filter.isLowPriorityCombatTarget(enemyType)
                    || enemyType.isBuilding() && !Filter.isHostileBuilding(enemyType)) {
                continue;
            }
            if (unit.getDistance(enemy) <= reach) {
                return true;
            }
        }
        return false;
    }

    /**
     * Orders the unit to move to a point on this frame, ahead of the ready gate.
     *
     * @param point where to move
     * @param frame current frame
     */
    public void evade(Position point, int frame) {
        evadePosition = point;
        evadeFrame = frame;
    }

    private void issueEvade() {
        unit.move(evadePosition);
        evadePosition = null;
        setUnready(OUTRANGED_EVADE_FRAMES);
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
            boolean invalidTarget = unit.getDistance(retreatTarget) < 16 || unit.isIdle();
            if (!unit.isFlying()) {
                invalidTarget = invalidTarget || !game.isWalkable(new WalkPosition(retreatTarget));
            }
            if (invalidTarget) {
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
            if (attackMoving) {
                attackMoveToward(fightTarget);
            } else if (canKite(fightTarget)) {
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

    /**
     * Acts on the order a runby squad gave this unit. Only zerglings run by, so every other type fights.
     */
    protected void runby() {
        fight();
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

    protected int retreatScanRadius() {
        return 128;
    }

    protected int retreatFleeDistance() {
        return 128;
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

    public void setFightTarget(Unit newFightTarget) {
        setFightTarget(newFightTarget, false);
    }

    /**
     * Sets the fight target and whether the unit attack-moves past it rather than attacking it.
     *
     * @param newFightTarget the target, or null for none
     * @param attackMove true to attack-move toward the target instead of attacking it
     */
    public void setFightTarget(Unit newFightTarget, boolean attackMove) {
        boolean sameTarget = newFightTarget != null && newFightTarget.equals(fightTarget);
        fightTarget = newFightTarget;
        attackMoving = attackMove && newFightTarget != null;
        if (!attackMoving || !sameTarget) {
            attackMoveDestination = null;
            attackMoveAnchor = null;
        }
        if (newFightTarget == null) {
            movementTargetPosition = null;
            return;
        }
        TilePosition targetTile = newFightTarget.getTilePosition();
        if (isValidTilePosition(targetTile)) {
            movementTargetPosition = targetTile;
        } else {
            movementTargetPosition = null;
        }
    }

    /**
     * Attack-moves to a point {@link #OVERFLOW_PAST_DISTANCE} past the target along the unit's approach, unless the
     * unit is attacking, is on an attack it acquired itself (see {@link #autoAcquired}), or is already attack-moving
     * to within {@link #ATTACK_MOVE_REISSUE_DISTANCE} of that point. The
     * point is measured once from where the unit and the target stand and kept, so it does not swing round as the
     * unit closes on or passes the target; it is measured again for a new target, or once the target has moved more
     * than {@link #ATTACK_MOVE_REISSUE_DISTANCE} from where it was measured.
     */
    protected void attackMoveToward(Unit target) {
        Position anchor = target.getPosition();
        if (attackMoveDestination == null || anchor.getDistance(attackMoveAnchor) > ATTACK_MOVE_REISSUE_DISTANCE) {
            attackMoveAnchor = anchor;
            attackMoveDestination = pastTarget(unit.getPosition(), anchor).clampToMap(game, anchor);
        }
        if (autoAcquired(unit.getOrder(), unit.getOrderTarget(), target)) {
            return;
        }
        if (needsAttackMove(unit.getOrder(), unit.getOrderTargetPosition(), unit.isAttacking(),
                attackMoveDestination)) {
            unit.attack(attackMoveDestination);
        }
    }

    /**
     * Whether an attack-moving unit is attacking an enemy it acquired on its own: its order is to attack a unit other
     * than the saturated fight target it was sent past. Such an attack is left to run, even while the unit closes on
     * that enemy between swings. An attack order on the fight target itself is the direct attack the unit held
     * before it entered overflow, and is replaced by the attack-move.
     *
     * @param order the unit's current order
     * @param orderTarget the unit its order targets, or null
     * @param fightTarget the saturated fight target
     */
    static boolean autoAcquired(Order order, Unit orderTarget, Unit fightTarget) {
        return order == Order.AttackUnit && orderTarget != null && !orderTarget.equals(fightTarget);
    }

    /**
     * The offset from the target to the point an overflow attack-move is aimed at: {@link #OVERFLOW_PAST_DISTANCE}
     * further along the line from the attacker through the target, or none when the two stand on one point.
     *
     * @param attacker the attacker's position
     * @param target the target's position
     * @return the offset to add to the target's position
     */
    static Vec2 pastTarget(Position attacker, Position target) {
        return Vec2.between(attacker, target).normalizeToLength(OVERFLOW_PAST_DISTANCE);
    }

    /**
     * Whether an attack-move toward the destination must be issued: not while the unit is attacking, and not while
     * its current attack-move already points within {@link #ATTACK_MOVE_REISSUE_DISTANCE} of the destination.
     *
     * @param order the unit's current order
     * @param orderTargetPosition where the current order points, or null
     * @param attacking true when the unit is attacking
     * @param destination where the attack-move is aimed
     */
    static boolean needsAttackMove(Order order, Position orderTargetPosition, boolean attacking,
                                   Position destination) {
        if (attacking) {
            return false;
        }
        return order != Order.AttackMove || orderTargetPosition == null
                || orderTargetPosition.getDistance(destination) > ATTACK_MOVE_REISSUE_DISTANCE;
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
            UnitType enemyType = enemy.getType();
            if (enemy.getPlayer().isEnemy(game.self()) && enemy.isTargetable() &&
                !util.Filter.isLowPriorityCombatTarget(enemyType) &&
                (!enemyType.isBuilding() || util.Filter.isHostileBuilding(enemyType))) {
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
