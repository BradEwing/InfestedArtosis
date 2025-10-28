package unit.managed;

import bwapi.Game;
import bwapi.Unit;
import info.map.GameMap;

public class Overlord extends ManagedUnit {
    public Overlord(Game game, Unit unit, UnitRole role, GameMap gameMap) {
        super(game, unit, role, gameMap);

        this.setCanFight(false);
    }
}
