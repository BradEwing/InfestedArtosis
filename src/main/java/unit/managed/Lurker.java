package unit.managed;

import bwapi.Game;
import bwapi.Position;
import bwapi.Unit;
import info.map.GameMap;
import lombok.Getter;

public class Lurker extends ManagedUnit {
    private int targetOutOfRangeFrames = 0;
    private int noAttackFrames = 0;
    private static final int MAX_TARGET_OUT_OF_RANGE_FRAMES = 20;
    private static final int MAX_NO_ATTACK_FRAMES = 50;

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

    @Override
    protected void fight() {
        if (unit.isAttackFrame()) {
            return;
        }
        setUnready(11);

        if (hasNoValidFightTarget()) {
            handleNoTarget();
            unburrowAndReset();
            return;
        }

        int range = weaponRange(fightTarget);
        double distance = unit.getDistance(fightTarget.getPosition());
        if (unit.isBurrowed()) {
            // Check if target is out of range
            if (distance > range) {
                targetOutOfRangeFrames++;
                if (targetOutOfRangeFrames >= MAX_TARGET_OUT_OF_RANGE_FRAMES) {
                    unburrowAndReset();
                    return;
                }
            } else {
                targetOutOfRangeFrames = 0;
            }

            // Find a closer enemy unit
            Unit closestEnemy = findClosestEnemyInRange();
            if (closestEnemy != null && closestEnemy != fightTarget) {
                fightTarget = closestEnemy;
                noAttackFrames = 0;
            }

            // Check if attacking
            int cooldown = unit.getGroundWeaponCooldown();
            if (cooldown == 0) {
                noAttackFrames++;
                if (noAttackFrames >= MAX_NO_ATTACK_FRAMES) {
                    unburrowAndReset();
                    return;
                }
            }
            noAttackFrames = 0;
            unit.attack(fightTarget);
            return;
        }

        // Not burrowed: move or burrow
        if (distance <= range && unit.canBurrow()) {
            unit.burrow();
            resetCounters();
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
        if (unit.isBurrowed() && !unit.isUnderAttack()) {
            return;
        }

        unit.burrow();
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
                unburrowAndReset();
                return;
            case MOVE:
                setUnready(HOLD_MOVE_FRAMES);
                unit.move(holdPosition);
                return;
            case BURROW:
                setUnready();
                if (unit.canBurrow()) {
                    unit.burrow();
                }
                resetCounters();
                return;
            default:
                setUnready();
                Unit nearbyEnemy = findClosestEnemyInRange();
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

        Unit nearbyEnemy = findClosestEnemyInRange();
        if (nearbyEnemy != null) {
            setUnready(11);
            if (unit.isBurrowed()) {
                unit.attack(nearbyEnemy);
            } else if (unit.getDistance(nearbyEnemy) <= weaponRange(nearbyEnemy) && unit.canBurrow()) {
                unit.burrow();
            } else {
                unit.move(nearbyEnemy.getPosition());
            }
            return;
        }

        if (unit.getDistance(containPosition) < 24) {
            setUnready(11);
            if (!unit.isBurrowed() && unit.canBurrow()) {
                unit.burrow();
            }
            return;
        }

        setUnready(6);
        if (unit.isBurrowed()) {
            unburrowAndReset();
            return;
        }
        unit.move(containPosition);
    }

    @Override
    protected void rally() {
        if (unit.isBurrowed()) {
            this.setUnready();
            unburrowAndReset();
            return;
        }
        super.rally();
    }

    @Override
    protected void scout() {
        if (unit.isBurrowed()) {
            this.setUnready();
            unburrowAndReset();
            return;
        }
        super.scout();
    }

    private void unburrowAndReset() {
        if (unit.canUnburrow()) {
            unit.unburrow();
        }
        resetCounters();
    }

    private void resetCounters() {
        targetOutOfRangeFrames = 0;
        noAttackFrames = 0;
    }

    private boolean hasNoValidFightTarget() {
        return fightTarget == null || !fightTarget.exists() || !fightTarget.isDetected();
    }
}