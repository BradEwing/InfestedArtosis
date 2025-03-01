package unit.managed;

import bwapi.Game;
import bwapi.Unit;

public class ManagedUnitFactory {

    private Game game;

    public ManagedUnitFactory(Game game) {
        this.game = game;
    }

    public ManagedUnit create(Unit unit, UnitRole role) {
        switch(unit.getType()) {
            case Zerg_Larva:
                return new Larva(game, unit, role);
            case Zerg_Drone:
                return new Drone(game, unit, role);
            case Zerg_Overlord:
                return new Overlord(game, unit, role);
            case Zerg_Zergling:
                return new Zergling(game, unit, role);
            case Zerg_Hydralisk:
                return new Hydralisk(game, unit, role);
            case Zerg_Mutalisk:
                return new Mutalisk(game, unit, role);
            case Zerg_Scourge:
                return new Scourge(game, unit, role);
            case Zerg_Queen:
                return new Queen(game, unit, role);
            case Zerg_Ultralisk:
                return new Ultralisk(game, unit, role);
            case Zerg_Guardian:
                return new Guardian(game, unit, role);
            case Zerg_Devourer:
                return new Devourer(game, unit, role);
            case Zerg_Lurker:
                return new Lurker(game, unit, role);
            case Zerg_Defiler:
                return new Defiler(game, unit, role);
            case Zerg_Broodling:
                return new Broodling(game, unit, role);
            case Zerg_Infested_Terran:
                return new InfestedTerran(game, unit, role);
            default:
                return new ManagedUnit(game, unit, role);
        }
    }
}
