package macro;

import bwapi.UnitType;
import macro.ProductionManager.PlanScheduler;
import macro.ProductionManager.ScanOutcome;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.PlanCancelSource;
import macro.plan.PlanState;
import macro.plan.UnitPlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import telemetry.PlanEventSink;
import telemetry.PlanEvents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionManagerTest {

    private static final int FRAME = 6253;

    private Plan spire(PlanState state) {
        Plan plan = new BuildingPlan(UnitType.Zerg_Spire, 1000);
        plan.setState(state);
        return plan;
    }

    private Plan hatchery() {
        return new BuildingPlan(UnitType.Zerg_Hatchery, 1);
    }

    private Plan mutalisk() {
        return new UnitPlan(UnitType.Zerg_Mutalisk, UnitPlan.ADVANCED_UNIT_PRIORITY);
    }

    private Plan zergling() {
        return new UnitPlan(UnitType.Zerg_Zergling, FRAME);
    }

    private static final class Recorder implements PlanScheduler {

        private final Map<Plan, PlanBlocker> blockers = new HashMap<>();
        private final Map<Plan, Boolean> bankClaimedAhead = new HashMap<>();
        private final List<Plan> examined = new ArrayList<>();

        private Recorder block(Plan plan, PlanBlocker blocker) {
            blockers.put(plan, blocker);
            return this;
        }

        @Override
        public PlanBlocker schedule(Plan plan, boolean claimedAhead) {
            examined.add(plan);
            bankClaimedAhead.put(plan, claimedAhead);
            return blockers.getOrDefault(plan, PlanBlocker.NONE);
        }
    }

    private final List<PlanBlocker> reportedBlockers = new ArrayList<>();

    private final List<Plan> reportedPlans = new ArrayList<>();

    private PlanEventSink blockerRecorder() {
        return new PlanEventSink() {
            @Override
            public void onEnqueue(Plan plan) {
            }

            @Override
            public void onStateChange(Plan plan, PlanState from, PlanState to) {
            }

            @Override
            public void onBlocked(Plan plan, PlanBlocker blocker) {
                reportedPlans.add(plan);
                reportedBlockers.add(blocker);
            }
        };
    }

    @AfterEach
    void clearSink() {
        PlanEvents.clear();
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

    @Test
    void onlyAResourceShortfallClaimsTheBank() {
        for (PlanBlocker blocker : PlanBlocker.values()) {
            assertEquals(blocker == PlanBlocker.RESOURCES, ProductionManager.claimsBank(blocker), blocker.name());
        }
    }

    @Test
    void anAffordablePlanBehindALarvaBlockedPlanIsScheduledTheSameScan() {
        Plan drone = new UnitPlan(UnitType.Zerg_Drone, 1);
        Plan ling = zergling();
        Recorder scheduler = new Recorder().block(drone, PlanBlocker.NO_LARVA);

        ScanOutcome outcome = ProductionManager.scanPlans(Arrays.asList(drone, ling), scheduler);

        assertEquals(Collections.singletonList(ling), outcome.scheduled);
        assertEquals(Collections.singletonList(drone), outcome.requeued);
    }

    @Test
    void anAffordablePlanBehindAPositionBlockedPlanIsScheduledTheSameScan() {
        Plan hatchery = hatchery();
        Plan ling = zergling();
        Recorder scheduler = new Recorder().block(hatchery, PlanBlocker.NO_BUILD_POSITION);

        ScanOutcome outcome = ProductionManager.scanPlans(Arrays.asList(hatchery, ling), scheduler);

        assertEquals(Collections.singletonList(ling), outcome.scheduled);
        assertEquals(Collections.singletonList(hatchery), outcome.requeued);
    }

    @Test
    void theScanExaminesEveryPlanInPriorityOrderPastAnyBlocker() {
        for (PlanBlocker blocker : PlanBlocker.values()) {
            if (blocker == PlanBlocker.NONE) {
                continue;
            }
            Plan hatchery = hatchery();
            Plan muta = mutalisk();
            Plan ling = zergling();
            Recorder scheduler = new Recorder().block(hatchery, blocker).block(muta, blocker);

            ProductionManager.scanPlans(Arrays.asList(hatchery, muta, ling), scheduler);

            assertEquals(Arrays.asList(hatchery, muta, ling), scheduler.examined, blocker.name());
        }
    }

    @Test
    void aNonResourceBlockerLeavesTheBankOpenToThePlansBehindIt() {
        for (PlanBlocker blocker : PlanBlocker.values()) {
            if (blocker == PlanBlocker.NONE || blocker == PlanBlocker.RESOURCES) {
                continue;
            }
            Plan hatchery = hatchery();
            Plan muta = mutalisk();
            Recorder scheduler = new Recorder().block(hatchery, blocker);

            ProductionManager.scanPlans(Arrays.asList(hatchery, muta), scheduler);

            assertFalse(scheduler.bankClaimedAhead.get(muta), blocker.name());
        }
    }

    @Test
    void aResourceBlockedPlanClaimsTheBankForEveryPlanBehindIt() {
        Plan hatchery = hatchery();
        Plan muta = mutalisk();
        Plan ling = zergling();
        Recorder scheduler = new Recorder().block(hatchery, PlanBlocker.RESOURCES);

        ProductionManager.scanPlans(Arrays.asList(hatchery, muta, ling), scheduler);

        assertFalse(scheduler.bankClaimedAhead.get(hatchery));
        assertTrue(scheduler.bankClaimedAhead.get(muta));
        assertTrue(scheduler.bankClaimedAhead.get(ling));
    }

    @Test
    void everySkippedPlanReportsItsBlocker() {
        PlanEvents.register(blockerRecorder());
        Plan hatchery = hatchery();
        Plan muta = mutalisk();
        Plan ling = zergling();
        Recorder scheduler = new Recorder()
                .block(hatchery, PlanBlocker.NO_BUILD_POSITION)
                .block(muta, PlanBlocker.RESOURCES);

        ProductionManager.scanPlans(Arrays.asList(hatchery, muta, ling), scheduler);

        assertEquals(Arrays.asList(hatchery, muta), reportedPlans);
        assertEquals(Arrays.asList(PlanBlocker.NO_BUILD_POSITION, PlanBlocker.RESOURCES), reportedBlockers);
    }

    @Test
    void anAffordableUnitIsNeverHeld() {
        BuildAheadSlot slot = new BuildAheadSlot();
        slot.claim(mutalisk(), FRAME, FRAME + 100);

        PlanBlocker blocker = ProductionManager.unitAheadBlocker(
                slot, UnitType.Zerg_Zergling, FRAME, false, true, Integer.MAX_VALUE);

        assertEquals(PlanBlocker.NONE, blocker);
    }

    @Test
    void anAffordableUnitSchedulesDuringItsTypeBackoff() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan evicted = mutalisk();
        slot.claim(evicted, FRAME, FRAME + 100);
        slot.releaseWithBackoff(evicted, FRAME);

        PlanBlocker blocker = ProductionManager.unitAheadBlocker(
                slot, UnitType.Zerg_Mutalisk, FRAME + 1, false, false, FRAME + 1);

        assertEquals(PlanBlocker.NONE, blocker);
    }

    @Test
    void aUnitBehindABankClaimCannotHoldItsCost() {
        PlanBlocker blocker = ProductionManager.unitAheadBlocker(
                new BuildAheadSlot(), UnitType.Zerg_Mutalisk, FRAME, true, true, FRAME + 100);

        assertEquals(PlanBlocker.BUILD_AHEAD_SLOT_TAKEN, blocker);
    }

    @Test
    void oneUnitHoldsAtATime() {
        BuildAheadSlot slot = new BuildAheadSlot();
        slot.claim(mutalisk(), FRAME, FRAME + 100);

        PlanBlocker blocker = ProductionManager.unitAheadBlocker(
                slot, UnitType.Zerg_Zergling, FRAME, true, false, FRAME + 100);

        assertEquals(PlanBlocker.BUILD_AHEAD_SLOT_TAKEN, blocker);
    }

    @Test
    void anEvictedUnitTypeWaitsOutItsBackoffBeforeHoldingAgain() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan evicted = mutalisk();
        slot.claim(evicted, FRAME, FRAME + 100);
        slot.releaseWithBackoff(evicted, FRAME);

        PlanBlocker blocker = ProductionManager.unitAheadBlocker(
                slot, UnitType.Zerg_Mutalisk, FRAME + 1, true, false, FRAME + 100);

        assertEquals(PlanBlocker.BUILD_AHEAD_BACKOFF, blocker);
    }

    @Test
    void aUnitWithNoIncomeTowardsItsCostIsNotHeld() {
        PlanBlocker blocker = ProductionManager.unitAheadBlocker(
                new BuildAheadSlot(), UnitType.Zerg_Mutalisk, FRAME, true, false, Integer.MAX_VALUE);

        assertEquals(PlanBlocker.NO_INCOME, blocker);
    }

    @Test
    void aUnitShortByMoreThanOneBuildCycleWaitsOnResources() {
        int predicted = FRAME + UnitType.Zerg_Mutalisk.buildTime() + 1;

        PlanBlocker blocker = ProductionManager.unitAheadBlocker(
                new BuildAheadSlot(), UnitType.Zerg_Mutalisk, FRAME, true, false, predicted);

        assertEquals(PlanBlocker.RESOURCES, blocker);
    }

    @Test
    void aUnitShortByLessThanOneBuildCycleHoldsItsCost() {
        int predicted = FRAME + UnitType.Zerg_Mutalisk.buildTime();

        PlanBlocker blocker = ProductionManager.unitAheadBlocker(
                new BuildAheadSlot(), UnitType.Zerg_Mutalisk, FRAME, true, false, predicted);

        assertEquals(PlanBlocker.NONE, blocker);
    }

    @Test
    void aHeldHeadOfQueueUnitKeepsLowerPriorityPlansOffTheBank() {
        int[] bank = {UnitType.Zerg_Mutalisk.mineralPrice() - 10};
        BuildAheadSlot slot = new BuildAheadSlot();
        PlanScheduler scheduler = (plan, claimedAhead) -> {
            UnitType unit = plan.getPlannedUnit();
            boolean cannotAfford = bank[0] < unit.mineralPrice();
            PlanBlocker blocker = ProductionManager.unitAheadBlocker(
                    slot, unit, FRAME, cannotAfford, claimedAhead, FRAME + 100);
            if (blocker != PlanBlocker.NONE) {
                return blocker;
            }
            if (cannotAfford) {
                slot.claim(plan, FRAME, FRAME + 100);
            }
            bank[0] -= unit.mineralPrice();
            return PlanBlocker.NONE;
        };
        Plan muta = mutalisk();
        Plan firstLing = zergling();
        Plan secondLing = zergling();

        ScanOutcome outcome = ProductionManager.scanPlans(Arrays.asList(muta, firstLing, secondLing), scheduler);

        assertEquals(Collections.singletonList(muta), outcome.scheduled);
        assertEquals(Arrays.asList(firstLing, secondLing), outcome.requeued);
        assertTrue(bank[0] < 0);
    }
}
