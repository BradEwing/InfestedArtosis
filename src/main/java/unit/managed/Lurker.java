package unit.managed;

import bwapi.Game;
import bwapi.Order;
import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import config.Config;
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
    /** Pixels from where it was hit within which a withdrawing Lurker is still in the hit cell. */
    static final int HIT_CELL_RADIUS = 96;
    /** Frames between two burrow refusals that are logged. */
    static final int REFUSAL_LOG_FRAMES = 96;
    /** Frames after losing hit points during which a Lurker counts as losing them. */
    static final int HURT_FRAMES = 32;
    /** Pixels from a Lurker within which a safe point is near enough to walk to under fire. */
    public static final int SAFE_POINT_NEAR_DISTANCE = 64;

    private List<StaticDefenseZone> fixedFireView = Collections.emptyList();
    private List<StaticDefenseZone> fixedFireZones = Collections.emptyList();
    private int fixedFirePadding;
    private Position withdrawPoint;
    private int withdrawFrame = -1;
    private int withdrawZoneCount;
    private Position withdrawOrigin;
    private List<StaticDefenseZone> fireZones = Collections.emptyList();
    private int firePadding;
    private Position safeFromHere;
    private Position safeFromHold;
    private int refusalLoggedFrame = -REFUSAL_LOG_FRAMES;
    private int allowedLoggedFrame = -REFUSAL_LOG_FRAMES;
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

    /**
     * Sets the ground it must not burrow on: every piece of enemy fire it cannot answer, and the nearest points
     * outside it.
     *
     * @param zones the zones of fixed fire that outrange it and the live hit marks
     * @param fixedZones the zones among them of fixed fire, which a hit mark alone does not make
     * @param padding pixels added to every zone's reach
     * @param safeFromHere the nearest point to where it stands that lies outside the zones, or null
     * @param safeFromHold the nearest point to its contain point that lies outside the zones, or null
     */
    public void setFireView(List<StaticDefenseZone> zones, List<StaticDefenseZone> fixedZones, int padding,
                            Position safeFromHere, Position safeFromHold) {
        this.fireZones = zones;
        this.fixedFireView = fixedZones;
        this.firePadding = padding;
        this.safeFromHere = safeFromHere;
        this.safeFromHold = safeFromHold;
    }

    /**
     * Whether the Lurker stands inside enemy fire it cannot answer, which it must not burrow in.
     *
     * @return true when a fire zone covers it
     */
    public boolean standsInFire() {
        return inFire(unit.getPosition());
    }

    /**
     * The point the Lurker walks to and holds now: its retreat hold point, else the withdrawal point while that
     * holds, else its contain point.
     *
     * @return the point, or null when it has none
     */
    public Position activeHoldPoint() {
        if (holdPosition != null) {
            return holdPosition;
        }
        return holdsWithdrawalNow() ? withdrawPoint : containPosition;
    }

    /**
     * Whether the point it walks to and holds lies inside enemy fire it cannot answer.
     *
     * @return true when it has such a point and a fire zone covers it
     */
    public boolean activeHoldInFire() {
        Position hold = activeHoldPoint();
        return hold != null && inFire(hold);
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
            burrow(BurrowReason.FIGHT_ENEMY_IN_RANGE, true);
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
        Position target = holdTarget(Config.lurkerFireAware, inFire(holdPosition), holdPosition, safeFromHold);
        switch (holdStep(unit.isBurrowed(), unit.getDistance(target))) {
            case UNBURROW:
                setUnready();
                unburrowAndReset(BurrowReason.RETREAT_HOLD);
                return;
            case MOVE:
                setUnready(HOLD_MOVE_FRAMES);
                unit.move(target);
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
        if (answersEnemyInRange(nearbyEnemy != null, unit.isBurrowed(), holdsWithdrawalNow() && inHitCellNow())) {
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

        Position plainHold = holdPoint();
        Position hold = holdTarget(Config.lurkerFireAware, inFire(plainHold), plainHold, safeFromHold);
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
            withdrawOrigin = unit.getPosition();
        }
        super.evade(point, frame);
    }

    /**
     * Sets how many zones of the shooters that outrange the Lurker cover where it stands, which the withdrawal row
     * records.
     *
     * @param count the number of covering zones, 0 when no known shooter covers it
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
        return role == UnitRole.CONTAIN && withdrawPoint != null
                && withdrawHolds(withdrawFrame, game.getFrameCount());
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

    /**
     * Whether a withdrawing Lurker is still in the cell it was hit in: within {@link #HIT_CELL_RADIUS} of where it
     * was hit, or inside enemy fire. Once it has walked out of the cell it burrows on an enemy in range.
     *
     * @param distanceFromHit pixels from where it was hit to where it stands
     * @param inFire true when a fire zone covers it
     * @return true while it is still in the hit cell
     */
    static boolean stillInHitCell(double distanceFromHit, boolean inFire) {
        return distanceFromHit <= HIT_CELL_RADIUS || inFire;
    }

    private boolean inHitCellNow() {
        if (!Config.lurkerFireAware || withdrawOrigin == null) {
            return true;
        }
        return stillInHitCell(unit.getDistance(withdrawOrigin), standsInFire());
    }

    /**
     * What a Lurker does when it is told to burrow where it stands. It refuses when the rule is on, the ground it
     * stands on is inside enemy fire it cannot answer and a safe point exists to walk to, except that it burrows and
     * fires when a ground enemy is within its weapon range and only a hit mark covers it, or when it lost hit points
     * within {@link #HURT_FRAMES} and the safe point lies more than {@link #SAFE_POINT_NEAR_DISTANCE} away. With no
     * safe point it burrows where it is.
     *
     * @param fireAware true when the rule is on
     * @param standingInFire true when a fire zone covers the Lurker
     * @param standingInFixedFire true when a zone of fixed fire, a structure or sieged tank, covers the Lurker
     * @param hasSafePoint true when a point outside the fire is known
     * @param enemyInRange true when a ground enemy is within the Lurker's weapon range
     * @param hurtRecently true when the Lurker lost hit points within {@link #HURT_FRAMES}
     * @param safePointDistance pixels from the Lurker to the safe point
     * @return the call
     */
    static BurrowCall burrowCall(boolean fireAware, boolean standingInFire, boolean standingInFixedFire,
                                 boolean hasSafePoint, boolean enemyInRange, boolean hurtRecently,
                                 double safePointDistance) {
        if (!fireAware || !standingInFire || !hasSafePoint) {
            return BurrowCall.BURROW;
        }
        if (enemyInRange && !standingInFixedFire) {
            return BurrowCall.BURROW_ENEMY_IN_RANGE;
        }
        if (hurtRecently && safePointDistance > SAFE_POINT_NEAR_DISTANCE) {
            return BurrowCall.BURROW_LOSING_HP;
        }
        return BurrowCall.REFUSE;
    }

    enum BurrowCall {
        BURROW,
        BURROW_ENEMY_IN_RANGE,
        BURROW_LOSING_HP,
        REFUSE
    }

    /**
     * The point a containing Lurker walks to and burrows at: the safe point nearest its hold point when the rule is
     * on, the hold point lies inside enemy fire and a safe point exists, else the hold point.
     *
     * @param fireAware true when the rule is on
     * @param holdInFire true when a fire zone covers the hold point
     * @param hold the hold point
     * @param safe the nearest point to the hold point outside the fire, or null
     * @return the point to walk to
     */
    static Position holdTarget(boolean fireAware, boolean holdInFire, Position hold, Position safe) {
        return fireAware && holdInFire && safe != null ? safe : hold;
    }

    /**
     * Whether a burrow refusal is written to the burrow log: the first, or one {@link #REFUSAL_LOG_FRAMES} after
     * the last logged.
     *
     * @param lastLoggedFrame frame of the last logged refusal
     * @param now current frame
     * @return true when the refusal is logged
     */
    static boolean logsRefusal(int lastLoggedFrame, int now) {
        return now - lastLoggedFrame >= REFUSAL_LOG_FRAMES;
    }

    private boolean inFire(Position point) {
        for (StaticDefenseZone zone : fireZones) {
            if (zone.covers(point, firePadding)) {
                return true;
            }
        }
        return false;
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
        burrow(reason, findClosestGroundEnemyInRange() != null);
    }

    private void burrow(BurrowReason reason, boolean enemyInRange) {
        if (unit.getOrder() == Order.Burrowing) {
            return;
        }
        int now = game.getFrameCount();
        BurrowCall call = burrowCall(Config.lurkerFireAware, standsInFire(), standsInFixedFireView(),
                safeFromHere != null, enemyInRange, wasHitSince(now - HURT_FRAMES),
                safeFromHere == null ? 0 : unit.getDistance(safeFromHere));
        if (call == BurrowCall.REFUSE) {
            unit.move(safeFromHere);
            if (logsRefusal(refusalLoggedFrame, now)) {
                refusalLoggedFrame = now;
                logUnderFire(BurrowReason.BURROW_REFUSED_UNDER_FIRE, now);
            }
            return;
        }
        if ((call == BurrowCall.BURROW_ENEMY_IN_RANGE || call == BurrowCall.BURROW_LOSING_HP)
                && logsRefusal(allowedLoggedFrame, now)) {
            allowedLoggedFrame = now;
            logUnderFire(call == BurrowCall.BURROW_ENEMY_IN_RANGE
                    ? BurrowReason.BURROW_UNDER_FIRE_ALLOWED_ENEMY_IN_RANGE
                    : BurrowReason.BURROW_UNDER_FIRE_ALLOWED_LOSING_HP, now);
        }
        unit.burrow();
        log(true, reason);
    }

    private boolean standsInFixedFireView() {
        for (StaticDefenseZone zone : fixedFireView) {
            if (zone.covers(unit.getPosition(), firePadding)) {
                return true;
            }
        }
        return false;
    }

    private void logUnderFire(BurrowReason reason, int now) {
        BurrowTelemetry.burrowCommand(BurrowCommand.builder()
                .frame(now)
                .unitId(unitID)
                .burrow(true)
                .reason(reason)
                .role(role.name())
                .position(unit.getPosition())
                .hitPoints(unit.getHitPoints())
                .containPoint(containPosition)
                .build());
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
