package unit.managed;

import bwapi.Game;
import bwapi.Unit;

public class Overlord extends ManagedUnit {
    public Overlord(Game game, Unit unit, UnitRole role) {
        super(game, unit, role);

        this.setCanFight(false);
    }
}
