package strategy.buildorder.opener;

import bwapi.Race;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.GameState;
import info.TechProgression;
import macro.plan.Plan;
import strategy.buildorder.BuildOrder;
import strategy.buildorder.SpeedlingAllIn;
import strategy.buildorder.protoss.ThreeHatchHydra;
import strategy.buildorder.protoss.ThreeHatchMuta;
import strategy.buildorder.terran.CrazyZerg;
import strategy.buildorder.terran.ThreeHatchLurker;
import strategy.buildorder.terran.TwoHatchMuta;
import strategy.buildorder.zerg.OneHatchSpire;
import util.Time;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NinePoolSpeed extends BuildOrder {
    private static final int POOL_SUPPLY = 18;

    private static final Time GAS_TIME = new Time(1, 4);

    public NinePoolSpeed() {
        super("9PoolSpeed");
    }

    @Override
    protected boolean openerComplete(GameState gameState) {
        return openerComplete(gameState.ourBuildingOrPlannedCount(UnitType.Zerg_Spawning_Pool));
    }

    @Override
    public Set<BuildOrder> transition(GameState gameState) {
        Set<BuildOrder> next = new HashSet<>();
        Race opponentRace = gameState.getOpponentRace();
        switch (opponentRace) {
            case Protoss:
                next.add(new ThreeHatchMuta());
                next.add(new ThreeHatchHydra());
                next.add(new SpeedlingAllIn());
                return next;
            case Zerg:
                next.add(new OneHatchSpire());
                next.add(new SpeedlingAllIn());
                return next;
            case Terran:
                next.add(new CrazyZerg());
                next.add(new TwoHatchMuta());
                next.add(new ThreeHatchLurker());
                next.add(new SpeedlingAllIn());
                return next;
            default:
                break;
        }
        return next;
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        List<Plan> plans = new ArrayList<>();
        TechProgression techProgression = gameState.getTechProgression();
        Time gameTime = gameState.getGameTime();

        int droneCount     = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int supplyUsed     = gameState.getSupply();
        int overlordCount  = gameState.ourUnitCount(UnitType.Zerg_Overlord);
        int poolCount      = gameState.ourUnitCount(UnitType.Zerg_Spawning_Pool);
        int extractorCount = gameState.getBaseData().numExtractor();
        int zerglingCount  = gameState.ourUnitCount(UnitType.Zerg_Zergling);

        if (droneCount < 9 && gameState.canPlanDrone()) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (shouldPlanPool(supplyUsed) && poolCount < 1 && techProgression.canPlanPool()) {
            plans.add(planSpawningPool(gameState));
            return plans;
        }

        if (shouldPlanOverlord(droneCount, overlordCount, gameState.hasExcessSupply())) {
            plans.add(planUnit(gameState, UnitType.Zerg_Overlord));
            return plans;
        }

        if (droneCount < 6 && gameState.canPlanDrone()) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (poolCount > 0 && zerglingCount < 3 && gameState.canPlanUnit(UnitType.Zerg_Zergling)) {
            plans.add(planUnit(gameState, UnitType.Zerg_Zergling));
            return plans;
        }

        if (shouldPlanExtractor(extractorCount, gameState.canPlanExtractor(), gameTime.greaterThan(GAS_TIME))) {
            plans.add(planExtractor(gameState));
            return plans;
        }

        if (gameState.canPlanUpgrade(UpgradeType.Metabolic_Boost)) {
            plans.add(planUpgrade(gameState, UpgradeType.Metabolic_Boost));
            return plans;
        }

        if (poolCount > 0 && zerglingCount <= this.zerglingsNeeded(gameState)) {
            plans.add(planUnit(gameState, UnitType.Zerg_Zergling));
            return plans;
        }

        plans.addAll(planUnknownRaceMacro(gameState));
        return plans;
    }

    @Override
    public boolean playsRace(Race race) {
        return true;
    }

    @Override
    public boolean isOpener() { 
        return true; 
    }

    @Override
    protected int poolPriority(int enqueueFrame) {
        return SPAWNING_POOL_PRIORITY;
    }

    static boolean shouldPlanOverlord(int droneCount, int overlordCount, boolean excessSupply) {
        return droneCount > 8 && overlordCount < 2 && !excessSupply;
    }

    static boolean shouldPlanPool(int supplyUsed) {
        return supplyUsed >= POOL_SUPPLY;
    }

    /**
     * Whether the opener has produced everything it will produce, so the terminal build order can
     * take over.
     *
     * <p>Counts a Spawning Pool under construction, not only a finished one: the opener's last
     * scripted act is committing to the pool, and everything the terminal build order would queue
     * next, the natural hatchery above all, is unreachable until this fires.
     *
     * @param poolCount Spawning Pools standing or claimed by a building plan in flight
     * @return true once the opener should hand off
     */
    static boolean openerComplete(int poolCount) {
        return poolCount > 0;
    }

    /**
     * Whether the opener takes its gas yet.
     *
     * <p>Reads the pool through canPlanExtractor, which opens once the pool is planned. A gate on
     * a completed pool is the same expression as openerComplete, so against a known opponent race
     * the opener handed off on the frame its own gas branch first became true.
     *
     * @param extractorCount Extractors standing or reserved by a queued plan
     * @param canPlanExtractor whether a geyser is free and the pool is planned or standing
     * @param pastGasTime whether the game is past GAS_TIME
     * @return true while the Extractor should be queued
     */
    static boolean shouldPlanExtractor(int extractorCount, boolean canPlanExtractor, boolean pastGasTime) {
        return extractorCount < 1 && canPlanExtractor && pastGasTime;
    }
}
