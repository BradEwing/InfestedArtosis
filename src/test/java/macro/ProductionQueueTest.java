package macro;

import bwapi.UnitType;
import bwapi.UpgradeType;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.PlanState;
import macro.plan.UnitPlan;
import macro.plan.UpgradePlan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionQueueTest {

    private static final int FRAME = 9649;

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
    void gasDemandSumsTheGasPriceOfEveryQueuedPlan() {
        ProductionQueue queue = new ProductionQueue();
        assertEquals(0, queue.gasDemand(FRAME));
        queue.add(new UnitPlan(UnitType.Zerg_Zergling, 6275));
        queue.add(new BuildingPlan(UnitType.Zerg_Spire, SPIRE_PRIORITY));
        queue.add(new UnitPlan(UnitType.Zerg_Mutalisk, UnitPlan.ADVANCED_UNIT_PRIORITY));
        queue.add(new UnitPlan(UnitType.Zerg_Mutalisk, UnitPlan.ADVANCED_UNIT_PRIORITY));

        assertEquals(UnitType.Zerg_Spire.gasPrice() + 2 * UnitType.Zerg_Mutalisk.gasPrice(), queue.gasDemand(FRAME));
    }

    @Test
    void aPlanPlannedBeyondTheStaleThresholdDoesNotCountTowardGasDemand() {
        ProductionQueue queue = new ProductionQueue();
        Plan mutalisk = new UnitPlan(UnitType.Zerg_Mutalisk, UnitPlan.ADVANCED_UNIT_PRIORITY);
        Plan carapace = new UpgradePlan(UpgradeType.Pneumatized_Carapace, 9649);
        carapace.markPlannedSince(FRAME);
        queue.add(mutalisk);
        queue.add(carapace);
        int stale = FRAME + ProductionQueue.STALE_PLANNED_FRAMES + 1;
        mutalisk.markPlannedSince(FRAME + ProductionQueue.STALE_PLANNED_FRAMES);

        assertEquals(UnitType.Zerg_Mutalisk.gasPrice() + carapace.gasPrice(),
                queue.gasDemand(FRAME + ProductionQueue.STALE_PLANNED_FRAMES));
        assertEquals(UnitType.Zerg_Mutalisk.gasPrice(), queue.gasDemand(stale));
    }

    @Test
    void theFirstGasPlanInQueueOrderCountsHoweverLongItHasWaited() {
        ProductionQueue queue = new ProductionQueue();
        Plan carapace = new UpgradePlan(UpgradeType.Pneumatized_Carapace, 100);
        Plan flyerAttacks = new UpgradePlan(UpgradeType.Zerg_Flyer_Attacks, 10901);
        carapace.markPlannedSince(FRAME);
        flyerAttacks.markPlannedSince(FRAME);
        queue.add(flyerAttacks);
        queue.add(carapace);

        assertEquals(carapace.gasPrice(), queue.gasDemand(FRAME + ProductionQueue.STALE_PLANNED_FRAMES + 1));
    }

    @Test
    void aPlanRequeuedFromScheduleStartsAFreshStintInPlanned() {
        Plan carapace = new UpgradePlan(UpgradeType.Pneumatized_Carapace, 100);
        carapace.markPlannedSince(FRAME);
        int stale = FRAME + ProductionQueue.STALE_PLANNED_FRAMES + 1;
        assertTrue(ProductionQueue.isStale(carapace, stale));

        carapace.setState(PlanState.SCHEDULE);
        carapace.setState(PlanState.PLANNED);
        carapace.markPlannedSince(stale);

        assertFalse(ProductionQueue.isStale(carapace, stale + ProductionQueue.STALE_PLANNED_FRAMES));
        assertEquals(0, new UnitPlan(UnitType.Zerg_Mutalisk, 1).plannedFrames(stale));
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

    @Test
    void buildingPlanCountCountsQueuedHatcheriesOnly() {
        ProductionQueue queue = new ProductionQueue();
        queue.add(new BuildingPlan(UnitType.Zerg_Hatchery, 6275));
        queue.add(new BuildingPlan(UnitType.Zerg_Hatchery, 6300));
        queue.add(new BuildingPlan(UnitType.Zerg_Extractor, EXTRACTOR_PRIORITY));
        queue.add(new UnitPlan(UnitType.Zerg_Drone, 6400));

        assertEquals(2, queue.buildingPlanCount(UnitType.Zerg_Hatchery));
        assertEquals(1, queue.buildingPlanCount(UnitType.Zerg_Extractor));
        assertEquals(0, queue.buildingPlanCount(UnitType.Zerg_Lair));
    }

    @Test
    void buildingPlanCountDropsToZeroOnceTheQueueDrains() {
        ProductionQueue queue = new ProductionQueue();
        queue.add(new BuildingPlan(UnitType.Zerg_Hatchery, 6275));

        assertEquals(1, queue.buildingPlanCount(UnitType.Zerg_Hatchery));

        queue.poll();

        assertEquals(0, queue.buildingPlanCount(UnitType.Zerg_Hatchery));
    }
}
