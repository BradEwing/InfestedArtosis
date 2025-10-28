package unit.managed;

import bwapi.Game;
import bwapi.Unit;
import info.GameState;

public class Larva extends ManagedUnit{
    public Larva(Game game, Unit unit, UnitRole role, GameState gameState) {
        super(game, unit, role, gameState);
        this.canFight = false;
    }
}
