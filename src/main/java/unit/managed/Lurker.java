package unit.managed;

import bwapi.Game;
import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import info.map.GameMap;
import telemetry.BurrowReason;
import telemetry.BurrowTelemetry;
import util.Filter;
import util.StaticDefenseZone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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

    private List<StaticDefenseZone> fixedFireZones = Collections.emptyList();
    private int fixedFirePadding;
    private Position withdrawPoint;
    private Position withdrawnFrom;
    private int withdrawFrame = -1;

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

        Unit inRange = findClosestGroundEnemyInRange();
        if (inRange != null) {
            if (inRange != fightTarget) {
                fightTarget = inRange;
                resetCounters();
            }
            if (!unit.isBurrowed() && unit.canBurrow()) {
                burrow(BurrowReason.FIGHT_ENEMY_IN_RANGE);
                resetCounters();
                return;
            }
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

    @Override
    protected void retreat() {
        this.setUnready();
        this.setRole(UnitRole.FIGHT);
        if (unit.isBurrowed() && !unit.isUnderAttack()) {
            return;
        }

        if (!unit.isBurrowed()) {
            log(true, BurrowReason.RETREAT);
        }
        unit.burrow();
    }

    @Override
    protected void contain() {
        if (containPosition == null) {
            role = UnitRole.IDLE;
            return;
        }

        Unit nearbyEnemy = findClosestGroundEnemyInRange();
        if (nearbyEnemy != null) {
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
            if (staysBurrowed(distance, isInFixedFire(hold))) {
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
        return role == UnitRole.CONTAIN && unit.isBurrowed() && unit.canUnburrow();
    }

    @Override
    public void evade(Position point, int frame) {
        withdrawPoint = point;
        withdrawnFrom = containPosition;
        withdrawFrame = frame;
        super.evade(point, frame);
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
        List<Unit> candidates = new ArrayList<>(game.getUnitsInRadius(unit.getPosition(), range));
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
     * @return true when the Lurker keeps its burrow
     */
    static boolean staysBurrowed(double distanceToPoint, boolean pointInFixedFire) {
        return distanceToPoint < KEEP_BURROWED_DISTANCE && !pointInFixedFire;
    }

    /**
     * Whether a withdrawal still holds: it was made from the contain point the Lurker has now, and fewer than
     * {@link #WITHDRAW_HOLD_FRAMES} frames have passed. A new contain point, which the squad draws outside the zones it
     * has learned, ends it.
     *
     * @param withdrawnFrom the contain point the Lurker withdrew from, or null
     * @param containPosition the Lurker's contain point now
     * @param withdrawFrame frame of the withdrawal, -1 for none
     * @param now current frame
     * @return true while the Lurker holds its withdrawal point
     */
    static boolean withdrawHolds(Position withdrawnFrom, Position containPosition, int withdrawFrame, int now) {
        return withdrawFrame >= 0 && now - withdrawFrame <= WITHDRAW_HOLD_FRAMES
                && Objects.equals(withdrawnFrom, containPosition);
    }

    private Position holdPoint() {
        if (withdrawPoint != null
                && withdrawHolds(withdrawnFrom, containPosition, withdrawFrame, game.getFrameCount())) {
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

    private void log(boolean burrow, BurrowReason reason) {
        BurrowTelemetry.burrowCommand(game.getFrameCount(), unitID, burrow, reason, role.name(), unit.getPosition(),
                unit.getHitPoints(), containPosition);
    }

    private void resetCounters() {
        targetOutOfRangeFrames = 0;
        noAttackFrames = 0;
    }

    private boolean hasNoValidFightTarget() {
        return fightTarget == null || !fightTarget.exists() || !fightTarget.isDetected();
    }
}
