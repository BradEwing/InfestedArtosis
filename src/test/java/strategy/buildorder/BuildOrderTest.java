package strategy.buildorder;

import bwapi.UnitType;
import info.TechProgression;
import info.UnitTypeCount;
import macro.AdvancedUnitEligibility;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
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
}
