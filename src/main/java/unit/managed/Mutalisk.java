package unit.managed;

import bwapi.Game;
import bwapi.Unit;

public class Mutalisk extends ManagedUnit {
    public Mutalisk(Game game, Unit unit, UnitRole role) {
        super(game, unit, role);
    }

    @Override
    protected void fight() {
        if (!this.canFightBack(this.fightTarget)) {
            Unit dangerousEnemy = this.findThreateningEnemy();
            if (dangerousEnemy != null) {
                this.fightTarget = dangerousEnemy;
            }
        }
        super.fight();
    }
}
