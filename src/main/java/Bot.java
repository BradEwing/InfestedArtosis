import bwapi.BWClient;
import bwapi.DefaultBWListener;
import bwapi.Game;
import bwapi.Race;
import bwapi.Unit;
import bwapi.UnitType;
import bwem.BWEM;
import info.GameState;
import info.InformationManager;
import learning.Decisions;
import learning.LearningManager;
import learning.OpponentRecord;
import macro.ProductionManager;
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
 * - * Only sweep info.map if we don't know where any enemies are
 * - * Avoid enemies that are attacking us
 */
public class Bot extends DefaultBWListener {
    private BWEM bwem;
    private BWClient bwClient;
    private Game game;

    private GameState gameState;

    private Debug debugMap;
    private LearningManager learningManager;
    private ProductionManager economyModule;
    private InformationManager informationManager;
    private UnitManager unitManager;

    private AutoObserver autoObserver;

    @Override
    public void onStart() {
        game = bwClient.getGame();

        // Load BWEM and analyze the info.map
        bwem = new BWEM(game);
        bwem.initialize();

        Race opponentRace = game.enemy().getRace();

        this.gameState = new GameState(game.self(), bwem);

        learningManager = new LearningManager(opponentRace, game.enemy().getName(), bwem);
        Decisions decisions = learningManager.getDecisions();
        gameState.onStart(decisions, opponentRace);
        OpponentRecord opponentRecord = learningManager.getOpponentRecord();
        informationManager = new InformationManager(bwem, game, gameState);
        debugMap = new Debug(bwem, game, decisions.getOpener(), opponentRecord, gameState);
        economyModule = new ProductionManager(game, gameState, decisions.getOpener().getBuildOrder()); // TODO: reverse
        unitManager = new UnitManager(game, informationManager, bwem, gameState);

        autoObserver = new AutoObserver(game, unitManager.getScoutManager());
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
        autoObserver.onFrame();
    }

    @Override
    public void onUnitHide(Unit unit) { informationManager.onUnitHide(unit); }

    @Override
    public void onUnitShow(Unit unit) {
        informationManager.onUnitShow(unit);
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
        economyModule.onUnitDestroy(unit);
        unitManager.onUnitDestroy(unit);
    }

    @Override
    public void onUnitRenegade(Unit unit) {
        informationManager.onUnitRenegade(unit);
        economyModule.onUnitRenegade(unit);
    }

    @Override
    public void onUnitMorph(Unit unit) {
        informationManager.onUnitMorph(unit);
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
