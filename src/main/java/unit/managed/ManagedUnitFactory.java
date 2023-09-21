package unit.managed;

import bwapi.Game;
import bwapi.Unit;

public class ManagedUnitFactory {

    private Game game;

    public ManagedUnitFactory(Game game) {
        this.game = game;
    }

    public ManagedUnit create(Unit unit, UnitRole role) {
        switch(unit.getType()) {
            case Zerg_Mutalisk:
                return new Mutalisk(game, unit, role);
            default:
                return new ManagedUnit(game, unit, role);
        }
    }
}
