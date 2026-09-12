package strategy.buildorder.zerg;

import bwapi.UnitType;
import info.TechProgression;
import macro.ProductionQueue;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OneHatchSpireTest {

    private static final int NO_EXTRACTOR = 0;

    private static final int POOL_FRAME = 2300;

    private static final int GAS_FRAME = POOL_FRAME + 1;

    @Test
    void derivesTheDroneAlongsideTheMutalisk() {
        List<UnitType> unitTypes = OneHatchSpire.unitsToPlan(false, true, false, true);
        assertEquals(Arrays.asList(UnitType.Zerg_Mutalisk, UnitType.Zerg_Drone), unitTypes);
    }

    @Test
    void derivesTheZerglingAlongsideTheScourge() {
        List<UnitType> unitTypes = OneHatchSpire.unitsToPlan(true, false, true, false);
        assertEquals(Arrays.asList(UnitType.Zerg_Scourge, UnitType.Zerg_Zergling), unitTypes);
    }

    @Test
    void derivesTheZerglingAheadOfTheDrone() {
        List<UnitType> unitTypes = OneHatchSpire.unitsToPlan(false, true, true, true);
        assertEquals(Arrays.asList(UnitType.Zerg_Mutalisk, UnitType.Zerg_Zergling), unitTypes);
    }

    @Test
    void derivesTheDroneWithoutAnySpireUnit() {
        List<UnitType> unitTypes = OneHatchSpire.unitsToPlan(false, false, false, true);
        assertEquals(Collections.singletonList(UnitType.Zerg_Drone), unitTypes);
    }

    @Test
    void derivesNothingOnceEveryCountIsMet() {
        assertEquals(Collections.emptyList(), OneHatchSpire.unitsToPlan(false, false, false, false));
    }

    @Test
    void withholdsTheFirstGasUntilTheSpawningPoolIsPlanned() {
        TechProgression techProgression = new TechProgression();

        assertFalse(OneHatchSpire.shouldPlanFirstGas(NO_EXTRACTOR, techProgression.canPlanExtractor()));
    }

    @Test
    void takesTheFirstGasWhileTheSpawningPoolIsStillBuilding() {
        TechProgression techProgression = new TechProgression();
        techProgression.setPlannedSpawningPool(true);

        assertFalse(techProgression.isSpawningPool());
        assertTrue(OneHatchSpire.shouldPlanFirstGas(NO_EXTRACTOR, techProgression.canPlanExtractor()));
    }

    @Test
    void withholdsTheFirstGasOnceAGeyserIsClaimed() {
        TechProgression techProgression = new TechProgression();
        techProgression.setSpawningPool(true);

        assertFalse(OneHatchSpire.shouldPlanFirstGas(1, techProgression.canPlanExtractor()));
    }

    /**
     * The gas gate is the complement of the pool gate, so it cannot open on any frame the build
     * still plans the pool on. The Extractor is enqueued on a later frame and carries that frame
     * as its priority, which leaves the pool ahead of it in the queue.
     */
    @Test
    void queuesTheFirstGasBehindTheSpawningPool() {
        TechProgression techProgression = new TechProgression();

        assertTrue(techProgression.canPlanPool());
        assertFalse(OneHatchSpire.shouldPlanFirstGas(NO_EXTRACTOR, techProgression.canPlanExtractor()));

        Plan pool = new BuildingPlan(UnitType.Zerg_Spawning_Pool, POOL_FRAME);
        techProgression.setPlannedSpawningPool(true);

        assertTrue(OneHatchSpire.shouldPlanFirstGas(NO_EXTRACTOR, techProgression.canPlanExtractor()));

        ProductionQueue queue = new ProductionQueue();
        queue.add(new BuildingPlan(UnitType.Zerg_Extractor, GAS_FRAME));
        queue.add(pool);

        assertSame(pool, queue.poll());
    }
}
