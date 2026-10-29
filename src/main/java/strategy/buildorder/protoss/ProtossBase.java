package strategy.buildorder.protoss;

import bwapi.Race;
import bwapi.UnitType;
import info.GameState;
import info.Readiness;
import info.tracking.StrategyTracker;
import info.tracking.protoss.ProxyGate;
import macro.plan.Plan;
import strategy.buildorder.BuildOrder;
import strategy.buildorder.SporeTargets;

import java.util.Collections;
import java.util.List;

/**
 * Shared behaviour for the Protoss matchup builds.
 *
 * <p>Abstract, and deliberately silent on {@link BuildOrder#macroHatcheryTechReady}. A default
 * here would be a lineage default: every build under it would inherit a tech condition it never
 * stated, which is how the larva-bound macro hatchery was lost once already. Leaving the hook
 * unanswered makes a new build in this matchup fail to compile until it states its own.
 */
public abstract class ProtossBase extends BuildOrder {

    protected ProtossBase(String name) {
        super(name);
    }

    static int zealotDrivenZerglings(int zealots, int gateways) {
        return ProtossReactions.zealotDrivenZerglings(zealots, gateways);
    }

    @Override
    protected List<Plan> buildPlans(GameState gameState) {
        return Collections.emptyList();
    }

    @Override
    public boolean playsRace(Race race) {
        return false;
    }

    @Override
    protected int zerglingsNeeded(GameState gameState) {
        if (gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Spawning_Pool) < 1) {
            return 0;
        }

        int currentZerglings = gameState.getUnitTypeCount().get(UnitType.Zerg_Zergling);
        int zerglings = protossZerglingTarget(gameState);

        if (currentZerglings >= zerglings) {
            return 0;
        }
        return zerglings;
    }

    /**
     * The zergling total the Protoss matchup holds against what the strategy tracker and the
     * observed units say, read by builds that do not extend this class.
     */
    public static int protossZerglingTarget(GameState gameState) {
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        int cannons = gameState.getObservedUnitTracker().getCountOfLivingUnits(UnitType.Protoss_Photon_Cannon);
        int gateways = gameState.getObservedUnitTracker().getCountOfLivingUnits(UnitType.Protoss_Gateway);
        return ProtossReactions.zerglingTarget(
                strategyTracker.isDetectedStrategy("CannonRush"),
                cannons,
                gameState.getResourceCount().availableMinerals(),
                strategyTracker.isAnyDetectedStrategy("2Gate", ProxyGate.NAME),
                strategyTracker.isDetectedStrategy("1GateCore"),
                strategyTracker.isDetectedStrategy("FFE"),
                gameState.enemyUnitCount(UnitType.Protoss_Zealot),
                gateways);
    }

    @Override
    protected int requiredSpores(GameState gameState) {
        return SporeTargets.protossSpores(gameState::enemyUnitCount);
    }

    /**
     * Sunkens per base the Protoss matchup asks for, see {@link ProtossReactions#matchupSunkens}.
     */
    @Override
    protected int matchupSunkens(GameState gameState) {
        return protossMatchupSunkens(gameState);
    }

    /**
     * The Protoss matchup's sunken target, read by builds that do not extend this class.
     */
    public static int protossMatchupSunkens(GameState gameState) {
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        return ProtossReactions.matchupSunkens(
                strategyTracker.isDetectedStrategy("CannonRush"),
                strategyTracker.isAnyDetectedStrategy("2Gate", ProxyGate.NAME),
                gameState.enemyUnitCount(UnitType.Protoss_Zealot),
                gameState.getGameTime(),
                gameState.ourUnitCount(UnitType.Zerg_Drone));
    }
}
