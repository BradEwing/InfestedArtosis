package unit.managed;

import bwapi.Game;
import bwapi.Unit;

public class ManagedUnitFactory {

    private Game game;

    public ManagedUnitFactory(Game game) {
        this.game = game;
    }

    public ManagedUnit create(Unit unit, UnitRole role) {
        ManagedUnit managedUnit;
        switch(unit.getType()) {
            case Zerg_Larva:
                managedUnit = new Larva(game, unit, role);
                break;
            case Zerg_Drone:
                managedUnit = new Drone(game, unit, role);
                break;
            case Zerg_Overlord:
                managedUnit = new Overlord(game, unit, role);
                break;
            case Zerg_Zergling:
                managedUnit = new Zergling(game, unit, role);
                break;
            case Zerg_Hydralisk:
                managedUnit = new Hydralisk(game, unit, role);
                break;
            case Zerg_Mutalisk:
                managedUnit = new Mutalisk(game, unit, role);
                break;
            case Zerg_Scourge:
                managedUnit = new Scourge(game, unit, role);
                break;
            case Zerg_Queen:
                managedUnit = new Queen(game, unit, role);
                break;
            case Zerg_Ultralisk:
                managedUnit = new Ultralisk(game, unit, role);
                break;
            case Zerg_Guardian:
                managedUnit = new Guardian(game, unit, role);
                break;
            case Zerg_Devourer:
                managedUnit = new Devourer(game, unit, role);
                break;
            case Zerg_Lurker:
                managedUnit = new Lurker(game, unit, role);
                break;
            case Zerg_Defiler:
                managedUnit = new Defiler(game, unit, role);
                break;
            case Zerg_Broodling:
                managedUnit = new Broodling(game, unit, role);
                break;
            case Zerg_Infested_Terran:
                managedUnit = new InfestedTerran(game, unit, role);
                break;
            default:
                managedUnit = new ManagedUnit(game, unit, role);
                break;
        }
        return managedUnit;
    }
}
