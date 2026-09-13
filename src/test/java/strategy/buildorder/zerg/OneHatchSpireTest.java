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

    private static final int NO_SPIRE = 0;

    private static final int ONE_SPIRE = 1;

    private static final boolean CAN_PLAN_EXTRACTOR = true;

    private static final int NO_LARVA = 0;

    private static final int TWO_HATCHERIES = 2;

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

    /**
     * The second gas follows the decision to build a Spire, not the Spire finishing. A Spire
     * claimed by a plan in flight, or standing part-built, already commits the build to
     * Mutalisks and to the gas they cost.
     */
    @Test
    void takesAnotherGasOnceTheSpireIsCommitted() {
        assertTrue(OneHatchSpire.shouldPlanAnotherGas(ONE_SPIRE, CAN_PLAN_EXTRACTOR));
    }

    @Test
    void withholdsAnotherGasUntilASpireIsCommitted() {
        assertFalse(OneHatchSpire.shouldPlanAnotherGas(NO_SPIRE, CAN_PLAN_EXTRACTOR));
    }

    @Test
    void withholdsAnotherGasWhileTheExtractorRequestIsBarred() {
        assertFalse(OneHatchSpire.shouldPlanAnotherGas(ONE_SPIRE, !CAN_PLAN_EXTRACTOR));
    }

    /**
     * Game L9NW30JG from 10:36: two hatcheries, at most one larva, and a peak bank of 638 minerals
     * and 530 gas, taken here as unreserved. The 1050 mineral bar the build used to wait for was
     * never reached.
     */
    @Test
    void requestsAHatcheryWhileLarvaStarvedAndFloatingBothResources() {
        assertTrue(OneHatchSpire.shouldPlanMacroHatchery(ONE_SPIRE, 1, TWO_HATCHERIES, 638, 530));
    }

    @Test
    void requestsAHatcheryAtTheFloatBars() {
        assertTrue(OneHatchSpire.shouldPlanMacroHatchery(ONE_SPIRE, NO_LARVA, TWO_HATCHERIES,
                OneHatchSpire.FLOAT_MINERALS, OneHatchSpire.FLOAT_GAS));
    }

    @Test
    void theMineralBarCoversTheHatcheryItBuys() {
        assertEquals(UnitType.Zerg_Hatchery.mineralPrice(), OneHatchSpire.FLOAT_MINERALS);
    }

    @Test
    void doesNotRequestAHatcheryWhileLarvaIsNotShort() {
        assertFalse(OneHatchSpire.shouldPlanMacroHatchery(ONE_SPIRE, TWO_HATCHERIES, TWO_HATCHERIES, 638, 530));
    }

    @Test
    void doesNotRequestAHatcheryOnFloatingMineralsAlone() {
        assertFalse(OneHatchSpire.shouldPlanMacroHatchery(ONE_SPIRE, NO_LARVA, TWO_HATCHERIES, 638,
                OneHatchSpire.FLOAT_GAS - 1));
    }

    @Test
    void doesNotRequestAHatcheryOnFloatingGasAlone() {
        assertFalse(OneHatchSpire.shouldPlanMacroHatchery(ONE_SPIRE, NO_LARVA, TWO_HATCHERIES,
                OneHatchSpire.FLOAT_MINERALS - 1, 530));
    }

    /**
     * The same 638 minerals and 530 gas with a Mutalisk pair and an upgrade already claiming most
     * of it. The banks are read after reservations, which a plan takes when it is scheduled, so a
     * committed bank is not a float.
     */
    @Test
    void doesNotRequestAHatcheryWhileTheBankIsReservedByScheduledPlans() {
        int reservedMinerals = 2 * UnitType.Zerg_Mutalisk.mineralPrice() + 150;
        int reservedGas = 2 * UnitType.Zerg_Mutalisk.gasPrice() + 150;

        assertFalse(OneHatchSpire.shouldPlanMacroHatchery(ONE_SPIRE, NO_LARVA, TWO_HATCHERIES,
                638 - reservedMinerals, 530 - reservedGas));
    }

    @Test
    void doesNotRequestAHatcheryWhileBankingForTheSpire() {
        assertFalse(OneHatchSpire.shouldPlanMacroHatchery(NO_SPIRE, NO_LARVA, TWO_HATCHERIES, 638, 530));
    }
}
