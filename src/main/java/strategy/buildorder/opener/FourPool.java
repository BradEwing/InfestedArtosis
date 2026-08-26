package strategy.buildorder.opener;

import bwapi.Race;
import bwapi.UnitType;
import info.GameState;
import info.TechProgression;
import macro.plan.Plan;
import strategy.buildorder.BuildOrder;
import util.Time;

import java.util.ArrayList;
import java.util.List;

public class FourPool extends BuildOrder {

    /**
     * Zergling plans this build order will leave waiting in the production queue at once. It
     * never transitions, so it must keep queueing zerglings for the whole game; the bound comes
     * from queue depth rather than from any total zergling count, which cannot dead-end.
     * Unschedulable plans are requeued rather than dropped, so a larva or mineral shortage holds
     * the queue at this depth instead of growing it. Emergency defense plans zerglings on its
     * own path and may briefly push the queue above this depth.
     */
    private static final int MAX_QUEUED_ZERGLING_PLANS = 6;

    public FourPool() {
        super("4Pool");
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        Time currentTime = gameState.getGameTime();
        TechProgression techProgression = gameState.getTechProgression();
        List<Plan> list = new ArrayList<>();
        boolean needPool = techProgression.canPlanPool();

        // Don't plan on first frame, otherwise the drone assigned to build the spawning pool won't gather minerals
        if (currentTime.lessThanOrEqual(new Time(1))) {
            return list;
        }

        if (needPool) {
            Plan poolPlan = this.planSpawningPool(gameState);
            list.add(poolPlan);
            return list;
        }

        final int neededDrones = 4 - gameState.ourUnitCount(UnitType.Zerg_Drone);
        if (neededDrones > 0) {
            Plan dronePlan = this.planUnit(gameState, UnitType.Zerg_Drone);
            list.add(dronePlan);
            return list;
        }

        final int queuedZerglings = gameState.queuedUnitPlanCount(UnitType.Zerg_Zergling);
        if (techProgression.isSpawningPool() && queuedZerglings < MAX_QUEUED_ZERGLING_PLANS) {
            Plan zerglingPlan = this.planUnit(gameState, UnitType.Zerg_Zergling);
            list.add(zerglingPlan);
        }

        return list;
    }

    @Override
    public boolean playsRace(Race race) {
        return true;
    }

    @Override
    public boolean isOpener() {
        return true;
    }
}
