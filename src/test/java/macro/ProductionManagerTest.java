package macro;

import bwapi.UnitType;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.PlanCancelSource;
import macro.plan.PlanBlocker;
import macro.plan.PlanState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProductionManagerTest {

    private Plan spire(PlanState state) {
        Plan plan = new BuildingPlan(UnitType.Zerg_Spire, 1000);
        plan.setState(state);
        return plan;
    }

    @Test
    void buildingClaimWithoutExecutorIsCancelledAsExecutorLost() {
        assertEquals(
                PlanCancelSource.PRODUCTION_EXECUTOR_LOST,
                ProductionManager.buildAheadCancellationSource(spire(PlanState.BUILDING), true, false));
    }

    @Test
    void scheduledClaimRetainsItsExecutorAssignmentWindow() {
        assertNull(ProductionManager.buildAheadCancellationSource(spire(PlanState.SCHEDULE), true, false));
    }

    @Test
    void buildingClaimWithExecutorRemainsActive() {
        assertNull(ProductionManager.buildAheadCancellationSource(spire(PlanState.BUILDING), true, true));
    }

    @Test
    void prerequisiteLossCancelsScheduledClaimBeforeExecutorAssignment() {
        assertEquals(
                PlanCancelSource.PRODUCTION_SCHEDULED_PREREQUISITE_LOST,
                ProductionManager.buildAheadCancellationSource(spire(PlanState.SCHEDULE), false, false));
    }

    @Test
    void affordableRederivedPlanIsBlockedDuringBackoff() {
        int frame = 1000;
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan original = spire(PlanState.SCHEDULE);
        slot.claim(original, frame, frame + 100);
        slot.releaseWithBackoff(original, frame);

        PlanBlocker blocker = ProductionManager.buildAheadBlocker(
                slot,
                UnitType.Zerg_Spire,
                frame + 1,
                false,
                false,
                frame + 1);

        assertEquals(PlanBlocker.BUILD_AHEAD_BACKOFF, blocker);
    }
}
