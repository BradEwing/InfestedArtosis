import bwapi.BWClient;
import bwapi.DefaultBWListener;
import bwapi.Game;
import bwapi.Unit;
import bwapi.UnitType;
import bwem.BWEM;
import learning.LearningManager;
import learning.OpponentRecord;
import learning.StrategyRecord;
import state.GameState;
import info.InformationManager;
import macro.ProductionManager;
import strategy.Strategy;
import unit.UnitManager;

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

    private GameState gameState = new GameState();

    // TODO: Can I implement these classes as listeners and register them here? Cleans up Bot class!
    private Debug debugMap;
    private LearningManager learningManager;
    private ProductionManager economyModule;
    private InformationManager informationManager;
    private UnitManager unitManager;

    @Override
    public void onStart() {
        game = bwClient.getGame();

        // Load BWEM and analyze the map
        bwem = new BWEM(game);
        bwem.initialize();

        learningManager = new LearningManager(game.enemy().getRace().toString(), game.enemy().getName(), bwem);
        Strategy strategy = learningManager.getDeterminedStrategy();
        OpponentRecord opponentRecord = learningManager.getOpponentRecord();
        informationManager = new InformationManager(bwem, game, gameState);
        debugMap = new Debug(bwem, game, strategy, opponentRecord);
        economyModule = new ProductionManager(game, bwem, gameState, strategy.getBuildOrder()); // TODO: reverse
        unitManager = new UnitManager(game, informationManager, bwem, gameState);
    }



    @Override
    public void onFrame() {
        if (bwem == null) {
            System.out.print("bwem is null\n");
            return;
        }
        informationManager.onFrame();
        economyModule.onFrame();
        unitManager.onFrame();

        debugMap.onFrame();
    }

    @Override
    public void onUnitShow(Unit unit) {
        unitManager.onUnitShow(unit);
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
        if (!unit.getType().isResourceContainer() && unit.getPlayer() != game.self()) {
            return;
        }

        economyModule.onUnitDestroy(unit);
        unitManager.onUnitDestroy(unit);
    }

    @Override
    public void onUnitRenegade(Unit unit) { economyModule.onUnitRenegade(unit); }

    @Override
    public void onUnitMorph(Unit unit) {
        economyModule.onUnitMorph(unit);
        unitManager.onUnitMorph(unit);
    }

    @Override
    public void onEnd(boolean isWinner) {
        learningManager.onEnd(isWinner);
    }

    public static void main(String[] args) {
        Bot bot = new Bot();
        bot.bwClient = new BWClient(bot);
        bot.bwClient.startGame();
    }
}
