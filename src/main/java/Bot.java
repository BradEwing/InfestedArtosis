import bwapi.BWClient;
import bwapi.DefaultBWListener;
import bwapi.Game;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwem.BWEM;
import info.InformationManager;
import unit.ManagedUnit;
import unit.UnitManager;
import unit.UnitRole;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * TODO High Level:
 *
 * - Economy Manager
 * - * Plan extractors with hatch
 * - * Bug: drones assigned to build try in unreachable locations
 * - * Bug: some drones are idling
 * - Unit manager
 * - * Potentially refactor scouting code into unit manager
 * - * Define role enums
 * - Smarter scouting:
 * - * Only sweep map if we don't know where any enemies are
 * - * Avoid enemies that are attacking us
 */
public class Bot extends DefaultBWListener {
    private BWEM bwem;
    private BWClient bwClient;
    private Game game;

    // TODO: Can I implement these classes as listeners and register them here? Cleans up Bot class!
    private DebugMap debugMap;
    private EconomyModule economyModule;
    private InformationManager informationManager;
    private UnitManager unitManager;

    @Override
    public void onStart() {
        game = bwClient.getGame();

        // Load BWEM and analyze the map
        bwem = new BWEM(game);
        bwem.initialize();

        informationManager = new InformationManager(bwem, game);
        debugMap = new DebugMap(bwem, game);
        economyModule = new EconomyModule(game, bwem); // TODO: reverse
        unitManager = new UnitManager(game, informationManager);
    }



    @Override
    public void onFrame() {
        informationManager.onFrame();
        economyModule.onFrame();
        unitManager.onFrame();

        debugMap.onFrame();
    }

    @Override
    public void onUnitComplete(Unit unit) {
        if (unit.getPlayer() != game.self()) {
            return;
        }
        if (unit.getType() == UnitType.Zerg_Larva) {
            return;
        }

        informationManager.onUnitComplete(unit);
        economyModule.onUnitComplete(unit);
        unitManager.onUnitComplete(unit);
    }

    @Override
    public void onUnitDestroy(Unit unit) {
        informationManager.onUnitDestroy(unit);
        if (unit.getPlayer() != game.self()) {
            return;
        }

        economyModule.onUnitDestroy(unit);
        unitManager.onUnitDestroy(unit);
    }

    public static void main(String[] args) {
        Bot bot = new Bot();
        bot.bwClient = new BWClient(bot);
        bot.bwClient.startGame();
    }
}
