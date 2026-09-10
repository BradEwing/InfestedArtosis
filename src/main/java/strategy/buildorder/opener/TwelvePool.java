package strategy.buildorder.opener;

import bwapi.Race;
import bwapi.UnitType;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TwelvePool extends BuildOrder {
    private static final int POOL_SUPPLY = 24;

    private static final int DRONE_TARGET = 12;

    public TwelvePool() {
        super("12Pool");
    }

    @Override
    protected boolean openerComplete(GameState gameState) {
        return openerComplete(
                gameState.ourBuildingOrPlannedCount(UnitType.Zerg_Spawning_Pool),
                gameState.ourLivingUnitCount(UnitType.Zerg_Drone));
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

        int droneCount    = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int supplyUsed = gameState.getSupply();
        int zerglingCount = gameState.ourUnitCount(UnitType.Zerg_Zergling);

        if (droneCount < DRONE_TARGET) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (shouldPlanPool(supplyUsed) && techProgression.canPlanPool()) {
            plans.add(planSpawningPool(gameState));
            return plans;
        }

        if (zerglingCount <= this.zerglingsNeeded(gameState)) {
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

    static boolean shouldPlanPool(int supplyUsed) {
        return supplyUsed >= POOL_SUPPLY;
    }

    /**
     * Whether the opener has produced everything it will produce, so the terminal build order can
     * take over.
     *
     * <p>The pool term counts a Spawning Pool under construction, not only a finished one: the
     * opener's last scripted act is committing to the pool, and everything the terminal build
     * order would queue next, the natural hatchery above all, is unreachable until this fires.
     *
     * <p>The drone term no longer decides the normal game. The opener queues its pool the frame
     * the drone count reaches its target, which is no later than the frame the twelfth drone is
     * alive, so the pool term is already true by then. It survives as the escape hatch for a pool
     * plan that never reaches the queue or is cancelled out of it, which would otherwise leave the
     * opener driving for the rest of the game.
     *
     * @param poolCount Spawning Pools standing or claimed by a building plan in flight
     * @param livingDrones drones alive now, excluding those still planned or in an egg
     * @return true once the opener should hand off
     */
    static boolean openerComplete(int poolCount, int livingDrones) {
        return poolCount > 0 || livingDrones >= DRONE_TARGET;
    }
}
