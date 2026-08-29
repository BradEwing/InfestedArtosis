package telemetry;

import bwapi.UnitType;
import macro.ProductionQueue;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.PlanCancelReason;
import macro.plan.PlanCancelSite;
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
    private final List<PlanBlocker> blockers = new ArrayList<>();

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

            @Override
            public void onBlocked(Plan plan, PlanBlocker blocker) {
                blockers.add(blocker);
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

    @Test
    void reassigningTheSameStateEmitsNothing() {
        PlanEvents.register(recorder());

        Plan plan = new UnitPlan(UnitType.Zerg_Drone, 1);
        plan.setState(PlanState.SCHEDULE);
        plan.setState(PlanState.SCHEDULE);

        assertEquals(1, transitions.size());
        assertEquals("PLANNED>SCHEDULE", transitions.get(0));
    }

    @Test
    void blockedHookCarriesTheBlocker() {
        PlanEvents.register(recorder());

        Plan plan = new UnitPlan(UnitType.Zerg_Drone, 1);
        PlanEvents.blocked(plan, PlanBlocker.NO_LARVA);

        assertEquals(1, blockers.size());
        assertEquals(PlanBlocker.NO_LARVA, blockers.get(0));
    }

    @Test
    void planIdIsUniqueAndStableAcrossTransitions() {
        Plan first = new UnitPlan(UnitType.Zerg_Drone, 1);
        Plan second = new UnitPlan(UnitType.Zerg_Drone, 1);

        int id = first.getPlanId();
        first.setState(PlanState.SCHEDULE);
        first.setState(PlanState.BUILDING);
        first.setState(PlanState.COMPLETE);

        assertEquals(id, first.getPlanId());
        assertTrue(second.getPlanId() != id);
    }

    @Test
    void removeWhereStampsTheCancelSite() {
        ProductionQueue queue = new ProductionQueue();
        Plan plan = new UnitPlan(UnitType.Zerg_Drone, 1);
        queue.add(plan);

        queue.removeWhere(p -> true, PlanCancelSite.REACTION_CANNON_RUSH_DRONE, p -> { });

        assertEquals(PlanCancelSite.REACTION_CANNON_RUSH_DRONE, plan.getCancelSite());
        assertEquals(PlanCancelReason.REACTION_DEFENSE, plan.getCancelReason());
    }

    @Test
    void theFirstCancellationOwnsTheReason() {
        Plan plan = new UnitPlan(UnitType.Zerg_Drone, 1);
        plan.setCancelSite(PlanCancelSite.REACTION_CANNON_RUSH_DRONE);
        plan.setState(PlanState.CANCELLED);
        plan.setCancelSite(PlanCancelSite.PRODUCTION_EXCESS_OVERLORD);

        assertEquals(PlanCancelSite.REACTION_CANNON_RUSH_DRONE, plan.getCancelSite());
    }

    @Test
    void aLivePlanHasNoCancelReason() {
        Plan plan = new UnitPlan(UnitType.Zerg_Drone, 1);

        assertEquals(PlanCancelReason.UNKNOWN, plan.getCancelReason());
    }
}
