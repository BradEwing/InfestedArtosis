package strategy.buildorder;

import bwapi.UnitType;
import info.TechProgression;
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
}
