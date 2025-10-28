package unit.managed;

import bwapi.Game;
import bwapi.Unit;
import info.map.GameMap;

public class Guardian extends ManagedUnit {
    public Guardian(Game game, Unit unit, UnitRole role, GameMap gameMap) {
        super(game, unit, role, gameMap);
    }
}
