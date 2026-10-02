package macro;

import bwapi.UnitType;
import info.TechProgression;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.UnitPlan;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardianPipelineTest {

    private static final int GATHERERS = AdvancedUnitEligibility.MIN_GATHERERS;

    private static TechProgression hiveAndSpire() {
        TechProgression techProgression = new TechProgression();
        techProgression.setLair(true);
        techProgression.setSpire(true);
        techProgression.setHive(true);
        return techProgression;
    }

    @Test
    void aGreaterSpireMorphWaitsForAFreeSpire() {
        assertEquals(PlanBlocker.NO_PRODUCER,
                ProductionManager.buildingMorphBlocker(UnitType.Zerg_Greater_Spire, false));
        assertEquals(PlanBlocker.NONE,
                ProductionManager.buildingMorphBlocker(UnitType.Zerg_Greater_Spire, true));
    }

    @Test
    void aGuardianNeedsAFinishedGreaterSpire() {
        TechProgression techProgression = hiveAndSpire();
        techProgression.setPlannedGreaterSpire(true);

        assertEquals(PlanBlocker.TECH_MISSING,
                AdvancedUnitEligibility.blocker(UnitType.Zerg_Guardian, techProgression, GATHERERS));

        techProgression.setGreaterSpire(true);
        techProgression.setPlannedGreaterSpire(false);

        assertEquals(PlanBlocker.NONE,
                AdvancedUnitEligibility.blocker(UnitType.Zerg_Guardian, techProgression, GATHERERS));
    }

    @Test
    void aGuardianIsNotMadeFromLarva() {
        assertEquals(UnitType.Zerg_Mutalisk, UnitType.Zerg_Guardian.whatBuilds().getFirst());
        assertFalse(ProductionManager.isLarvaBlocked(UnitType.Zerg_Guardian, false, true));
        assertFalse(ProductionManager.isSupplyBlocked(UnitType.Zerg_Guardian, 0));
    }

    @Test
    void aGuardianPlanQueuedAheadOfTheGreaterSpirePlanIsRemoved() {
        Plan guardian = new UnitPlan(UnitType.Zerg_Guardian, UnitPlan.ADVANCED_UNIT_PRIORITY);
        Plan greaterSpire = new BuildingPlan(UnitType.Zerg_Greater_Spire, 200);

        List<Plan> removed = ProductionManager.plansWithLaterPrerequisites(
                Arrays.asList(guardian, greaterSpire), hiveAndSpire());

        assertEquals(Collections.singletonList(guardian), removed);
    }

    @Test
    void aGuardianPlanQueuedBehindTheGreaterSpirePlanIsKept() {
        Plan guardian = new UnitPlan(UnitType.Zerg_Guardian, UnitPlan.ADVANCED_UNIT_PRIORITY);
        Plan greaterSpire = new BuildingPlan(UnitType.Zerg_Greater_Spire, 4);

        List<Plan> removed = ProductionManager.plansWithLaterPrerequisites(
                Arrays.asList(greaterSpire, guardian), hiveAndSpire());

        assertTrue(removed.isEmpty());
    }

    @Test
    void theGreaterSpireNeedsAFinishedSpireAndAFinishedHive() {
        TechProgression techProgression = hiveAndSpire();
        assertTrue(techProgression.canPlanGreaterSpire());

        techProgression.setHive(false);
        assertFalse(techProgression.canPlanGreaterSpire());

        techProgression.setHive(true);
        techProgression.setSpire(false);
        assertFalse(techProgression.canPlanGreaterSpire());
    }

    @Test
    void theGreaterSpireIsPlannedOnceAndNotAgainOnceItStands() {
        TechProgression techProgression = hiveAndSpire();

        techProgression.setPlannedGreaterSpire(true);
        assertFalse(techProgression.canPlanGreaterSpire());

        techProgression.setPlannedGreaterSpire(false);
        techProgression.setGreaterSpire(true);
        assertFalse(techProgression.canPlanGreaterSpire());
    }

    @Test
    void theSpireTechFlagStaysSetWhileTheGreaterSpireStands() {
        TechProgression techProgression = hiveAndSpire();
        techProgression.setGreaterSpire(true);

        assertTrue(techProgression.isSpire());
        assertFalse(techProgression.canPlanSpire());
        assertEquals(PlanBlocker.NONE,
                AdvancedUnitEligibility.blocker(UnitType.Zerg_Mutalisk, techProgression, GATHERERS));
    }

    @Test
    void excessMorphPlansAreThoseBeyondTheirProducersAndTheirEggs() {
        assertEquals(0, ProductionManager.excessLurkerPlans(2, 2, 0));
        assertEquals(1, ProductionManager.excessLurkerPlans(3, 1, 1));
    }
}
