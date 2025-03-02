package unit.managed;

import bwapi.Game;
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
}
