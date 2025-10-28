package unit.managed;

import bwapi.Game;
import bwapi.Unit;
import info.GameState;

public class Overlord extends ManagedUnit {
    public Overlord(Game game, Unit unit, UnitRole role, GameState gameState) {
        super(game, unit, role, gameState);

        this.setCanFight(false);
    }
}
