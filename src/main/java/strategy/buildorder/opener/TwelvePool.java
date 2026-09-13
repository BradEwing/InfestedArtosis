package strategy.buildorder.opener;

import bwapi.Race;
import bwapi.UnitType;
import info.GameState;
import info.Readiness;
import info.TechProgression;
import macro.plan.Plan;
import strategy.buildorder.BuildOrder;

import java.util.ArrayList;
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
                gameState.structureCount(Readiness.COMMITTED, UnitType.Zerg_Spawning_Pool),
                gameState.ourLivingUnitCount(UnitType.Zerg_Drone));
    }

    @Override
    public Set<BuildOrder> transition(GameState gameState) {
        return OpenerTransitions.forRace(gameState.getOpponentRace());
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        List<Plan> plans = new ArrayList<>();
        TechProgression techProgression = gameState.getTechProgression();

        int droneCount    = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int supplyUsed = gameState.getSupply();
        int poolCount = gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Spawning_Pool);
        int zerglingCount = gameState.ourUnitCount(UnitType.Zerg_Zergling);

        if (droneCount < DRONE_TARGET) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (shouldPlanPool(supplyUsed) && techProgression.canPlanPool()) {
            plans.add(planSpawningPool(gameState));
            return plans;
        }

        if (shouldPlanZergling(poolCount, zerglingCount, this.zerglingsNeeded(gameState))) {
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
     * <p>The drone term is an escape hatch, not a second trigger. The opener queues its pool the
     * frame its drone count reaches the target, and twelve living drones is the pool's supply, so
     * the pool term is already true whenever the drone term is. It carries the game only where no
     * pool plan survives to be counted, which would otherwise leave the opener driving for the
     * rest of the game.
     *
     * @param poolCount Spawning Pools standing, under construction, or claimed by a plan in flight
     * @param livingDrones drones alive now, excluding those still planned or in an egg
     * @return true once the opener should hand off
     */
    static boolean openerComplete(int poolCount, int livingDrones) {
        return poolCount > 0 || livingDrones >= DRONE_TARGET;
    }

    /**
     * Whether the opener queues another zergling.
     *
     * <p>The pool term counts finished pools. Without it the branch is true from the frame the
     * drone target is met, well before a pool is planned, and queues a zergling every frame against
     * a tech requirement nothing can satisfy. While no pool plan exists each of those plans is
     * enqueued and swept on the same frame; once one exists the plan survives the sweep and takes a
     * larva it holds for the rest of the pool build, because the morph no-ops until the pool stands.
     *
     * <p>The count it is compared against includes planned zerglings, two per plan, so the branch
     * would otherwise run away while the pool was still going up.
     *
     * @param poolCount Spawning Pools that have finished building
     * @param zerglingCount zerglings living and planned, two per plan
     * @param zerglingsNeeded the target the build order asks for
     * @return true while another zergling should be queued
     */
    static boolean shouldPlanZergling(int poolCount, int zerglingCount, int zerglingsNeeded) {
        return poolCount > 0 && zerglingCount <= zerglingsNeeded;
    }
}
