package unit.managed;

import bwapi.Game;
import bwapi.Unit;
import info.GameState;

public class ManagedUnitFactory {

    private Game game;
    private GameState gameState;

    public ManagedUnitFactory(Game game) {
        this.game = game;
    }

    public ManagedUnitFactory(Game game, GameState gameState) {
        this.game = game;
        this.gameState = gameState;
    }

    public ManagedUnit create(Unit unit, UnitRole role) {
        ManagedUnit managedUnit;
        switch(unit.getType()) {
            case Zerg_Larva:
                managedUnit = new Larva(game, unit, role, gameState);
                break;
            case Zerg_Drone:
                managedUnit = new Drone(game, unit, role, gameState);
                break;
            case Zerg_Overlord:
                managedUnit = new Overlord(game, unit, role, gameState);
                break;
            case Zerg_Zergling:
                managedUnit = new Zergling(game, unit, role, gameState);
                break;
            case Zerg_Hydralisk:
                managedUnit = new Hydralisk(game, unit, role, gameState);
                break;
            case Zerg_Mutalisk:
                managedUnit = new Mutalisk(game, unit, role, gameState);
                break;
            case Zerg_Scourge:
                managedUnit = new Scourge(game, unit, role, gameState);
                break;
            case Zerg_Queen:
                managedUnit = new Queen(game, unit, role, gameState);
                break;
            case Zerg_Ultralisk:
                managedUnit = new Ultralisk(game, unit, role, gameState);
                break;
            case Zerg_Guardian:
                managedUnit = new Guardian(game, unit, role, gameState);
                break;
            case Zerg_Devourer:
                managedUnit = new Devourer(game, unit, role, gameState);
                break;
            case Zerg_Lurker:
                managedUnit = new Lurker(game, unit, role, gameState);
                break;
            case Zerg_Defiler:
                managedUnit = new Defiler(game, unit, role, gameState);
                break;
            case Zerg_Broodling:
                managedUnit = new Broodling(game, unit, role, gameState);
                break;
            case Zerg_Infested_Terran:
                managedUnit = new InfestedTerran(game, unit, role, gameState);
                break;
            default:
                managedUnit = new ManagedUnit(game, unit, role, gameState);
                break;
        }
        
        return managedUnit;
    }
}
