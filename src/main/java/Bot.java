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
import telemetry.BaseCheckLogger;
import telemetry.BaseChecks;
import telemetry.CombatTelemetry;
import telemetry.FixedFireLogger;
import telemetry.FixedFireTelemetry;
import telemetry.FlockLogger;
import telemetry.FlockTelemetry;
import telemetry.PerchAssignmentLogger;
import telemetry.PerchAssignments;
import telemetry.PlanEventLogger;
import telemetry.PlanEvents;
import telemetry.AirReinforcementLogger;
import telemetry.AirReinforcementTelemetry;
import telemetry.HarassLogger;
import telemetry.HarassTelemetry;
import telemetry.RunbyLogger;
import telemetry.BurrowLogger;
import telemetry.BurrowTelemetry;
import telemetry.BunkerLogger;
import telemetry.BunkerTelemetry;
import telemetry.ReachLogger;
import telemetry.ReachTelemetry;
import telemetry.RunbyTelemetry;
import telemetry.SquadDecisionLogger;
import telemetry.SquadDecisions;
import telemetry.TargetChoiceLogger;
import telemetry.TargetChoices;
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
    private CombatTelemetry combatTelemetry;

    private AutoObserver autoObserver;

    private PlanEventLogger planEventLogger;
    private SquadDecisionLogger squadDecisionLogger;
    private PerchAssignmentLogger perchAssignmentLogger;
    private BaseCheckLogger baseCheckLogger;
    private TargetChoiceLogger targetChoiceLogger;
    private RunbyLogger runbyLogger;
    private HarassLogger harassLogger;
    private AirReinforcementLogger airReinforcementLogger;
    private FlockLogger flockLogger;
    private ReachLogger reachLogger;
    private BunkerLogger bunkerLogger;
    private BurrowLogger burrowLogger;
    private FixedFireLogger fixedFireLogger;

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

        combatTelemetry = new CombatTelemetry(game, gameState, unitManager.getSquadManager());
        startSquadDecisionLogging();
        startPerchAssignmentLogging();
        startBaseCheckLogging();
        startTargetChoiceLogging();
        startRunbyLogging();
        startHarassLogging();
        startAirReinforcementLogging();
        startFlockLogging();
        startReachLogging();
        startBunkerLogging();
        startBurrowLogging();
        startFixedFireLogging();
        startPlanEventLogging(decisions.getOpener());
    }

    private void startSquadDecisionLogging() {
        if (!gameState.getConfig().telemetryCombat) {
            return;
        }

        squadDecisionLogger = new SquadDecisionLogger(game, gameState, unitManager.getSquadManager(),
                combatTelemetry.getGameId());
        SquadDecisions.register(squadDecisionLogger);
    }

    private void startPerchAssignmentLogging() {
        if (!gameState.getConfig().telemetryCombat) {
            return;
        }

        perchAssignmentLogger = new PerchAssignmentLogger(game, gameState, combatTelemetry.getGameId());
        PerchAssignments.register(perchAssignmentLogger);
    }

    private void startBaseCheckLogging() {
        if (!gameState.getConfig().telemetryCombat) {
            return;
        }

        baseCheckLogger = new BaseCheckLogger(game, combatTelemetry.getGameId());
        BaseChecks.register(baseCheckLogger);
    }

    private void startTargetChoiceLogging() {
        if (!gameState.getConfig().telemetryCombat) {
            return;
        }

        targetChoiceLogger = new TargetChoiceLogger(game, combatTelemetry.getGameId());
        TargetChoices.register(targetChoiceLogger);
    }

    private void startRunbyLogging() {
        if (!gameState.getConfig().telemetryCombat) {
            return;
        }

        runbyLogger = new RunbyLogger(game, combatTelemetry.getGameId());
        RunbyTelemetry.register(runbyLogger);
    }

    private void startHarassLogging() {
        if (!gameState.getConfig().telemetryCombat) {
            return;
        }

        harassLogger = new HarassLogger(game, combatTelemetry.getGameId());
        HarassTelemetry.register(harassLogger);
    }

    private void startAirReinforcementLogging() {
        if (!gameState.getConfig().telemetryCombat) {
            return;
        }

        airReinforcementLogger = new AirReinforcementLogger(game, unitManager.getSquadManager(),
                combatTelemetry.getGameId());
        AirReinforcementTelemetry.register(airReinforcementLogger);
    }

    private void startFlockLogging() {
        if (!gameState.getConfig().telemetryCombat) {
            return;
        }

        flockLogger = new FlockLogger(game, combatTelemetry.getGameId());
        FlockTelemetry.register(flockLogger);
    }

    private void startReachLogging() {
        if (!gameState.getConfig().telemetryCombat) {
            return;
        }

        reachLogger = new ReachLogger(game, combatTelemetry.getGameId());
        ReachTelemetry.register(reachLogger);
    }

    private void startBunkerLogging() {
        if (!gameState.getConfig().telemetryCombat) {
            return;
        }

        bunkerLogger = new BunkerLogger(game, gameState, combatTelemetry.getGameId());
        BunkerTelemetry.register(bunkerLogger);
    }

    private void startBurrowLogging() {
        if (!gameState.getConfig().telemetryCombat) {
            return;
        }

        burrowLogger = new BurrowLogger(game, combatTelemetry.getGameId());
        BurrowTelemetry.register(burrowLogger);
    }

    private void startFixedFireLogging() {
        if (!gameState.getConfig().telemetryCombat) {
            return;
        }

        fixedFireLogger = new FixedFireLogger(game, combatTelemetry.getGameId());
        FixedFireTelemetry.register(fixedFireLogger);
    }

    private void startPlanEventLogging(BuildOrder opener) {
        if (!gameState.getConfig().logPlanEvents) {
            return;
        }

        planEventLogger = new PlanEventLogger(game, gameState, opener == null ? "" : opener.getName(),
                bwem.getMap().getStartingLocations().size());
        PlanEvents.register(planEventLogger);
        PlanEvents.racePrior(learningManager.racePriorLabel());
        gameState.reportClaimedBases();
        gameState.reportTerranMechPrior();
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
        if (squadDecisionLogger != null) {
            squadDecisionLogger.onFrame();
        }
        if (perchAssignmentLogger != null) {
            perchAssignmentLogger.onFrame();
        }
        if (baseCheckLogger != null) {
            baseCheckLogger.onFrame();
        }
        if (targetChoiceLogger != null) {
            targetChoiceLogger.onFrame();
        }
        if (runbyLogger != null) {
            runbyLogger.onFrame();
        }
        if (harassLogger != null) {
            harassLogger.onFrame();
        }
        if (airReinforcementLogger != null) {
            airReinforcementLogger.onFrame();
        }
        if (flockLogger != null) {
            flockLogger.onFrame();
        }
        if (reachLogger != null) {
            reachLogger.onFrame();
        }
        if (bunkerLogger != null) {
            bunkerLogger.onFrame();
        }
        if (burrowLogger != null) {
            burrowLogger.onFrame();
        }
        if (fixedFireLogger != null) {
            fixedFireLogger.onFrame();
        }
        combatTelemetry.onFrame();
        debugMap.onFrame();
        autoObserver.onFrame();
        learningManager.onFrame();
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
        combatTelemetry.onUnitDestroy(unit);
        if (bunkerLogger != null) {
            bunkerLogger.onUnitDestroy(unit);
        }
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
        if (squadDecisionLogger != null) {
            squadDecisionLogger.onEnd();
        }
        if (perchAssignmentLogger != null) {
            perchAssignmentLogger.onEnd();
        }
        if (baseCheckLogger != null) {
            baseCheckLogger.onEnd();
        }
        if (targetChoiceLogger != null) {
            targetChoiceLogger.onEnd();
        }
        if (runbyLogger != null) {
            runbyLogger.onEnd();
        }
        if (harassLogger != null) {
            harassLogger.onEnd();
        }
        if (airReinforcementLogger != null) {
            airReinforcementLogger.onEnd();
        }
        if (flockLogger != null) {
            flockLogger.onEnd();
        }
        if (reachLogger != null) {
            reachLogger.onEnd();
        }
        if (bunkerLogger != null) {
            bunkerLogger.onEnd();
        }
        if (burrowLogger != null) {
            burrowLogger.onEnd();
        }
        if (fixedFireLogger != null) {
            fixedFireLogger.onEnd();
        }
        combatTelemetry.onEnd(isWinner);
    }

    public static void main(String[] args) {
        Bot bot = new Bot();
        bot.bwClient = new BWClient(bot);
        bot.bwClient.startGame();
    }
}
