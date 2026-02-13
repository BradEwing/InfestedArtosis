package unit.managed;

import bwapi.Game;
import bwapi.Unit;
import info.map.GameMap;
import util.Filter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Overlord behavior: avoids air threats while scouting/retreating and cannot fight.
 */
public class Overlord extends ManagedUnit {
    public Overlord(Game game, Unit unit, UnitRole role, GameMap gameMap) {
        super(game, unit, role, gameMap);
        this.setCanFight(false);
    }

    @Override
    protected List<Unit> getEnemiesInRadius(int currentX, int currentY) {
        return game.getUnitsInRadius(currentX, currentY, 192)
                .stream()
                .filter(u -> u.getPlayer() != game.self())
                .filter(u -> Filter.isAirThreat(u.getType()))
                .collect(Collectors.toList());
    }
}
