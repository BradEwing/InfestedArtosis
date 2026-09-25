package strategy.buildorder.zerg;

import bwapi.UnitType;
import bwapi.UpgradeType;
import info.TechProgression;
import info.UnitTypeCount;
import macro.ProductionQueue;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import org.junit.jupiter.api.Test;
import strategy.buildorder.BuildOrder;

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

    private static final int POOL_FRAME = 2300;

    private static final int GAS_FRAME = POOL_FRAME + 1;

    @Test
    void withholdsFlyerCarapaceWhileTheMutalisksAreOnlyPlanned() {
        UnitTypeCount count = new UnitTypeCount();
        for (int i = 0; i < 7; i++) {
            count.planUnit(UnitType.Zerg_Mutalisk);
        }

        assertTrue(count.get(UnitType.Zerg_Mutalisk) > 6);
        assertFalse(OneHatchSpire.shouldPlanFlyerCarapace(withSpire(), count.livingCount(UnitType.Zerg_Mutalisk)));
    }

    @Test
    void plansFlyerCarapaceWithSevenLivingMutalisks() {
        assertTrue(OneHatchSpire.shouldPlanFlyerCarapace(withSpire(), 7));
    }

    @Test
    void withholdsFlyerCarapaceWithSixLivingMutalisks() {
        assertFalse(OneHatchSpire.shouldPlanFlyerCarapace(withSpire(), 6));
    }

    private static TechProgression withSpire() {
        TechProgression techProgression = new TechProgression();
        techProgression.setSpire(true);
        return techProgression;
    }

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

    private static int flyerCarapacePriority(int livingMutalisks) {
        UnitTypeCount count = new UnitTypeCount();
        for (int i = 0; i < livingMutalisks; i++) {
            count.addUnit(UnitType.Zerg_Mutalisk);
        }
        return new OneHatchSpire().upgradePriority(UpgradeType.Zerg_Flyer_Carapace, count, 12000);
    }

    @Test
    void flyerCarapacePollsAheadOfMutalisksOnceTheMutalisksThatPlanItAreAlive() {
        assertEquals(12000, flyerCarapacePriority(OneHatchSpire.MUTALISKS_BEFORE_FLYER_UPGRADE - 1));
        assertEquals(BuildOrder.ARMY_UPGRADE_PRIORITY, flyerCarapacePriority(OneHatchSpire.MUTALISKS_BEFORE_FLYER_UPGRADE));
        assertEquals(BuildOrder.ARMY_UPGRADE_PRIORITY, flyerCarapacePriority(OneHatchSpire.MUTALISKS_BEFORE_FLYER_UPGRADE + 1));
    }
}
