package strategy.buildorder.opener;

import bwapi.Race;
import bwapi.UnitType;
import info.BaseData;
import info.GameState;
import info.TechProgression;
import macro.plan.Plan;
import strategy.buildorder.BuildOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TwelveHatch extends BuildOrder {

    private static final int HATCHERY_SUPPLY = 24;

    private static final int DRONE_TARGET = 12;

    private static final int POOL_PRIORITY_OFFSET = 1;

    public TwelveHatch() {
        super("12Hatch");
    }

    @Override
    public boolean playsRace(Race race) {
        return race != Race.Unknown;
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        List<Plan> plans = new ArrayList<>();

        BaseData baseData = gameState.getBaseData();
        TechProgression techProgression = gameState.getTechProgression();
        int baseCount = baseData.currentBaseCount();
        int plannedHatcheries = gameState.getPlannedHatcheries();
        final int plannedAndCurrentHatcheries = plannedHatcheries + baseCount;
        int droneCount    = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int supplyUsed    = gameState.getSupply();
        int overlordCount = gameState.ourUnitCount(UnitType.Zerg_Overlord);

        if (droneCount < 9) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (droneCount < DRONE_TARGET && overlordCount >= 2) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (shouldPlanHatchery(supplyUsed, plannedAndCurrentHatcheries)) {
            Plan hatcheryPlan = this.planNewBase(gameState);
            if (hatcheryPlan != null) {
                plans.add(hatcheryPlan);
            }
        }

        if (shouldPlanPool(supplyUsed, techProgression.canPlanPool())) {
            plans.add(planSpawningPool(gameState));
        }

        return plans;
    }

    static boolean shouldPlanHatchery(int supplyUsed, int plannedAndCurrentHatcheries) {
        return supplyUsed >= HATCHERY_SUPPLY && plannedAndCurrentHatcheries < 2;
    }

    /**
     * Whether the opener queues its Spawning Pool.
     *
     * <p>Reads the supply bar the hatchery branch reads and the pool itself, never the expansion,
     * so the pool is queued on the frame the opener reaches its hatchery step whether or not a
     * hatchery plan came out of it. A rule that suppresses the expansion therefore cannot take the
     * pool with it, and the opener never depends on a transition target to obtain one.
     *
     * @param supplyUsed supply used now
     * @param canPlanPool whether no Spawning Pool is standing or already claimed by a plan
     * @return true while the Spawning Pool should be queued
     */
    static boolean shouldPlanPool(int supplyUsed, boolean canPlanPool) {
        return supplyUsed >= HATCHERY_SUPPLY && canPlanPool;
    }

    /**
     * Priority for the Spawning Pool, one behind a hatchery queued on the same frame, so the
     * expansion is served first and the pool follows it.
     *
     * @param enqueueFrame the frame the plan is created on
     * @return the plan priority
     */
    @Override
    protected int poolPriority(int enqueueFrame) {
        return enqueueFrame + POOL_PRIORITY_OFFSET;
    }

    @Override
    public boolean shouldTransition(GameState gameState) {
        BaseData baseData = gameState.getBaseData();
        int baseCount = baseData.currentBaseCount();
        int droneCount = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int plannedHatcheries = gameState.getPlannedHatcheries();
        return shouldTransition(plannedHatcheries + baseCount, droneCount);
    }

    static boolean shouldTransition(int plannedAndCurrentHatcheries, int droneCount) {
        return plannedAndCurrentHatcheries >= 2 && droneCount == DRONE_TARGET;
    }

    @Override
    public Set<BuildOrder> transition(GameState gameState) {
        return OpenerTransitions.forRace(gameState.getOpponentRace());
    }

    @Override
    public boolean isOpener() { 
        return true; 
    }
}
