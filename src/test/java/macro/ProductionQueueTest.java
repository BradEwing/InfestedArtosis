package macro;

import bwapi.UnitType;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.UnitPlan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ProductionQueueTest {

    private static final int EMERGENCY_DEFENSE_PRIORITY = 1;
    private static final int SPIRE_PRIORITY = 4;
    private static final int EXTRACTOR_PRIORITY = 50;

    private List<Plan> drain(ProductionQueue queue) {
        List<Plan> polled = new ArrayList<>();
        while (!queue.isEmpty()) {
            polled.add(queue.poll());
        }
        return polled;
    }

    @Test
    void advancedUnitPlanLeadsTheBacklogItWasDerivedBehind() {
        ProductionQueue queue = new ProductionQueue();
        queue.add(new UnitPlan(UnitType.Zerg_Zergling, 6275));
        queue.add(new UnitPlan(UnitType.Zerg_Drone, 7100));
        queue.add(new UnitPlan(UnitType.Zerg_Zergling, 7678));
        Plan mutalisk = new UnitPlan(UnitType.Zerg_Mutalisk, UnitPlan.ADVANCED_UNIT_PRIORITY);
        queue.add(mutalisk);

        assertSame(mutalisk, queue.poll());
    }

    @Test
    void advancedUnitPlanKeepsEmergencyAndBuildingBandsAhead() {
        ProductionQueue queue = new ProductionQueue();
        Plan mutalisk = new UnitPlan(UnitType.Zerg_Mutalisk, UnitPlan.ADVANCED_UNIT_PRIORITY);
        Plan extractor = new BuildingPlan(UnitType.Zerg_Extractor, EXTRACTOR_PRIORITY);
        Plan spire = new BuildingPlan(UnitType.Zerg_Spire, SPIRE_PRIORITY);
        Plan emergencyZergling = new UnitPlan(UnitType.Zerg_Zergling, EMERGENCY_DEFENSE_PRIORITY);
        queue.add(mutalisk);
        queue.add(extractor);
        queue.add(spire);
        queue.add(emergencyZergling);

        List<Plan> polled = drain(queue);

        assertEquals(4, polled.size());
        assertSame(emergencyZergling, polled.get(0));
        assertSame(spire, polled.get(1));
        assertSame(extractor, polled.get(2));
        assertSame(mutalisk, polled.get(3));
    }

    @Test
    void zerglingContinuousBuildKeepsDerivationOrder() {
        ProductionQueue queue = new ProductionQueue();
        List<Plan> derived = new ArrayList<>();
        for (int frame = 1000; frame < 1006; frame++) {
            Plan zergling = new UnitPlan(UnitType.Zerg_Zergling, frame);
            derived.add(zergling);
            queue.add(zergling);
        }

        assertEquals(derived, drain(queue));
    }

    @Test
    void advancedUnitPlanDoesNotDisplaceTheZerglingsBehindIt() {
        ProductionQueue queue = new ProductionQueue();
        List<Plan> zerglings = new ArrayList<>();
        for (int frame = 7690; frame < 7696; frame++) {
            Plan zergling = new UnitPlan(UnitType.Zerg_Zergling, frame);
            zerglings.add(zergling);
            queue.add(zergling);
        }
        Plan mutalisk = new UnitPlan(UnitType.Zerg_Mutalisk, UnitPlan.ADVANCED_UNIT_PRIORITY);
        queue.add(mutalisk);

        List<Plan> polled = drain(queue);

        assertSame(mutalisk, polled.get(0));
        assertEquals(zerglings, polled.subList(1, polled.size()));
    }

    @Test
    void unitPlanCountCountsQueuedPlansOfOneUnitType() {
        ProductionQueue queue = new ProductionQueue();
        queue.add(new UnitPlan(UnitType.Zerg_Zergling, 6275));
        queue.add(new UnitPlan(UnitType.Zerg_Zergling, 6300));
        queue.add(new UnitPlan(UnitType.Zerg_Drone, 6400));
        queue.add(new BuildingPlan(UnitType.Zerg_Spire, SPIRE_PRIORITY));

        assertEquals(2, queue.unitPlanCount(UnitType.Zerg_Zergling));
        assertEquals(1, queue.unitPlanCount(UnitType.Zerg_Drone));
        assertEquals(0, queue.unitPlanCount(UnitType.Zerg_Spire));
        assertEquals(0, queue.unitPlanCount(UnitType.Zerg_Mutalisk));
    }

    @Test
    void unitPlanCountDropsPlansThatLeftTheQueue() {
        ProductionQueue queue = new ProductionQueue();
        Plan mutalisk = new UnitPlan(UnitType.Zerg_Mutalisk, UnitPlan.ADVANCED_UNIT_PRIORITY);
        queue.add(mutalisk);
        queue.add(new UnitPlan(UnitType.Zerg_Zergling, 7690));

        assertEquals(1, queue.unitPlanCount(UnitType.Zerg_Mutalisk));

        queue.poll();

        assertEquals(0, queue.unitPlanCount(UnitType.Zerg_Mutalisk));
    }
}
