package strategy.buildorder.protoss;

import bwapi.Race;
import bwapi.UnitType;
import info.GameState;
import info.tracking.StrategyTracker;
import macro.plan.Plan;
import strategy.buildorder.BuildOrder;
import util.Time;

import java.util.Collections;
import java.util.List;

public class ProtossBase extends BuildOrder {

    private static final int EXCESS_MINERALS = 350;

    /**
     * Ceiling on the zerglings the enemy's zealots may add to the per-strategy base: the zealot
     * term only, not the {@link #zerglingsNeeded(GameState)} total. Uncapped, a bot that masses
     * zealots scales the demand forever and crowds tech units out of the production queue.
     */
    static final int MAX_ZEALOT_DEMAND = 25;

    /**
     * Zerglings a single observed enemy Gateway justifies; once Gateways are observed the cap is
     * this per Gateway, still bounded by {@link #MAX_ZEALOT_DEMAND}.
     */
    static final int ZERGLINGS_PER_GATEWAY = 6;

    protected ProtossBase(String name) {
        super(name);
    }

    /**
     * Zerglings owed for the enemy's zealots, on top of the per-strategy base, counted as
     * individual zerglings rather than morphs (a morph hatches a pair).
     *
     * Observed zealot count is a lagging, fog-censored measure of the army already produced, while
     * Gateway count predicts sustained production, so once a Gateway is observed the cap becomes
     * {@link #ZERGLINGS_PER_GATEWAY} per Gateway. That lowers the cap while the product stays under
     * {@link #MAX_ZEALOT_DEMAND} and stops mattering once it reaches it. Both constants are
     * hand-tuned in IA-314, not derived.
     *
     * @param zealots living enemy zealots observed, from {@link GameState#enemyUnitCount(UnitType)}
     * @param gateways living enemy Gateways observed, from the ObservedUnitTracker
     * @return individual zerglings to add, never more than {@link #MAX_ZEALOT_DEMAND}
     */
    static int zealotDrivenZerglings(int zealots, int gateways) {
        int cap = MAX_ZEALOT_DEMAND;
        if (gateways > 0) {
            cap = Math.min(ZERGLINGS_PER_GATEWAY * gateways, MAX_ZEALOT_DEMAND);
        }
        return Math.min(zealots * 2, cap);
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        return Collections.emptyList();
    }

    @Override
    public boolean playsRace(Race race) {
        return false;
    }

    @Override
    protected int zerglingsNeeded(GameState gameState) {
        if (gameState.ourUnitCount(UnitType.Zerg_Spawning_Pool) < 1) {
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
        } else if (strategyTracker.isDetectedStrategy("2Gate")) {
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
        int spores = 0;

        if (gameState.enemyUnitCount(UnitType.Protoss_Stargate) > 0) {
            spores = 1;
        }

        if (gameState.enemyUnitCount(UnitType.Protoss_Corsair) > 0
                || gameState.enemyUnitCount(UnitType.Protoss_Scout) > 0) {
            spores = Math.max(spores, 1);
        }

        if (gameState.enemyUnitCount(UnitType.Protoss_Fleet_Beacon) > 0) {
            spores = Math.max(spores, 2);
        }

        if (gameState.enemyUnitCount(UnitType.Protoss_Dark_Templar) > 0
                || gameState.enemyUnitCount(UnitType.Protoss_Arbiter) > 0) {
            spores = Math.max(spores, 1);
        }

        if (gameState.enemyUnitCount(UnitType.Protoss_Templar_Archives) > 0) {
            spores = Math.max(spores, 1);
        }

        if (gameState.enemyUnitCount(UnitType.Protoss_Observer) > 0
                || gameState.enemyUnitCount(UnitType.Protoss_Shuttle) > 0) {
            spores = Math.max(spores, 1);
        }

        return spores;
    }

    /**
     * requiredSunkens per base
     *
     * Take a sunken if 2Gate is detected.
     * Taken a sunken if 6+ zealots are detected
     * Take a sunken if game time over 10 minutes and drone supply is healthy (20+)
     */
    @Override
    protected int requiredSunkens(GameState gameState) {
        int sunkens = 0;
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        Time gameTime = gameState.getGameTime();

        if (strategyTracker.isDetectedStrategy("CannonRush")) {
            return 1;
        }

        boolean zealotsObserved = gameState.enemyUnitCount(UnitType.Protoss_Zealot) > 0;
        if (strategyTracker.isDetectedStrategy("2Gate") && (zealotsObserved || gameTime.greaterThan(new Time(3, 20)))) {
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
