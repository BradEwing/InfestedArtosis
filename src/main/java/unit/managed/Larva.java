package unit.managed;

import bwapi.Game;
import bwapi.Unit;
import info.map.GameMap;

public class Larva extends ManagedUnit{
    public Larva(Game game, Unit unit, UnitRole role, GameMap gameMap) {
        super(game, unit, role, gameMap);
        this.canFight = false;
    }
}
