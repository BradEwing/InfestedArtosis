package telemetry;

import bwapi.UnitType;
import macro.ProductionQueue;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.PlanCancelReason;
import macro.plan.PlanCancelSource;
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
    private final List<String> withheld = new ArrayList<>();

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

            @Override
            public void onWithheld(UnitType unitType, PlanBlocker blocker) {
                withheld.add(unitType + ":" + blocker);
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
    void removeWhereStampsTheCancelSource() {
        ProductionQueue queue = new ProductionQueue();
        Plan plan = new UnitPlan(UnitType.Zerg_Drone, 1);
        queue.add(plan);

        queue.removeWhere(p -> true, PlanCancelSource.REACTION_CANNON_RUSH_DRONE, p -> { });

        assertEquals(PlanCancelSource.REACTION_CANNON_RUSH_DRONE, plan.getCancelSource());
        assertEquals(PlanCancelReason.REACTION_DEFENSE, plan.getCancelReason());
    }

    @Test
    void theFirstCancellationOwnsTheReason() {
        Plan plan = new UnitPlan(UnitType.Zerg_Drone, 1);
        plan.setCancelSource(PlanCancelSource.REACTION_CANNON_RUSH_DRONE);
        plan.setState(PlanState.CANCELLED);
        plan.setCancelSource(PlanCancelSource.PRODUCTION_EXCESS_OVERLORD);

        assertEquals(PlanCancelSource.REACTION_CANNON_RUSH_DRONE, plan.getCancelSource());
    }

    @Test
    void aMissingCancelSourceHasUnknownReason() {
        Plan plan = new UnitPlan(UnitType.Zerg_Drone, 1);

        assertEquals(PlanCancelReason.UNKNOWN, plan.getCancelReason());
    }

    @Test
    void withheldHookCarriesTheUnitAndTheBlocker() {
        PlanEvents.register(recorder());

        PlanEvents.withheld(UnitType.Zerg_Mutalisk, PlanBlocker.INSUFFICIENT_GATHERERS);

        assertEquals(1, withheld.size());
        assertEquals("Zerg_Mutalisk:INSUFFICIENT_GATHERERS", withheld.get(0));
        assertTrue(enqueued.isEmpty());
    }

    @Test
    void withheldWithNoSinkRegisteredIsANoOp() {
        PlanEvents.withheld(UnitType.Zerg_Mutalisk, PlanBlocker.TECH_MISSING);

        assertTrue(withheld.isEmpty());
    }

    @Test
    void aSpecificReasonOverridesTheSourceDefault() {
        Plan plan = new UnitPlan(UnitType.Zerg_Mutalisk, 1);

        plan.setCancelSource(PlanCancelSource.PRODUCTION_IMPOSSIBLE_SWEEP, PlanCancelReason.INSUFFICIENT_GATHERERS);

        assertEquals(PlanCancelSource.PRODUCTION_IMPOSSIBLE_SWEEP, plan.getCancelSource());
        assertEquals(PlanCancelReason.INSUFFICIENT_GATHERERS, plan.getCancelReason());
    }

    @Test
    void theFirstCancellationOwnsTheSpecificReasonToo() {
        Plan plan = new UnitPlan(UnitType.Zerg_Mutalisk, 1);
        plan.setCancelSource(PlanCancelSource.PRODUCTION_IMPOSSIBLE_SWEEP, PlanCancelReason.TECH_MISSING);
        plan.setState(PlanState.CANCELLED);
        plan.setCancelSource(PlanCancelSource.PRODUCTION_IMPOSSIBLE_SWEEP, PlanCancelReason.INSUFFICIENT_GATHERERS);

        assertEquals(PlanCancelReason.TECH_MISSING, plan.getCancelReason());
    }

    @Test
    void sweepBlockersMapToTheirOwnCancelReason() {
        assertEquals(PlanCancelReason.INSUFFICIENT_GATHERERS, PlanBlocker.INSUFFICIENT_GATHERERS.cancelReason());
        assertEquals(PlanCancelReason.TECH_MISSING, PlanBlocker.TECH_MISSING.cancelReason());
        assertEquals(PlanCancelReason.NO_LARVA, PlanBlocker.NO_LARVA.cancelReason());
        assertEquals(PlanCancelReason.SUPPLY_BLOCKED, PlanBlocker.SUPPLY.cancelReason());
        assertEquals(PlanCancelReason.PREREQUISITE_MISSING, PlanBlocker.NO_PRODUCER.cancelReason());
    }
}
