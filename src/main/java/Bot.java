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
import macro.plan.PlanManager;
import strategy.buildorder.BuildOrder;
import telemetry.PlanEventLogger;
import telemetry.PlanEvents;
import unit.UnitManager;

/**
 * Execution flow:
 * - LearningManager: analyze past match history to determine build order
 * - InformationManager: tracks game state
 * - ProductionManager: manages production of units, buildings, upgrades and research
 * - PlanManager: manages plans for units, buildings, upgrades and research
 * - UnitManager: manages units
 * - Debug: provides debug information
 */
public class Bot extends DefaultBWListener {
    private BWEM bwem;
    private BWClient bwClient;
    private Game game;

    private GameState gameState;

    private Debug debugMap;
    private LearningManager learningManager;
    private PlanManager planManager;
    private ProductionManager productionManager;
    private InformationManager informationManager;
    private UnitManager unitManager;

    private AutoObserver autoObserver;

    private PlanEventLogger planEventLogger;

    @Override
    public void onStart() {
        game = bwClient.getGame();

        // Load BWEM and analyze the map
        bwem = new BWEM(game);
        bwem.initialize();

        Race opponentRace = game.enemy().getRace();

        this.gameState = new GameState(game, bwem);

        learningManager = new LearningManager(gameState.getConfig(), game, bwem, gameState);
        Decisions decisions = learningManager.getDecisions();
        gameState.onStart(decisions, opponentRace);

        OpponentRecord opponentRecord = learningManager.getOpponentRecord();

        informationManager = new InformationManager(bwem, game, gameState, learningManager);
        productionManager = new ProductionManager(game, gameState, decisions.getOpener()); // TODO: reverse
        planManager = new PlanManager(game, gameState);
        unitManager = new UnitManager(game, informationManager, gameState);
        debugMap = new Debug(game, decisions.getOpener(), opponentRecord, gameState, gameState.getConfig(), unitManager.getSquadManager());

        autoObserver = new AutoObserver(gameState.getConfig(), game, unitManager.getScoutManager(), unitManager.getSquadManager());

        startPlanEventLogging(decisions.getOpener());
    }

    private void startPlanEventLogging(BuildOrder opener) {
        if (!gameState.getConfig().logPlanEvents) {
            return;
        }

        planEventLogger = new PlanEventLogger(game, gameState, opener == null ? "" : opener.getName(),
                bwem.getMap().getStartingLocations().size());
        PlanEvents.register(planEventLogger);
    }



    @Override
    public void onFrame() {
        if (planEventLogger != null) {
            planEventLogger.onFrame();
        }
        informationManager.onFrame();
        productionManager.onFrame();
        planManager.onFrame();
        unitManager.onFrame();
        debugMap.onFrame();
        autoObserver.onFrame();
    }

    @Override
    public void onUnitHide(Unit unit) {
        informationManager.onUnitHide(unit);
    }

    @Override
    public void onUnitShow(Unit unit) {
        if (unit.getType() == UnitType.Resource_Vespene_Geyser) {
            gameState.getBaseData().onGeyserShow(unit);
        }
        informationManager.onUnitShow(unit);
        unitManager.onUnitShow(unit);
    }

    @Override
    public void onUnitCreate(Unit unit) {
        if (unit.getType() == UnitType.Resource_Vespene_Geyser) {
            gameState.getBaseData().onGeyserComplete(unit);
        }
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
        unitManager.onUnitComplete(unit);
    }

    @Override
    public void onUnitDestroy(Unit unit) {
        informationManager.onUnitDestroy(unit);
        productionManager.onUnitDestroy(unit);
        unitManager.onUnitDestroy(unit);
    }

    @Override
    public void onUnitRenegade(Unit unit) {
        informationManager.onUnitRenegade(unit);
        productionManager.onUnitRenegade(unit);
    }

    @Override
    public void onUnitMorph(Unit unit) {
        informationManager.onUnitMorph(unit);
        productionManager.onUnitMorph(unit);
        unitManager.onUnitMorph(unit);
    }

    @Override
    public void onEnd(boolean isWinner) {
        learningManager.onEnd(isWinner);
        if (planEventLogger != null) {
            planEventLogger.onEnd(isWinner);
        }
    }

    public static void main(String[] args) {
        Bot bot = new Bot();
        bot.bwClient = new BWClient(bot);
        bot.bwClient.startGame();
    }
}
