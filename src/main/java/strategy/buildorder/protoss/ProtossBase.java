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
import util.Time;

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

    private static final int EXCESS_MINERALS = 350;

    static final int MAX_ZEALOT_DEMAND = 25;

    static final int ZERGLINGS_PER_GATEWAY = 6;

    protected ProtossBase(String name) {
        super(name);
    }

    static int zealotDrivenZerglings(int zealots, int gateways) {
        int cap = MAX_ZEALOT_DEMAND;
        if (gateways > 0) {
            cap = Math.min(ZERGLINGS_PER_GATEWAY * gateways, MAX_ZEALOT_DEMAND);
        }
        return Math.min(zealots * 2, cap);
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

        int zerglings = 6;
        int currentZerglings = gameState.getUnitTypeCount().get(UnitType.Zerg_Zergling);
        int zealots = gameState.enemyUnitCount(UnitType.Protoss_Zealot);
        int availableMinerals = gameState.getResourceCount().availableMinerals();

        StrategyTracker strategyTracker = gameState.getStrategyTracker();

        if (strategyTracker.isDetectedStrategy("CannonRush")) {
            int cannons = gameState.getObservedUnitTracker()
                    .getCountOfLivingUnits(UnitType.Protoss_Photon_Cannon);
            zerglings = 8 + (cannons * 3);
            if (availableMinerals > EXCESS_MINERALS) {
                zerglings += availableMinerals % UnitType.Zerg_Zergling.mineralPrice();
            }
        } else if (strategyTracker.isAnyDetectedStrategy("2Gate", ProxyGate.NAME)) {
            zerglings = 12;
        } else if (strategyTracker.isDetectedStrategy("1GateCore")) {
            zerglings = 4;
        } else if (strategyTracker.isDetectedStrategy("FFE")) {
            zerglings = 2;
        }

        int gateways = gameState.getObservedUnitTracker()
                .getCountOfLivingUnits(UnitType.Protoss_Gateway);
        zerglings += zealotDrivenZerglings(zealots, gateways);

        if (currentZerglings >= zerglings) {
            return 0;
        }
        return zerglings;
    }

    @Override
    protected int requiredSpores(GameState gameState) {
        return SporeTargets.protossSpores(gameState::enemyUnitCount);
    }

    /**
     * Sunkens per base the Protoss matchup asks for.
     *
     * <p>One if CannonRush is detected, and nothing else. Otherwise the sum of:
     * <ul>
     *     <li>one if 2Gate or ProxyGate is detected and a Zealot has been seen or the game is past 3:20;</li>
     *     <li>one if more than 3 Zealots are seen, and another if more than 6;</li>
     *     <li>one past 10:00 with more than 20 drones.</li>
     * </ul>
     */
    @Override
    protected int matchupSunkens(GameState gameState) {
        int sunkens = 0;
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        Time gameTime = gameState.getGameTime();

        if (strategyTracker.isDetectedStrategy("CannonRush")) {
            return 1;
        }

        boolean zealotsObserved = gameState.enemyUnitCount(UnitType.Protoss_Zealot) > 0;
        if (strategyTracker.isAnyDetectedStrategy("2Gate", ProxyGate.NAME) && (zealotsObserved || gameTime.greaterThan(new Time(3, 20)))) {
            sunkens += 1;
        }

        if (gameState.enemyUnitCount(UnitType.Protoss_Zealot) > 3) {
            sunkens += 1;
        }

        if (gameState.enemyUnitCount(UnitType.Protoss_Zealot) > 6) {
            sunkens += 1;
        }

        if (gameTime.greaterThan(new Time(10, 0)) && gameState.ourUnitCount(UnitType.Zerg_Drone) > 20) {
            sunkens += 1;
        }

        return sunkens;
    }
}
