package unit.managed;

import bwapi.Game;
import bwapi.Unit;
import info.GameState;

public class Guardian extends ManagedUnit {
    public Guardian(Game game, Unit unit, UnitRole role, GameState gameState) {
        super(game, unit, role, gameState);
    }
}
