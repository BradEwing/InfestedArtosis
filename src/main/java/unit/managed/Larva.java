package unit.managed;

import bwapi.Game;
import bwapi.Unit;

public class Larva extends ManagedUnit{
    public Larva(Game game, Unit unit, UnitRole role) {
        super(game, unit, role);
        this.canFight = false;
    }
}
