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

/**
 * 12 Pool: drones to 12, Spawning Pool at 12, then six zerglings the moment the pool finishes.
 *
 * <p>The opener hands over only once all six zerglings are queued, so the terminal build order's
 * natural hatchery, Extractor and drones cannot take the larva and minerals the opening zerglings
 * need while the pool is going up.
 */
public class TwelvePool extends BuildOrder {
    private static final int POOL_SUPPLY = 24;

    private static final int DRONE_TARGET = 12;

    static final int OPENING_ZERGLINGS = 6;

    public TwelvePool() {
        super("12Pool");
    }

    @Override
    protected boolean openerComplete(GameState gameState) {
        return openerComplete(
                gameState.structureCount(Readiness.COMMITTED, UnitType.Zerg_Spawning_Pool),
                gameState.ourLivingUnitCount(UnitType.Zerg_Drone),
                gameState.ourUnitCount(UnitType.Zerg_Zergling));
    }

    @Override
    public Set<BuildOrder> transition(GameState gameState) {
        return OpenerTransitions.forRace(gameState.getOpponentRace());
    }

    @Override
    protected List<Plan> buildPlans(GameState gameState) {
        List<Plan> plans = new ArrayList<>();
        TechProgression techProgression = gameState.getTechProgression();

        int droneCount     = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int supplyUsed     = gameState.getSupply();
        int committedPools = gameState.structureCount(Readiness.COMMITTED, UnitType.Zerg_Spawning_Pool);
        int poolCount      = gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Spawning_Pool);
        int zerglingCount  = gameState.ourUnitCount(UnitType.Zerg_Zergling);

        if (shouldPlanDrone(droneCount, committedPools, zerglingCount)) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (shouldPlanPool(supplyUsed) && techProgression.canPlanPool()) {
            plans.add(planSpawningPool(gameState));
            return plans;
        }

        if (shouldPlanZergling(poolCount, zerglingCount)) {
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
     * <p>The zerglings term is the trigger: the opener's last scripted act is queueing its six
     * opening zerglings, and the count includes planned zerglings, two per plan, so it fires on
     * the frame the third plan is queued. A Spawning Pool that is only committed does not hand
     * over, because the terminal build order would spend the pool's build time on its natural,
     * Extractor and drones and leave the finished pool without larva or minerals for zerglings.
     *
     * <p>The drone term is a recovery path, not a second trigger. It fires only while no Spawning
     * Pool is committed, which in the scripted order never coincides with twelve living drones:
     * the pool is queued at 12 supply, before the twelfth drone hatches. It carries the game where
     * the pool has been lost and twelve drones already stand, handing over to a terminal build
     * order that plans its own pool.
     *
     * @param committedPools Spawning Pools standing, under construction, or claimed by a plan in flight
     * @param livingDrones drones alive now, excluding those still planned or in an egg
     * @param zerglingCount zerglings living and planned, two per plan
     * @return true once the opener should hand off
     */
    static boolean openerComplete(int committedPools, int livingDrones, int zerglingCount) {
        return zerglingCount >= OPENING_ZERGLINGS || committedPools == 0 && livingDrones >= DRONE_TARGET;
    }

    /**
     * Whether the opener queues another drone.
     *
     * <p>Replacement drones, including the one for the drone that becomes the pool, are held from
     * the frame the pool is committed until the six opening zerglings are queued, so the larva
     * and minerals banked through the pool build are there for the zerglings when it finishes.
     *
     * @param droneCount drones living, morphing, and planned
     * @param committedPools Spawning Pools standing, under construction, or claimed by a plan in flight
     * @param zerglingCount zerglings living and planned, two per plan
     * @return true while another drone should be queued
     */
    static boolean shouldPlanDrone(int droneCount, int committedPools, int zerglingCount) {
        return droneCount < DRONE_TARGET && (committedPools == 0 || zerglingCount >= OPENING_ZERGLINGS);
    }

    /**
     * Whether the opener queues another of its six opening zerglings.
     *
     * <p>The pool term counts finished pools. A zergling plan queued against a pool that is only
     * committed takes a larva it holds for the rest of the pool build, because the morph no-ops
     * until the pool stands.
     *
     * @param poolCount Spawning Pools that have finished building
     * @param zerglingCount zerglings living and planned, two per plan
     * @return true until the six opening zerglings are planned
     */
    static boolean shouldPlanZergling(int poolCount, int zerglingCount) {
        return poolCount > 0 && zerglingCount < OPENING_ZERGLINGS;
    }

    /**
     * False. The opener hands over before any tech unit exists, so it is never larva bound on
     * tech and never needs the shared macro hatchery.
     */
    @Override
    protected boolean macroHatcheryTechReady(TechProgression techProgression) {
        return false;
    }
}
