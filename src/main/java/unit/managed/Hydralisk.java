package unit.managed;

import bwapi.Game;
import bwapi.Position;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.UpgradeType;
import bwapi.WeaponType;
import info.map.GameMap;
import macro.plan.PlanType;

public class Hydralisk extends ManagedUnit {
    private static final int UPGRADED_RANGE_BONUS = 32;
    private static final int MOVE_DISTANCE = 64;
    private static final int FIGHT_UNREADY_FRAMES = 11;
    private static final double KITE_RANGE_FACTOR = 0.9;
    private static final int RETREAT_ARRIVAL_DISTANCE = 24;

    public Hydralisk(Game game, Unit unit, UnitRole role, GameMap gameMap) {
        super(game, unit, role, gameMap);
    }

    @Override
    protected int weaponRange(Unit enemy) {
        boolean isEnemyAir = enemy.isFlying();
        WeaponType weapon = isEnemyAir ? unit.getType().airWeapon() : unit.getType().groundWeapon();
        int range = weapon.maxRange();
        if (this.game.self().getUpgradeLevel(UpgradeType.Grooved_Spines) > 0) {
            range += UPGRADED_RANGE_BONUS;
        }
        return range;
    }

    private boolean hasNoValidFightTarget() {
        if (fightTarget == null || !fightTarget.exists() || !fightTarget.isVisible()) {
            return true;
        }
        return false;
    }

    private void kiteFromTarget() {
        Position myPos = unit.getPosition();
        Position enemyPos = fightTarget.getPosition();

        double dx = myPos.x - enemyPos.x;
        double dy = myPos.y - enemyPos.y;
        double length = Math.sqrt(dx * dx + dy * dy);

        Position kitePosition = (length == 0) ?
                new Position(myPos.x + MOVE_DISTANCE, myPos.y) :
                new Position(
                        (int) (myPos.x + (dx / length) * MOVE_DISTANCE),
                        (int) (myPos.y + (dy / length) * MOVE_DISTANCE)
                );

        unit.move(kitePosition);
    }

    @Override
    protected void fight() {
        if (unit.isAttackFrame()) {
            return;
        }
        setUnready(FIGHT_UNREADY_FRAMES);

        if (hasNoValidFightTarget()) {
            handleNoTarget();
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
        double kiteThreshold = this.weaponRange(fightTarget) * KITE_RANGE_FACTOR;
        if (this.weaponRange(fightTarget) > enemyWeapon.maxRange()) {
            kiteThreshold = enemyWeapon.maxRange();
        }

        final boolean outsideKiteThreshold = distance >= kiteThreshold;

        if (fightTarget.getType().isBuilding()) {
            unit.attack(fightTarget);
            return;
        }

        if (cooldown == 0 || outsideKiteThreshold) {
            unit.attack(fightTarget);
            return;
        }

        kiteFromTarget();
    }

    @Override
    public void execute() {
        if (plan != null && plan.getType() == PlanType.UNIT && plan.getPlannedUnit() == UnitType.Zerg_Lurker) {
            if (this.role != UnitRole.MORPH) {
                this.setRole(UnitRole.MORPH);
            }
        }
        
        super.execute();
    }

    @Override
    public void setMovementTargetPosition(TilePosition tp) {
        if (plan != null && plan.getType() == PlanType.UNIT && plan.getPlannedUnit() == UnitType.Zerg_Lurker) {
            this.setRole(UnitRole.MORPH);
            return;
        }

        super.setMovementTargetPosition(tp);
    }

    @Override
    protected int getRetreatArrivalDistance() {
        return RETREAT_ARRIVAL_DISTANCE;
    }
}
