package strategy.buildorder.opener;

import bwapi.UnitType;
import info.GameState;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.UnitPlan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwelvePoolTest {

    private static final int NO_POOL = 0;

    private static final int ONE_POOL = 1;

    private static final int NO_ZERGLINGS = 0;

    private static final int ELEVEN_DRONES = 11;

    private static final int TWELVE_DRONES = 12;

    private static final int ZERGLINGS_PER_PLAN = 2;

    private static final int TWELVE_SUPPLY = 24;

    private static final int ELEVEN_SUPPLY = 22;

    private static final int FRAME = 2062;

    /** Stands in for the plan factories so the real step walk runs without a live game. */
    private static final class ScriptedTwelvePool extends TwelvePool {
        @Override
        protected Plan planUnit(GameState gameState, UnitType unitType) {
            return new UnitPlan(unitType, FRAME);
        }

        @Override
        protected Plan planSpawningPool(GameState gameState) {
            return new BuildingPlan(UnitType.Zerg_Spawning_Pool, poolPriority(FRAME));
        }

        @Override
        protected List<Plan> planUnknownRaceMacro(GameState gameState) {
            return Collections.emptyList();
        }
    }

    private static List<UnitType> planned(List<Plan> plans) {
        return plans.stream().map(Plan::getPlannedUnit).collect(Collectors.toList());
    }

    /** LVOUC0M9 frame 2062: the pool was queued the frame before and the opener handed off. */
    @Test
    void holdsOnACommittedButUnfinishedSpawningPool() {
        assertFalse(TwelvePool.openerComplete(ONE_POOL, ELEVEN_DRONES, NO_ZERGLINGS));
        assertFalse(TwelvePool.openerComplete(ONE_POOL, TWELVE_DRONES, NO_ZERGLINGS));
    }

    @Test
    void holdsWhileFewerThanSixZerglingsAreQueued() {
        assertFalse(TwelvePool.openerComplete(ONE_POOL, ELEVEN_DRONES, TwelvePool.OPENING_ZERGLINGS - ZERGLINGS_PER_PLAN));
    }

    @Test
    void handsOffOnceSixZerglingsAreQueued() {
        assertTrue(TwelvePool.openerComplete(ONE_POOL, ELEVEN_DRONES, TwelvePool.OPENING_ZERGLINGS));
    }

    @Test
    void holdsWhileNoSpawningPoolIsCommittedTo() {
        assertFalse(TwelvePool.openerComplete(NO_POOL, ELEVEN_DRONES, NO_ZERGLINGS));
    }

    @Test
    void recoversOnTwelveLivingDronesWithoutASpawningPool() {
        assertTrue(TwelvePool.openerComplete(NO_POOL, TWELVE_DRONES, NO_ZERGLINGS));
    }

    @Test
    void takesDronesToTwelveBeforeThePool() {
        assertTrue(TwelvePool.shouldPlanDrone(ELEVEN_DRONES, NO_POOL, NO_ZERGLINGS));
        assertFalse(TwelvePool.shouldPlanDrone(TWELVE_DRONES, NO_POOL, NO_ZERGLINGS));
    }

    /** LVOUC0M9: replacement Drones #13-#15 took every larva made during the pool build. */
    @Test
    void holdsReplacementDronesOnceThePoolIsCommitted() {
        assertFalse(TwelvePool.shouldPlanDrone(ELEVEN_DRONES, ONE_POOL, NO_ZERGLINGS));
        assertFalse(TwelvePool.shouldPlanDrone(ELEVEN_DRONES, ONE_POOL, TwelvePool.OPENING_ZERGLINGS - ZERGLINGS_PER_PLAN));
    }

    @Test
    void releasesReplacementDronesOnceTheOpeningZerglingsAreQueued() {
        assertTrue(TwelvePool.shouldPlanDrone(ELEVEN_DRONES, ONE_POOL, TwelvePool.OPENING_ZERGLINGS));
    }

    /**
     * The army branch waits on a finished pool. A pool that is only committed to cannot morph a
     * zergling, so a plan queued against one holds a larva for the rest of the pool build.
     */
    @Test
    void withholdsZerglingsUntilTheSpawningPoolStands() {
        assertFalse(TwelvePool.shouldPlanZergling(NO_POOL, NO_ZERGLINGS));
    }

    @Test
    void queuesExactlyThreeZerglingPlansAtZeroTwoAndFour() {
        List<Integer> queuedAt = new ArrayList<>();
        int zerglingCount = NO_ZERGLINGS;
        for (int frame = 0; frame < 10; frame++) {
            if (TwelvePool.shouldPlanZergling(ONE_POOL, zerglingCount)) {
                queuedAt.add(zerglingCount);
                zerglingCount += ZERGLINGS_PER_PLAN;
            }
        }
        assertEquals(Arrays.asList(0, 2, 4), queuedAt);
        assertFalse(TwelvePool.shouldPlanZergling(ONE_POOL, TwelvePool.OPENING_ZERGLINGS));
    }

    @Test
    void stepsQueueTheDroneBeforeThePool() {
        List<Plan> plans = new ScriptedTwelvePool()
                .planSteps(null, ELEVEN_DRONES, ELEVEN_SUPPLY, NO_POOL, NO_POOL, NO_ZERGLINGS, true);

        assertEquals(Collections.singletonList(UnitType.Zerg_Drone), planned(plans));
    }

    @Test
    void stepsQueueThePoolAtTwelveSupply() {
        List<Plan> plans = new ScriptedTwelvePool()
                .planSteps(null, TWELVE_DRONES, TWELVE_SUPPLY, NO_POOL, NO_POOL, NO_ZERGLINGS, true);

        assertEquals(Collections.singletonList(UnitType.Zerg_Spawning_Pool), planned(plans));
    }

    /** LVOUC0M9 frames 2062-3385: the pool's drone is gone and the pool is going up. */
    @Test
    void stepsQueueNothingWhileThePoolIsCommittedButUnfinished() {
        List<Plan> plans = new ScriptedTwelvePool()
                .planSteps(null, ELEVEN_DRONES, ELEVEN_SUPPLY, ONE_POOL, NO_POOL, NO_ZERGLINGS, false);

        assertTrue(plans.isEmpty());
    }

    @Test
    void stepsQueueAZerglingRatherThanADroneOnceThePoolFinishes() {
        List<Plan> plans = new ScriptedTwelvePool()
                .planSteps(null, ELEVEN_DRONES, ELEVEN_SUPPLY, ONE_POOL, ONE_POOL, NO_ZERGLINGS, false);

        assertEquals(Collections.singletonList(UnitType.Zerg_Zergling), planned(plans));
    }

    @Test
    void stepsQueueTheReplacementDroneOnceTheOpeningZerglingsAreQueued() {
        List<Plan> plans = new ScriptedTwelvePool()
                .planSteps(null, ELEVEN_DRONES, ELEVEN_SUPPLY, ONE_POOL, ONE_POOL, TwelvePool.OPENING_ZERGLINGS, false);

        assertEquals(Collections.singletonList(UnitType.Zerg_Drone), planned(plans));
    }
}
