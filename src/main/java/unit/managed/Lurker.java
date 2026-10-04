package unit.managed;

import bwapi.Game;
import bwapi.Order;
import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import info.map.GameMap;
import lombok.Getter;
import telemetry.BurrowCommand;
import telemetry.BurrowReason;
import telemetry.BurrowTelemetry;
import util.Filter;
import util.StaticDefenseZone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public class Lurker extends ManagedUnit {
    private int targetOutOfRangeFrames = 0;
    private int noAttackFrames = 0;
    private static final int MAX_TARGET_OUT_OF_RANGE_FRAMES = 20;
    private static final int MAX_NO_ATTACK_FRAMES = 50;
    /** Pixels from its contain point within which a burrowed Lurker stays put when the point moves. */
    static final int KEEP_BURROWED_DISTANCE = 64;
    /** Frames a withdrawal point is held before the Lurker takes its contain point again. */
    static final int WITHDRAW_HOLD_FRAMES = 240;
    /** Pixels added to a radius query, which measures center to center, to cover the size of the enemy. */
    static final int QUERY_PADDING = 96;

    private List<StaticDefenseZone> fixedFireZones = Collections.emptyList();
    private int fixedFirePadding;
    private Position withdrawPoint;
    private int withdrawFrame = -1;
    private int withdrawZoneCount;
    private Boolean commandedBurrow;

    /**
     * Pixels from its hold point within which a Lurker counts as arrived and burrows, the same tolerance
     * {@link #contain} gives a contain position.
     */
    static final int HOLD_ARRIVAL_DISTANCE = 24;
    private static final int HOLD_MOVE_FRAMES = 6;

    @Getter
    private Position holdPosition;

    public Lurker(Game game, Unit unit, UnitRole role, GameMap gameMap) {
        super(game, unit, role, gameMap);
    }

    /**
     * Sets the ground the enemy fires on from where it stands, which a burrowed Lurker does not stay near when its
     * contain point moves.
     *
     * @param zones the fixed-fire zones, at the reach learned over the game
     * @param padding pixels added to every zone's reach
     */
    public void setFixedFireZones(List<StaticDefenseZone> zones, int padding) {
        this.fixedFireZones = zones;
        this.fixedFirePadding = padding;
    }

    @Override
    protected void fight() {
        if (unit.isAttackFrame()) {
            return;
        }
        setUnready(11);

        boolean assignedInRange = !hasNoValidFightTarget() && !fightTarget.isFlying()
                && unit.getDistance(fightTarget.getPosition()) <= weaponRange(fightTarget);
        Unit inRange = findClosestGroundEnemyInRange();
        Unit chosen = pickFightTarget(fightTarget, assignedInRange, inRange);
        if (chosen != fightTarget) {
            fightTarget = chosen;
            resetCounters();
        }
        if (burrowsInFight(unit.isBurrowed(), unit.canBurrow(), assignedInRange || inRange != null)) {
            burrow(BurrowReason.FIGHT_ENEMY_IN_RANGE);
            resetCounters();
            return;
        }

        if (hasNoValidFightTarget()) {
            handleNoTarget();
            unburrowAndReset(BurrowReason.FIGHT_NO_TARGET);
            return;
        }

        int range = weaponRange(fightTarget);
        double distance = unit.getDistance(fightTarget.getPosition());
        if (unit.isBurrowed()) {
            if (distance > range) {
                targetOutOfRangeFrames++;
                if (targetOutOfRangeFrames >= MAX_TARGET_OUT_OF_RANGE_FRAMES) {
                    unburrowAndReset(BurrowReason.FIGHT_TARGET_OUT_OF_RANGE);
                    return;
                }
            } else {
                targetOutOfRangeFrames = 0;
            }

            if (unit.getGroundWeaponCooldown() == 0) {
                noAttackFrames++;
                if (noAttackFrames >= MAX_NO_ATTACK_FRAMES) {
                    unburrowAndReset(BurrowReason.FIGHT_IDLE);
                    return;
                }
            } else {
                noAttackFrames = 0;
            }
            unit.attack(fightTarget);
            return;
        }

        unit.move(fightTarget.getPosition());
    }

    /**
     * Retreats. A Lurker given a hold point walks out of fire to it and holds there, see {@link #holdStep}, keeping
     * its RETREAT role throughout. Without one it burrows where it stands and fights from there.
     */
    @Override
    protected void retreat() {
        if (holdPosition != null) {
            holdOutOfFire();
            return;
        }
        this.setUnready();
        this.setRole(UnitRole.FIGHT);
        if (!unit.isBurrowed() && unit.canBurrow()) {
            burrow(BurrowReason.RETREAT);
        }
    }

    /**
     * Gives the Lurker a point out of fire to walk to and hold.
     *
     * @param position the point
     */
    public void holdAt(Position position) {
        holdPosition = position;
    }

    public void clearHold() {
        holdPosition = null;
    }

    private void holdOutOfFire() {
        switch (holdStep(unit.isBurrowed(), unit.getDistance(holdPosition))) {
            case UNBURROW:
                setUnready();
                unburrowAndReset(BurrowReason.RETREAT_HOLD);
                return;
            case MOVE:
                setUnready(HOLD_MOVE_FRAMES);
                unit.move(holdPosition);
                return;
            case BURROW:
                setUnready();
                if (unit.canBurrow()) {
                    burrow(BurrowReason.RETREAT_HOLD);
                }
                resetCounters();
                return;
            default:
                setUnready();
                Unit nearbyEnemy = findClosestGroundEnemyInRange();
                if (nearbyEnemy != null) {
                    unit.attack(nearbyEnemy);
                }
        }
    }

    /**
     * The next step of walking out of fire to a hold point and holding it: a burrowed Lurker away from the point
     * unburrows, an unburrowed one walks to it, and once within {@link #HOLD_ARRIVAL_DISTANCE} of it the Lurker
     * burrows and then holds, attacking what comes into its range.
     *
     * @param burrowed whether the Lurker is burrowed
     * @param distanceToHold pixels from the Lurker to its hold point
     * @return the step to take this frame
     */
    static HoldStep holdStep(boolean burrowed, double distanceToHold) {
        boolean arrived = distanceToHold <= HOLD_ARRIVAL_DISTANCE;
        if (burrowed) {
            return arrived ? HoldStep.HOLD : HoldStep.UNBURROW;
        }
        return arrived ? HoldStep.BURROW : HoldStep.MOVE;
    }

    enum HoldStep {
        UNBURROW,
        MOVE,
        BURROW,
        HOLD
    }

    @Override
    protected void contain() {
        if (containPosition == null) {
            role = UnitRole.IDLE;
            return;
        }

        Unit nearbyEnemy = findClosestGroundEnemyInRange();
        if (answersEnemyInRange(nearbyEnemy != null, unit.isBurrowed(), holdsWithdrawalNow())) {
            setUnready(11);
            if (unit.isBurrowed()) {
                unit.attack(nearbyEnemy);
            } else if (unit.getDistance(nearbyEnemy) <= weaponRange(nearbyEnemy) && unit.canBurrow()) {
                burrow(BurrowReason.CONTAIN_ENEMY_IN_RANGE);
            } else {
                unit.move(nearbyEnemy.getPosition());
            }
            return;
        }

        Position hold = holdPoint();
        double distance = unit.getDistance(hold);
        if (distance < 24) {
            setUnready(11);
            if (!unit.isBurrowed() && unit.canBurrow()) {
                burrow(BurrowReason.CONTAIN_HOLD);
            }
            return;
        }

        if (unit.isBurrowed()) {
            if (staysBurrowed(distance, isInFixedFire(hold), isInFixedFire(unit.getPosition()))) {
                setUnready(11);
                return;
            }
            setUnready(6);
            unburrowAndReset(withdrawPoint != null ? BurrowReason.UNDER_FIRE_WITHDRAW
                    : BurrowReason.CONTAIN_POINT_MOVED);
            return;
        }

        setUnready(6);
        unit.move(hold);
    }

    @Override
    protected void rally() {
        if (unit.isBurrowed()) {
            this.setUnready();
            unburrowAndReset(BurrowReason.RALLY);
            return;
        }
        super.rally();
    }

    @Override
    protected void scout() {
        if (unit.isBurrowed()) {
            this.setUnready();
            unburrowAndReset(BurrowReason.SCOUT);
            return;
        }
        super.scout();
    }

    @Override
    public boolean canWithdrawNow() {
        return canWithdraw(role, unit.isBurrowed(), unit.canUnburrow());
    }

    /**
     * Whether a Lurker leaves fire it cannot answer by unburrowing: it is containing, burrowed and able to unburrow.
     *
     * @param role the Lurker's role
     * @param burrowed true when it is burrowed
     * @param canUnburrow true when it can unburrow now
     * @return true when it withdraws
     */
    public static boolean canWithdraw(UnitRole role, boolean burrowed, boolean canUnburrow) {
        return role == UnitRole.CONTAIN && burrowed && canUnburrow;
    }

    @Override
    public boolean canStepOutNow() {
        return super.canStepOutNow() && !holdsWithdrawalNow();
    }

    @Override
    public void evade(Position point, int frame) {
        if (unit.isBurrowed()) {
            withdrawPoint = point;
            withdrawFrame = frame;
        }
        super.evade(point, frame);
    }

    /**
     * Sets how many zones of the shooters that outrange the Lurker cover it, which the withdrawal row records.
     *
     * @param count the number of covering zones, 0 when the hit was not attributed to a shooter
     */
    public void setWithdrawZoneCount(int count) {
        this.withdrawZoneCount = count;
    }

    @Override
    protected void issueEvade() {
        if (unit.isBurrowed()) {
            unburrowAndReset(BurrowReason.UNDER_FIRE_WITHDRAW);
            setUnready(OUTRANGED_EVADE_FRAMES);
            return;
        }
        super.issueEvade();
    }

    @Override
    public boolean hasEnemyWithinReach(int margin) {
        return findClosestGroundEnemyWithin(weaponRange(unit) + margin) != null;
    }

    /**
     * Finds the closest enemy the Lurker's spines could hit: a detected ground unit or a hostile building within its
     * ground range. A flying unit never counts, its range against it is none.
     *
     * @return the enemy, or null when there is none
     */
    protected Unit findClosestGroundEnemyInRange() {
        return findClosestGroundEnemyWithin(weaponRange(unit));
    }

    private Unit findClosestGroundEnemyWithin(int range) {
        List<Unit> candidates = new ArrayList<>(game.getUnitsInRadius(unit.getPosition(), range + QUERY_PADDING));
        return closestInRange(candidates, this::isGroundTargetCandidate, unit::getDistance, range);
    }

    private boolean isGroundTargetCandidate(Unit enemy) {
        UnitType enemyType = enemy.getType();
        return isGroundTarget(enemy.isFlying(), enemy.isDetected())
                && enemy.getPlayer().isEnemy(game.self())
                && enemy.isTargetable()
                && !Filter.isLowPriorityCombatTarget(enemyType)
                && (!enemyType.isBuilding() || Filter.isHostileBuilding(enemyType));
    }

    /**
     * Whether an enemy can be a Lurker's in-range pick: it is on the ground and the Lurker detects it.
     *
     * @param flying true when the enemy is flying
     * @param detected true when the enemy is detected
     * @return true for a ground unit that can be hit
     */
    static boolean isGroundTarget(boolean flying, boolean detected) {
        return !flying && detected;
    }

    /**
     * Picks the eligible enemy closest to the unit within a range.
     *
     * @param enemies the enemies to pick from
     * @param eligible the test an enemy must pass
     * @param distance distance from the unit to an enemy
     * @param range the range, in pixels
     * @param <E> the enemy type
     * @return the closest eligible enemy within the range, or null when there is none
     */
    static <E> E closestInRange(Collection<E> enemies, Predicate<E> eligible, ToDoubleFunction<E> distance,
                                double range) {
        E closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (E enemy : enemies) {
            if (!eligible.test(enemy)) {
                continue;
            }
            double enemyDistance = distance.applyAsDouble(enemy);
            if (enemyDistance <= range && enemyDistance < closestDistance) {
                closest = enemy;
                closestDistance = enemyDistance;
            }
        }
        return closest;
    }

    /**
     * Whether a burrowed containing Lurker stays burrowed when its contain point moves: the new point is close and
     * lies outside every fixed-fire zone, so walking to it would only unburrow the Lurker for nothing.
     *
     * @param distanceToPoint pixels from the Lurker to its new contain point
     * @param pointInFixedFire true when the point lies within reach of a fixed-fire zone
     * @param standingInFixedFire true when the Lurker stands within reach of a fixed-fire zone
     * @return true when the Lurker keeps its burrow
     */
    static boolean staysBurrowed(double distanceToPoint, boolean pointInFixedFire, boolean standingInFixedFire) {
        return distanceToPoint < KEEP_BURROWED_DISTANCE && !pointInFixedFire && !standingInFixedFire;
    }

    /**
     * Picks the target a fighting Lurker works on: its assigned target while that is in range, else the closest
     * ground enemy in range, else the assigned target.
     *
     * @param assigned the target the squad assigned, or null
     * @param assignedInRange true when the assigned target is in range
     * @param closestInRange the closest ground enemy in range, or null
     * @param <E> the enemy type
     * @return the target to work on
     */
    static <E> E pickFightTarget(E assigned, boolean assignedInRange, E closestInRange) {
        if (assignedInRange || closestInRange == null) {
            return assigned;
        }
        return closestInRange;
    }

    /**
     * Whether a fighting Lurker burrows now: it is unburrowed, able to burrow, and the assigned target or any other
     * ground enemy is in range.
     *
     * @param burrowed true when the Lurker is burrowed
     * @param canBurrow true when it can burrow
     * @param enemyInRange true when its target or any ground enemy is in range
     * @return true when it burrows
     */
    static boolean burrowsInFight(boolean burrowed, boolean canBurrow, boolean enemyInRange) {
        return !burrowed && canBurrow && enemyInRange;
    }

    /**
     * Whether a withdrawal still holds: it holds for the full {@link #WITHDRAW_HOLD_FRAMES} frames from the frame it
     * was made, whatever the contain point does meanwhile.
     *
     * @param withdrawFrame frame of the withdrawal, -1 for none
     * @param now current frame
     * @return true while the Lurker holds its withdrawal point
     */
    static boolean withdrawHolds(int withdrawFrame, int now) {
        return withdrawFrame >= 0 && now - withdrawFrame <= WITHDRAW_HOLD_FRAMES;
    }

    private boolean holdsWithdrawalNow() {
        return withdrawPoint != null && withdrawHolds(withdrawFrame, game.getFrameCount());
    }

    /**
     * Whether a containing Lurker answers a ground enemy in range ahead of walking to its hold point: an unburrowed
     * Lurker holding a withdrawal walks out of fire first, since burrowing where it was hit puts it back in the fire.
     *
     * @param enemyInRange true when a ground enemy is in range
     * @param burrowed true when the Lurker is burrowed
     * @param holdsWithdrawal true while a withdrawal holds
     * @return true when the Lurker attacks, burrows or closes on the enemy
     */
    static boolean answersEnemyInRange(boolean enemyInRange, boolean burrowed, boolean holdsWithdrawal) {
        return enemyInRange && (burrowed || !holdsWithdrawal);
    }

    private Position holdPoint() {
        if (holdsWithdrawalNow()) {
            return withdrawPoint;
        }
        withdrawPoint = null;
        return containPosition;
    }

    private boolean isInFixedFire(Position point) {
        for (StaticDefenseZone zone : fixedFireZones) {
            if (zone.covers(point, fixedFirePadding)) {
                return true;
            }
        }
        return false;
    }

    private void burrow(BurrowReason reason) {
        if (unit.getOrder() == Order.Burrowing) {
            return;
        }
        unit.burrow();
        log(true, reason);
    }

    private void unburrowAndReset(BurrowReason reason) {
        if (unit.canUnburrow()) {
            unit.unburrow();
            log(false, reason);
        }
        resetCounters();
    }

    /**
     * Whether a burrow command is logged: the first one, or one that flips the state last commanded.
     *
     * @param lastCommanded the state last commanded, or null for none
     * @param next the state commanded now, true for burrow
     * @return true when the command is a state change
     */
    static boolean changesBurrowState(Boolean lastCommanded, boolean next) {
        return lastCommanded == null || lastCommanded != next;
    }

    private void log(boolean burrow, BurrowReason reason) {
        if (!changesBurrowState(commandedBurrow, burrow)) {
            return;
        }
        commandedBurrow = burrow;
        boolean withdrawing = reason == BurrowReason.UNDER_FIRE_WITHDRAW && withdrawPoint != null;
        BurrowTelemetry.burrowCommand(BurrowCommand.builder()
                .frame(game.getFrameCount())
                .unitId(unitID)
                .burrow(burrow)
                .reason(reason)
                .role(role.name())
                .position(unit.getPosition())
                .hitPoints(unit.getHitPoints())
                .containPoint(containPosition)
                .withdrawPoint(withdrawing ? withdrawPoint : null)
                .withdrawZones(withdrawing ? withdrawZoneCount : -1)
                .build());
    }

    private void resetCounters() {
        targetOutOfRangeFrames = 0;
        noAttackFrames = 0;
    }

    private boolean hasNoValidFightTarget() {
        return fightTarget == null || !fightTarget.exists() || !fightTarget.isDetected();
    }
}
