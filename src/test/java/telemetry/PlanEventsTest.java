package telemetry;

import bwapi.UnitType;
import macro.ProductionQueue;
import macro.plan.Plan;
import macro.plan.PlanState;
import macro.plan.UnitPlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanEventsTest {

    private final List<String> transitions = new ArrayList<>();
    private final List<Plan> enqueued = new ArrayList<>();

    private PlanEventSink recorder() {
        return new PlanEventSink() {
            @Override
            public void onEnqueue(Plan plan) {
                enqueued.add(plan);
            }

            @Override
            public void onStateChange(Plan plan, PlanState from, PlanState to) {
                transitions.add(from + ">" + to);
            }
        };
    }

    @AfterEach
    void clearSink() {
        PlanEvents.clear();
    }

    @Test
    void setStateFiresHookWithPreviousAndNextState() {
        PlanEvents.register(recorder());

        Plan plan = new UnitPlan(UnitType.Zerg_Drone, 1);
        plan.setState(PlanState.SCHEDULE);
        plan.setState(PlanState.BUILDING);

        assertEquals(2, transitions.size());
        assertEquals("PLANNED>SCHEDULE", transitions.get(0));
        assertEquals("SCHEDULE>BUILDING", transitions.get(1));
        assertEquals(PlanState.BUILDING, plan.getState());
    }

    @Test
    void setStateStillAssignsWithNoSinkRegistered() {
        Plan plan = new UnitPlan(UnitType.Zerg_Drone, 1);
        plan.setState(PlanState.COMPLETE);

        assertEquals(PlanState.COMPLETE, plan.getState());
        assertTrue(transitions.isEmpty());
    }

    @Test
    void queueAddAndAddAllFireEnqueueHook() {
        PlanEvents.register(recorder());

        ProductionQueue queue = new ProductionQueue();
        Plan single = new UnitPlan(UnitType.Zerg_Zergling, 1);
        Plan batched = new UnitPlan(UnitType.Zerg_Overlord, 2);
        queue.add(single);
        queue.addAll(Collections.singletonList(batched));

        assertEquals(2, enqueued.size());
        assertEquals(single, enqueued.get(0));
        assertEquals(batched, enqueued.get(1));
    }
}
