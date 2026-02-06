package unit.managed;

import bwapi.Game;
import bwapi.Unit;
import info.map.GameMap;

public class Lurker extends ManagedUnit {
    private static final int FIGHT_UNREADY_FRAMES = 11;
    private static final int MAX_TARGET_OUT_OF_RANGE_FRAMES = 20;
    private static final int MAX_NO_ATTACK_FRAMES = 50;

    private int targetOutOfRangeFrames = 0;
    private int noAttackFrames = 0;

    public Lurker(Game game, Unit unit, UnitRole role, GameMap gameMap) {
        super(game, unit, role, gameMap);
    }

    @Override
    protected void fight() {
        if (unit.isAttackFrame()) {
            return;
        }
        setUnready(FIGHT_UNREADY_FRAMES);

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

    @Override
    protected void retreat() {
        this.setUnready();
        this.setRole(UnitRole.FIGHT);
        if (unit.isBurrowed() && !unit.isUnderAttack()) {
            return;
        }

        unit.burrow();
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