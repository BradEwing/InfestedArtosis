package unit.managed;

import bwapi.Game;
import bwapi.Unit;
import info.map.GameMap;

public class ManagedUnitFactory {

    private Game game;
    private GameMap gameMap;

    public ManagedUnitFactory(Game game) {
        this.game = game;
    }

    public ManagedUnitFactory(Game game, GameMap gameMap) {
        this.game = game;
        this.gameMap = gameMap;
    }

    public ManagedUnit create(Unit unit, UnitRole role) {
        ManagedUnit managedUnit;
        switch(unit.getType()) {
            case Zerg_Larva:
                managedUnit = new Larva(game, unit, role, gameMap);
                break;
            case Zerg_Drone:
                managedUnit = new Drone(game, unit, role, gameMap);
                break;
            case Zerg_Overlord:
                managedUnit = new Overlord(game, unit, role, gameMap);
                break;
            case Zerg_Zergling:
                managedUnit = new Zergling(game, unit, role, gameMap);
                break;
            case Zerg_Hydralisk:
                managedUnit = new Hydralisk(game, unit, role, gameMap);
                break;
            case Zerg_Mutalisk:
                managedUnit = new Mutalisk(game, unit, role, gameMap);
                break;
            case Zerg_Scourge:
                managedUnit = new Scourge(game, unit, role, gameMap);
                break;
            case Zerg_Queen:
                managedUnit = new Queen(game, unit, role, gameMap);
                break;
            case Zerg_Ultralisk:
                managedUnit = new Ultralisk(game, unit, role, gameMap);
                break;
            case Zerg_Guardian:
                managedUnit = new Guardian(game, unit, role, gameMap);
                break;
            case Zerg_Devourer:
                managedUnit = new Devourer(game, unit, role, gameMap);
                break;
            case Zerg_Lurker:
                managedUnit = new Lurker(game, unit, role, gameMap);
                break;
            case Zerg_Defiler:
                managedUnit = new Defiler(game, unit, role, gameMap);
                break;
            case Zerg_Broodling:
                managedUnit = new Broodling(game, unit, role, gameMap);
                break;
            case Zerg_Infested_Terran:
                managedUnit = new InfestedTerran(game, unit, role, gameMap);
                break;
            default:
                managedUnit = new ManagedUnit(game, unit, role, gameMap);
                break;
        }
        
        return managedUnit;
    }
}
