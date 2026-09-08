package strategy.buildorder;

import bwapi.UnitType;
import info.TechProgression;
import info.UnitTypeCount;
import macro.AdvancedUnitEligibility;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.PlanComparator;
import macro.plan.PlanState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import telemetry.PlanEventSink;
import telemetry.PlanEvents;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildOrderTest {

    private static final int GATHERER_FLOOR = AdvancedUnitEligibility.MIN_GATHERERS;

    private static final int EMERGENCY_ZERGLING_TARGET = 6;

    private static final int FRAMES = 30;

    private static final int EMERGENCY_PRIORITY = 0;

    private static final int LAIR_PRIORITY = 3;

    private static final int SPIRE_PRIORITY = 4;

    private static final int EXTRACTOR_PRIORITY = 50;

    private final List<String> withheld = new ArrayList<>();

    private PlanEventSink recorder() {
        return new PlanEventSink() {
            @Override
            public void onEnqueue(Plan plan) {
            }

            @Override
            public void onStateChange(Plan plan, PlanState from, PlanState to) {
            }

            @Override
            public void onBlocked(Plan plan, PlanBlocker blocker) {
            }

            @Override
            public void onWithheld(UnitType unitType, PlanBlocker blocker) {
                withheld.add(unitType + ":" + blocker);
            }
        };
    }

    private static TechProgression withSpire() {
        TechProgression techProgression = new TechProgression();
        techProgression.setSpire(true);
        return techProgression;
    }

    @AfterEach
    void clearSink() {
        PlanEvents.clear();
    }

    @Test
    void theGateWithholdsAMutaliskBelowTheGathererFloorAndReportsIt() {
        PlanEvents.register(recorder());

        assertFalse(BuildOrder.canPlanAdvancedUnit(UnitType.Zerg_Mutalisk, withSpire(), GATHERER_FLOOR - 1));
        assertEquals(1, withheld.size());
        assertEquals("Zerg_Mutalisk:INSUFFICIENT_GATHERERS", withheld.get(0));
    }

    @Test
    void theGateReportsMissingTechBeforeGatherers() {
        PlanEvents.register(recorder());

        assertFalse(BuildOrder.canPlanAdvancedUnit(UnitType.Zerg_Scourge, new TechProgression(), 0));
        assertEquals("Zerg_Scourge:TECH_MISSING", withheld.get(0));
    }

    @Test
    void theGatePassesAnEligibleUnitWithoutAnEvent() {
        PlanEvents.register(recorder());

        assertTrue(BuildOrder.canPlanAdvancedUnit(UnitType.Zerg_Mutalisk, withSpire(), GATHERER_FLOOR));
        assertTrue(withheld.isEmpty());
    }

    private int emergencyZerglingPlansOverFrames(boolean readLivingOnly) {
        UnitTypeCount count = new UnitTypeCount();
        int plans = 0;
        for (int frame = 0; frame < FRAMES; frame++) {
            int zerglings = readLivingOnly
                    ? count.livingCount(UnitType.Zerg_Zergling)
                    : count.get(UnitType.Zerg_Zergling);
            if (BuildOrder.shouldPlanEmergencyZergling(zerglings, EMERGENCY_ZERGLING_TARGET)) {
                count.planUnit(UnitType.Zerg_Zergling);
                plans++;
            }
        }
        return plans;
    }

    @Test
    void theEmergencyQueuesOnePlanPerPairAndThenStops() {
        assertEquals(EMERGENCY_ZERGLING_TARGET / 2, emergencyZerglingPlansOverFrames(false));
    }

    @Test
    void theEmergencyWouldFloodTheQueueIfItCountedOnlyHatchedZerglings() {
        assertEquals(FRAMES, emergencyZerglingPlansOverFrames(true));
    }

    @Test
    void theEmergencyStopsAtTheTargetItIsGiven() {
        assertFalse(BuildOrder.shouldPlanEmergencyZergling(EMERGENCY_ZERGLING_TARGET, EMERGENCY_ZERGLING_TARGET));
        assertTrue(BuildOrder.shouldPlanEmergencyZergling(EMERGENCY_ZERGLING_TARGET - 1, EMERGENCY_ZERGLING_TARGET));
    }

    @Test
    void theSpawningPoolOutranksTheBuildingsItCompetesWith() {
        PlanComparator comparator = new PlanComparator();
        Plan pool = new BuildingPlan(UnitType.Zerg_Spawning_Pool, BuildOrder.SPAWNING_POOL_PRIORITY);

        assertTrue(comparator.compare(pool, new BuildingPlan(UnitType.Zerg_Extractor, EXTRACTOR_PRIORITY)) < 0);
        assertTrue(comparator.compare(pool, new BuildingPlan(UnitType.Zerg_Lair, LAIR_PRIORITY)) < 0);
        assertTrue(comparator.compare(pool, new BuildingPlan(UnitType.Zerg_Spire, SPIRE_PRIORITY)) < 0);
    }

    @Test
    void theSpawningPoolYieldsToTheReservedEmergencyPriority() {
        Plan pool = new BuildingPlan(UnitType.Zerg_Spawning_Pool, BuildOrder.SPAWNING_POOL_PRIORITY);
        Plan emergency = new BuildingPlan(UnitType.Zerg_Sunken_Colony, EMERGENCY_PRIORITY);

        assertTrue(new PlanComparator().compare(emergency, pool) < 0);
    }

    @Test
    void theHashDoesNotDependOnTheClassObjectIdentity() {
        BuildOrder order = new SpeedlingAllIn();
        assertEquals(java.util.Objects.hash(SpeedlingAllIn.class.getName(), order.getName()), order.hashCode());
    }

    @Test
    void twoInstancesOfOneBuildOrderAgreeOnHashAndEquality() {
        BuildOrder first = new SpeedlingAllIn();
        BuildOrder second = new SpeedlingAllIn();
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

}
