package unit.managed;

import bwapi.Game;
import bwapi.Unit;
import info.GameState;

public class Queen extends ManagedUnit{
    public Queen(Game game, Unit unit, UnitRole role, GameState gameState) {
        super(game, unit, role, gameState);
    }
}
