package unit.managed;

import bwapi.Game;
import bwapi.Position;
import bwapi.Unit;
import bwapi.UpgradeType;
import bwapi.WeaponType;

public class Hydralisk extends ManagedUnit {
    public Hydralisk(Game game, Unit unit, UnitRole role) {
        super(game, unit, role);
    }

    @Override
    protected int weaponRange(Unit enemy) {
        boolean isEnemyAir = enemy.isFlying();
        WeaponType weapon = isEnemyAir ? unit.getType().airWeapon() : unit.getType().groundWeapon();
        int range = weapon.maxRange();
        if (this.game.self().getUpgradeLevel(UpgradeType.Grooved_Spines) > 0) {
            range += 32;
        }
        return range;
    }

    @Override
    protected void fight() {
        if (unit.isAttackFrame()) {
            return;
        }
        setUnready(11);

        if (fightTarget != null) {
            if (!fightTarget.exists() || !fightTarget.isVisible()) {
                return;
            }

            boolean isEnemyAir = fightTarget.isFlying();
            WeaponType weapon = isEnemyAir ? unit.getType().airWeapon() : unit.getType().groundWeapon();
            WeaponType enemyWeapon = unit.isFlying() ? fightTarget.getType().airWeapon() : fightTarget.getType().groundWeapon();

            setUnready(weapon.damageCooldown());

            int cooldown = isEnemyAir ? unit.getAirWeaponCooldown() : unit.getGroundWeaponCooldown();

            Position enemyPos = fightTarget.getPosition();
            Position myPos = unit.getPosition();
            double distance = myPos.getDistance(enemyPos);
            double kiteThreshold = this.weaponRange(fightTarget) * 0.9;
            if (this.weaponRange(fightTarget) > enemyWeapon.maxRange()) {
                kiteThreshold = enemyWeapon.maxRange();
            }

            final boolean outsideKiteThreshold = distance >= kiteThreshold;

            if (fightTarget.getType().isBuilding()) {
                unit.attack(fightTarget);
                return;
            }

            if (cooldown == 0 || outsideKiteThreshold) {
                if (outsideKiteThreshold) {
                    unit.attack(fightTarget);
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
            return;
        }

        if (movementTargetPosition != null) {
            unit.move(movementTargetPosition.toPosition());
            return;
        }

        role = UnitRole.IDLE;
    }
}
