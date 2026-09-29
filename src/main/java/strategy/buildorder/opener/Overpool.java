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

public class Overpool extends BuildOrder {
    private static final int POOL_SUPPLY = 18;

    private static final int DRONE_TARGET_AFTER_POOL = 10;

    static final int ZERGLING_TARGET = 4;

    public Overpool() {
        super("Overpool");
    }

    @Override
    protected boolean openerComplete(GameState gameState) {
        int unstartedZerglings = 2 * gameState.outstandingUnitPlanCount(UnitType.Zerg_Zergling);
        return openerComplete(
                gameState.structureCount(Readiness.COMMITTED, UnitType.Zerg_Spawning_Pool),
                gameState.ourUnitCount(UnitType.Zerg_Zergling) - unstartedZerglings);
    }

    @Override
    public Set<BuildOrder> transition(GameState gameState) {
        return OpenerTransitions.forGame(gameState);
    }

    @Override
    protected List<Plan> buildPlans(GameState gameState) {
        List<Plan> plans = new ArrayList<>();
        TechProgression techProgression = gameState.getTechProgression();

        // Count existing units/buildings
        int droneCount    = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int supplyUsed    = gameState.getSupply();
        int overlordCount = gameState.ourUnitCount(UnitType.Zerg_Overlord);
        int zerglingCount     = gameState.ourUnitCount(UnitType.Zerg_Zergling);

        if (droneCount < 9 && gameState.canPlanDrone()) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (shouldPlanPool(supplyUsed, overlordCount) && techProgression.canPlanPool()) {
            plans.add(planSpawningPool(gameState));
            return plans;
        }

        boolean pool = techProgression.isPlannedSpawningPool() || techProgression.isSpawningPool();
        if (pool && droneCount < DRONE_TARGET_AFTER_POOL) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (zerglingCount < ZERGLING_TARGET && gameState.canPlanUnit(UnitType.Zerg_Zergling)) {
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

    static boolean shouldPlanPool(int supplyUsed, int overlordCount) {
        return supplyUsed >= POOL_SUPPLY && overlordCount > 1;
    }

    /**
     * Whether the opener has produced everything it will produce, so the terminal build order can
     * take over.
     *
     * <p>The opener's last scripted act is starting its zerglings once the pool finishes. Everything
     * the terminal build order would queue next, the natural hatchery above all, is unreachable
     * until this fires, so the hatchery cannot take the minerals and larva those zerglings need.
     *
     * <p>A zergling counts as started once its larva has become an egg. A plan still waiting in
     * the queue or on a larva does not count: a terminal build order that took over then could
     * still spend the minerals first.
     *
     * @param poolCount Spawning Pools standing, under construction, or claimed by a plan in flight
     * @param startedZerglings zerglings alive or in an egg
     * @return true once the opener should hand off
     */
    static boolean openerComplete(int poolCount, int startedZerglings) {
        return poolCount > 0 && startedZerglings >= ZERGLING_TARGET;
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
