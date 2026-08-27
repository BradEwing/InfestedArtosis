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
import unit.UnitManager;
import util.BotLogger;

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

    /** False until {@link #initialize()} completes, so a failed start does not become a per-frame NPE storm. */
    private boolean started;

    /**
     * Runs an event handler body, logging and suppressing anything it throws.
     *
     * <p>An exception escaping a listener method propagates out of the JBWAPI event dispatch and kills the client
     * thread. StarCraft keeps running, so the game is played out and lost while the bot sits idle, and
     * {@link #onEnd(boolean)} never fires — losing the learning row for that game. Suppressing here keeps the client
     * alive: the current event is abandoned, but later events and the end-of-game teardown still run.
     *
     * @param event the handler name, used as the log context.
     * @param handler the handler body.
     */
    private void safely(String event, Runnable handler) {
        try {
            handler.run();
        } catch (Throwable t) {
            BotLogger.error(event, t);
        }
    }

    /**
     * Runs a guarded event handler body only once the managers exist.
     *
     * <p>If {@link #onStart()} failed, every manager field is still null and an unguarded handler would throw a
     * {@link NullPointerException} on every event for the rest of the game. Skipping is quieter and equivalent: a bot
     * that never started has nothing to do.
     *
     * @param event the handler name, used as the log context.
     * @param handler the handler body.
     */
    private void safelyWhenStarted(String event, Runnable handler) {
        if (!started) {
            return;
        }
        safely(event, handler);
    }

    @Override
    public void onStart() {
        safely("onStart", this::initialize);
    }

    /**
     * Loads the map and constructs the managers. Extracted from {@link #onStart()} so the guarded body stays a method
     * reference rather than an oversized lambda.
     */
    private void initialize() {
        game = bwClient.getGame();

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

        started = true;
    }

    /**
     * Runs the game loop in two guarded halves.
     *
     * <p>The four gameplay managers share one guard because their order is load-bearing — {@link InformationManager}
     * refreshes the shared {@code GameState} the other three read, so running them on a half-refreshed state is worse
     * than skipping the frame. Debug rendering and the observer camera are pure observation and get their own guard,
     * so a fault there can never stop the bot from playing.
     */
    @Override
    public void onFrame() {
        safelyWhenStarted("onFrame", () -> {
            informationManager.onFrame();
            productionManager.onFrame();
            planManager.onFrame();
            unitManager.onFrame();
        });
        safelyWhenStarted("onFrame.observation", () -> {
            debugMap.onFrame();
            autoObserver.onFrame();
        });
    }

    @Override
    public void onUnitHide(Unit unit) {
        safelyWhenStarted("onUnitHide", () -> informationManager.onUnitHide(unit));
    }

    @Override
    public void onUnitShow(Unit unit) {
        safelyWhenStarted("onUnitShow", () -> {
            if (unit.getType() == UnitType.Resource_Vespene_Geyser) {
                gameState.getBaseData().onGeyserShow(unit);
            }
            informationManager.onUnitShow(unit);
            unitManager.onUnitShow(unit);
        });
    }

    @Override
    public void onUnitCreate(Unit unit) {
        safelyWhenStarted("onUnitCreate", () -> {
            if (unit.getType() == UnitType.Resource_Vespene_Geyser) {
                gameState.getBaseData().onGeyserComplete(unit);
            }
        });
    }

    @Override
    public void onUnitComplete(Unit unit) {
        safelyWhenStarted("onUnitComplete", () -> {
            if (unit.getPlayer() != game.self()) {
                return;
            }
            if (unit.getType() == UnitType.Zerg_Larva) {
                return;
            }

            informationManager.onUnitComplete(unit);
            unitManager.onUnitComplete(unit);
        });
    }

    @Override
    public void onUnitDestroy(Unit unit) {
        safelyWhenStarted("onUnitDestroy", () -> {
            informationManager.onUnitDestroy(unit);
            productionManager.onUnitDestroy(unit);
            unitManager.onUnitDestroy(unit);
        });
    }

    @Override
    public void onUnitRenegade(Unit unit) {
        safelyWhenStarted("onUnitRenegade", () -> {
            informationManager.onUnitRenegade(unit);
            productionManager.onUnitRenegade(unit);
        });
    }

    @Override
    public void onUnitMorph(Unit unit) {
        safelyWhenStarted("onUnitMorph", () -> {
            informationManager.onUnitMorph(unit);
            productionManager.onUnitMorph(unit);
            unitManager.onUnitMorph(unit);
        });
    }

    @Override
    public void onEnd(boolean isWinner) {
        if (learningManager == null) {
            BotLogger.error("onEnd", "no learning manager; game result not recorded");
            return;
        }
        safely("onEnd", () -> learningManager.onEnd(isWinner));
    }

    public static void main(String[] args) {
        Bot bot = new Bot();
        bot.bwClient = new BWClient(bot);
        bot.bwClient.startGame();
    }
}
