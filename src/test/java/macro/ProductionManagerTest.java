package macro;

import bwapi.UnitType;
import bwapi.UpgradeType;
import macro.ProductionManager.PlanScheduler;
import macro.ProductionManager.ScanOutcome;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.PlanCancelSource;
import macro.plan.PlanState;
import macro.plan.UnitPlan;
import macro.plan.UpgradePlan;
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

    /** A drone walking to a far expansion; its claim-time hold ends near 815 frames. */
    private static final int HATCHERY_TRAVEL_FRAMES = 455;

    private Plan spire(PlanState state) {
        Plan plan = new BuildingPlan(UnitType.Zerg_Spire, 1000);
        plan.setState(state);
        return plan;
    }

    private Plan metabolicBoost() {
        return new UpgradePlan(UpgradeType.Metabolic_Boost, 3330);
    }

    private Plan extractor() {
        return new BuildingPlan(UnitType.Zerg_Extractor, 3649);
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

    private Plan overlord(int priority) {
        return new UnitPlan(UnitType.Zerg_Overlord, priority);
    }

    private List<Plan> hydralisks(int count, int firstPriority) {
        List<Plan> plans = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            plans.add(new UnitPlan(UnitType.Zerg_Hydralisk, firstPriority + i));
        }
        return plans;
    }

    private List<Plan> blockedOverlordBacklog() {
        return Arrays.asList(overlord(6708), overlord(6716), overlord(8031), overlord(9903));
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

    /**
     * The building path of the scheduler over a mineral bank. A plan that clears buildAheadBlocker
     * takes the slot and reserves its cost, leaving the bank short for everything behind it.
     */
    private static final class Bank implements PlanScheduler {

        private final BuildAheadSlot slot = new BuildAheadSlot();

        private int minerals;

        private Bank(int minerals) {
            this.minerals = minerals;
        }

        @Override
        public PlanBlocker schedule(Plan plan, boolean bankClaimedAhead) {
            UnitType building = plan.getPlannedUnit();
            PlanBlocker blocker = ProductionManager.buildAheadBlocker(
                    slot,
                    plan,
                    FRAME,
                    minerals < building.mineralPrice(),
                    bankClaimedAhead,
                    FRAME + 100);
            if (blocker != PlanBlocker.NONE) {
                return blocker;
            }
            slot.claim(plan, FRAME, FRAME + 100);
            minerals -= building.mineralPrice();
            return PlanBlocker.NONE;
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
    void aQueuedOverlordBacklogEarnsNoSupplyHeadroom() {
        List<Plan> hydralisks = hydralisks(7, 11000);
        List<Plan> withBacklog = new ArrayList<>(blockedOverlordBacklog());
        withBacklog.addAll(hydralisks);

        List<Integer> withoutBacklog = ProductionManager.overlordInsertPriorities(
                Collections.<Plan>emptyList(), hydralisks, 1, 16, 53);
        List<Integer> withQueuedBacklog = ProductionManager.overlordInsertPriorities(
                Collections.<Plan>emptyList(), withBacklog, 1, 16, 53);

        assertEquals(Collections.singletonList(11005), withoutBacklog);
        assertEquals(withoutBacklog, withQueuedBacklog);
    }

    @Test
    void aScheduledOverlordCreditsTheHeadroomItWillProvide() {
        List<Integer> priorities = ProductionManager.overlordInsertPriorities(
                Collections.singletonList(overlord(6708)), hydralisks(7, 11000), 1, 16, 53);

        assertTrue(priorities.isEmpty());
    }

    @Test
    void eachInsertOnlyPaysForItsOwnOverlord() {
        List<Integer> priorities = ProductionManager.overlordInsertPriorities(
                Collections.<Plan>emptyList(), hydralisks(23, 11000), 1, 16, 53);

        assertEquals(Arrays.asList(11005, 11013, 11021), priorities);
    }

    @Test
    void anAffordableBuildingSchedulesDuringItsBackoff() {
        int frame = 1000;
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan evicted = spire(PlanState.SCHEDULE);
        slot.claim(evicted, frame, frame + 100);
        slot.releaseWithBackoff(evicted, frame);

        PlanBlocker blocker = ProductionManager.buildAheadBlocker(
                slot,
                evicted,
                frame + 1,
                false,
                false,
                frame + 1);

        assertEquals(PlanBlocker.NONE, blocker);
    }

    @Test
    void anEvictedPlanIsBlockedDuringItsBackoff() {
        int frame = 1000;
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan evicted = spire(PlanState.SCHEDULE);
        slot.claim(evicted, frame, frame + 100);
        slot.releaseWithBackoff(evicted, frame);

        PlanBlocker blocker = ProductionManager.buildAheadBlocker(
                slot,
                evicted,
                frame + 1,
                true,
                false,
                frame + 1);

        assertEquals(PlanBlocker.BUILD_AHEAD_BACKOFF, blocker);
    }

    @Test
    void anEvictionDoesNotBlockAnotherPlanOfTheSameBuilding() {
        int frame = 1000;
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan evicted = spire(PlanState.SCHEDULE);
        slot.claim(evicted, frame, frame + 100);
        slot.releaseWithBackoff(evicted, frame);

        PlanBlocker blocker = ProductionManager.buildAheadBlocker(
                slot,
                spire(PlanState.PLANNED),
                frame + 1,
                true,
                false,
                frame + 1);

        assertEquals(PlanBlocker.NONE, blocker);
    }

    @Test
    void anEvictedPlanCannotRestartItsHoldOnTheEvictionFrame() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan plan = hatchery();
        slot.claim(plan, FRAME, FRAME + 20, HATCHERY_TRAVEL_FRAMES);
        int evictionFrame = BuildAheadSlot.deadline(FRAME, FRAME + 20, HATCHERY_TRAVEL_FRAMES);
        slot.releaseWithBackoff(plan, evictionFrame);

        PlanBlocker blocker = ProductionManager.buildAheadBlocker(
                slot, plan, evictionFrame, false, false, evictionFrame + 20);
        slot.claim(plan, evictionFrame, evictionFrame + 20, HATCHERY_TRAVEL_FRAMES);

        assertEquals(PlanBlocker.NONE, blocker);
        assertEquals(evictionFrame - FRAME, slot.heldFrames(plan, evictionFrame));
    }

    @Test
    void anEvictedPlanWithNoHoldLeftWaitsOutItsBackoff() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan plan = hatchery();
        slot.claim(plan, FRAME, FRAME + 20, HATCHERY_TRAVEL_FRAMES);
        int evictionFrame = FRAME + BuildAheadSlot.TOTAL_HOLD_FRAMES;
        slot.releaseWithBackoff(plan, evictionFrame);

        PlanBlocker blocker = ProductionManager.buildAheadBlocker(
                slot, plan, evictionFrame, false, false, evictionFrame + 20);

        assertEquals(PlanBlocker.BUILD_AHEAD_BACKOFF, blocker);
    }

    @Test
    void aSpentHoldClaimsAfreshOnceItsBackoffExpires() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan plan = hatchery();
        slot.claim(plan, FRAME, FRAME + 20, HATCHERY_TRAVEL_FRAMES);
        int evictionFrame = FRAME + BuildAheadSlot.TOTAL_HOLD_FRAMES;
        slot.releaseWithBackoff(plan, evictionFrame);
        int retryFrame = evictionFrame + BuildAheadSlot.BACKOFF_FRAMES;

        PlanBlocker blocker = ProductionManager.buildAheadBlocker(
                slot, plan, retryFrame, false, false, retryFrame + 20);
        slot.claim(plan, retryFrame, retryFrame + 20, HATCHERY_TRAVEL_FRAMES);

        assertEquals(PlanBlocker.NONE, blocker);
        assertEquals(0, slot.heldFrames(plan, retryFrame));
    }

    @Test
    void aBuildingClaimIsRefreshedAfterItsBuilderLaunches() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan plan = hatchery();
        slot.claim(plan, FRAME, FRAME + 20, HATCHERY_TRAVEL_FRAMES);
        plan.setState(PlanState.BUILDING);
        int claimDeadline = BuildAheadSlot.deadline(FRAME, FRAME + 20, HATCHERY_TRAVEL_FRAMES);
        int frame = claimDeadline - 1;

        ProductionManager.refreshBuildAheadPredictions(slot, frame + 20, p -> HATCHERY_TRAVEL_FRAMES);

        assertTrue(slot.stalled(claimDeadline).isEmpty());
    }

    @Test
    void refreshingALaunchedBuilderLeavesThePredictionItClearsMineralsBy() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan plan = hatchery();
        plan.setPredictedReadyFrame(FRAME + 20);
        slot.claim(plan, FRAME, FRAME + 20, HATCHERY_TRAVEL_FRAMES);
        plan.setState(PlanState.BUILDING);

        ProductionManager.refreshBuildAheadPredictions(slot, FRAME + 800, p -> HATCHERY_TRAVEL_FRAMES);

        assertEquals(FRAME + 20, plan.getPredictedReadyFrame());
    }

    @Test
    void refreshingAParkedBuilderRetimesItsDispatch() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan plan = hatchery();
        plan.setPredictedReadyFrame(FRAME + 20);
        slot.claim(plan, FRAME, FRAME + 20, HATCHERY_TRAVEL_FRAMES);
        plan.setState(PlanState.SCHEDULE);

        ProductionManager.refreshBuildAheadPredictions(slot, FRAME + 800, p -> HATCHERY_TRAVEL_FRAMES);

        assertEquals(FRAME + 800, plan.getPredictedReadyFrame());
    }

    @Test
    void aClaimOutsideTheBuilderPipelineIsNotRefreshed() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan plan = hatchery();
        slot.claim(plan, FRAME, FRAME + 20, HATCHERY_TRAVEL_FRAMES);
        plan.setState(PlanState.MORPHING);
        int claimDeadline = BuildAheadSlot.deadline(FRAME, FRAME + 20, HATCHERY_TRAVEL_FRAMES);

        ProductionManager.refreshBuildAheadPredictions(slot, claimDeadline + 19, p -> HATCHERY_TRAVEL_FRAMES);

        assertFalse(slot.stalled(claimDeadline).isEmpty());
    }

    @Test
    void anUpgradeNoWorkerCanGatherForReportsNoIncome() {
        assertEquals(PlanBlocker.NO_INCOME, ProductionManager.shortfallBlocker(Integer.MAX_VALUE));
    }

    @Test
    void anUpgradeIncomeWillCoverReportsAResourceShortfall() {
        assertEquals(PlanBlocker.RESOURCES, ProductionManager.shortfallBlocker(FRAME + 100));
    }

    @Test
    void aGasUpgradeWithNoGasIncomeLeavesTheBankToTheExtractorBehindIt() {
        Plan speed = metabolicBoost();
        Plan extractor = extractor();
        Recorder scheduler = new Recorder()
                .block(speed, ProductionManager.shortfallBlocker(Integer.MAX_VALUE));

        ProductionManager.scanPlans(Arrays.asList(speed, extractor), scheduler);

        assertFalse(scheduler.bankClaimedAhead.get(extractor));
    }

    @Test
    void aGasUpgradeTheBankAlreadyCoversStillHoldsTheBank() {
        Plan speed = metabolicBoost();
        Plan extractor = extractor();
        Recorder scheduler = new Recorder()
                .block(speed, ProductionManager.shortfallBlocker(FRAME + 100));

        ProductionManager.scanPlans(Arrays.asList(speed, extractor), scheduler);

        assertTrue(scheduler.bankClaimedAhead.get(extractor));
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
                slot, zergling(), FRAME, false, true, true, Integer.MAX_VALUE);

        assertEquals(PlanBlocker.NONE, blocker);
    }

    @Test
    void anAffordableUnitSchedulesDuringItsBackoff() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan evicted = mutalisk();
        slot.claim(evicted, FRAME, FRAME + 100);
        slot.releaseWithBackoff(evicted, FRAME);

        PlanBlocker blocker = ProductionManager.unitAheadBlocker(
                slot, evicted, FRAME + 1, false, false, false, FRAME + 1);

        assertEquals(PlanBlocker.NONE, blocker);
    }

    @Test
    void aUnitBehindABankClaimCannotHoldItsCost() {
        PlanBlocker blocker = ProductionManager.unitAheadBlocker(
                new BuildAheadSlot(), mutalisk(), FRAME, true, true, false, FRAME + 100);

        assertEquals(PlanBlocker.BUILD_AHEAD_SLOT_TAKEN, blocker);
    }

    @Test
    void oneUnitHoldsAtATime() {
        BuildAheadSlot slot = new BuildAheadSlot();
        slot.claim(mutalisk(), FRAME, FRAME + 100);

        PlanBlocker blocker = ProductionManager.unitAheadBlocker(
                slot, zergling(), FRAME, true, false, false, FRAME + 100);

        assertEquals(PlanBlocker.BUILD_AHEAD_SLOT_TAKEN, blocker);
    }

    @Test
    void anEvictedUnitWaitsOutItsBackoffBeforeHoldingAgain() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan evicted = mutalisk();
        slot.claim(evicted, FRAME, FRAME + 100);
        slot.releaseWithBackoff(evicted, FRAME);

        PlanBlocker blocker = ProductionManager.unitAheadBlocker(
                slot, evicted, FRAME + 1, true, false, false, FRAME + 100);

        assertEquals(PlanBlocker.BUILD_AHEAD_BACKOFF, blocker);
    }

    @Test
    void aUnitWithNoIncomeTowardsItsCostIsNotHeld() {
        PlanBlocker blocker = ProductionManager.unitAheadBlocker(
                new BuildAheadSlot(), mutalisk(), FRAME, true, false, false, Integer.MAX_VALUE);

        assertEquals(PlanBlocker.NO_INCOME, blocker);
    }

    @Test
    void aUnitShortByMoreThanOneBuildCycleWaitsOnResources() {
        int predicted = FRAME + UnitType.Zerg_Mutalisk.buildTime() + 1;

        PlanBlocker blocker = ProductionManager.unitAheadBlocker(
                new BuildAheadSlot(), mutalisk(), FRAME, true, false, false, predicted);

        assertEquals(PlanBlocker.RESOURCES, blocker);
    }

    @Test
    void aUnitShortByLessThanOneBuildCycleHoldsItsCost() {
        int predicted = FRAME + UnitType.Zerg_Mutalisk.buildTime();

        PlanBlocker blocker = ProductionManager.unitAheadBlocker(
                new BuildAheadSlot(), mutalisk(), FRAME, true, false, false, predicted);

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
                    slot, plan, FRAME, cannotAfford, claimedAhead, false, FRAME + 100);
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

    @Test
    void aBuildingMorphWithNoFreeProducerNeverTakesTheSlot() {
        assertEquals(PlanBlocker.NO_PRODUCER,
                ProductionManager.buildingMorphBlocker(UnitType.Zerg_Lair, false));
        assertEquals(PlanBlocker.NO_PRODUCER,
                ProductionManager.buildingMorphBlocker(UnitType.Zerg_Hive, false));
        assertEquals(PlanBlocker.NO_PRODUCER,
                ProductionManager.buildingMorphBlocker(UnitType.Zerg_Sunken_Colony, false));
        assertEquals(PlanBlocker.NO_PRODUCER,
                ProductionManager.buildingMorphBlocker(UnitType.Zerg_Spore_Colony, false));
    }

    @Test
    void aBuildingMorphSchedulesOnceAProducerIsFree() {
        assertEquals(PlanBlocker.NONE,
                ProductionManager.buildingMorphBlocker(UnitType.Zerg_Lair, true));
        assertEquals(PlanBlocker.NONE,
                ProductionManager.buildingMorphBlocker(UnitType.Zerg_Hive, true));
    }

    @Test
    void aDroneBuiltBuildingIsNotGatedOnAMorphProducer() {
        assertEquals(PlanBlocker.NONE,
                ProductionManager.buildingMorphBlocker(UnitType.Zerg_Hatchery, false));
        assertEquals(PlanBlocker.NONE,
                ProductionManager.buildingMorphBlocker(UnitType.Zerg_Spawning_Pool, false));
        assertEquals(PlanBlocker.NONE,
                ProductionManager.buildingMorphBlocker(UnitType.Zerg_Extractor, false));
        assertEquals(PlanBlocker.NONE,
                ProductionManager.buildingMorphBlocker(UnitType.Zerg_Spire, false));
    }

    @Test
    void aUnitCannotSpendAgainstAScheduledBuildingsReservation() {
        Plan drone = new UnitPlan(UnitType.Zerg_Drone, 1);
        int predicted = FRAME + UnitType.Zerg_Drone.buildTime() - 1;

        PlanBlocker blocker = ProductionManager.unitAheadBlocker(
                new BuildAheadSlot(), drone, FRAME, true, false, true, predicted);

        assertEquals(PlanBlocker.BUILD_AHEAD_SLOT_TAKEN, blocker);
    }

    @Test
    void aUnitSchedulesOnceNoBuildingHoldsTheBank() {
        Plan drone = new UnitPlan(UnitType.Zerg_Drone, 1);
        int predicted = FRAME + UnitType.Zerg_Drone.buildTime() - 1;

        PlanBlocker blocker = ProductionManager.unitAheadBlocker(
                new BuildAheadSlot(), drone, FRAME, true, false, false, predicted);

        assertEquals(PlanBlocker.NONE, blocker);
    }

    @Test
    void anOverlordIsNotBarredByAScheduledBuildingsReservation() {
        int predicted = FRAME + UnitType.Zerg_Overlord.buildTime() - 1;

        PlanBlocker blocker = ProductionManager.unitAheadBlocker(
                new BuildAheadSlot(), overlord(1), FRAME, true, false, true, predicted);

        assertEquals(PlanBlocker.NONE, blocker);
    }

    @Test
    void onlyABuildingHoldingTheBankBarsAUnit() {
        assertTrue(ProductionManager.isBarredByBuildingReservation(UnitType.Zerg_Drone, true));
        assertFalse(ProductionManager.isBarredByBuildingReservation(UnitType.Zerg_Drone, false));
        assertFalse(ProductionManager.isBarredByBuildingReservation(UnitType.Zerg_Overlord, true));
    }

    @Test
    void anOverlordIsNeverSupplyBlocked() {
        assertFalse(ProductionManager.isSupplyBlocked(UnitType.Zerg_Overlord, 0));
    }

    @Test
    void aLarvaMorphIsBlockedBelowItsOwnSupplyCost() {
        assertTrue(ProductionManager.isSupplyBlocked(UnitType.Zerg_Hydralisk, 1));
        assertFalse(ProductionManager.isSupplyBlocked(UnitType.Zerg_Hydralisk, 2));
    }

    @Test
    void aPairHatchedMorphCostsSupplyForBothUnits() {
        assertTrue(ProductionManager.isSupplyBlocked(UnitType.Zerg_Zergling, 1));
        assertFalse(ProductionManager.isSupplyBlocked(UnitType.Zerg_Zergling, 2));
    }

    @Test
    void aMorphFromAnExistingUnitIsNotSupplyBlocked() {
        assertFalse(ProductionManager.isSupplyBlocked(UnitType.Zerg_Lurker, 0));
        assertFalse(ProductionManager.isSupplyBlocked(UnitType.Zerg_Guardian, 0));
        assertFalse(ProductionManager.isSupplyBlocked(UnitType.Zerg_Devourer, 0));
    }

    @Test
    void aMorphFromAnExistingUnitIsNotLarvaBlocked() {
        assertFalse(ProductionManager.isLarvaBlocked(UnitType.Zerg_Lurker, false));
        assertFalse(ProductionManager.isLarvaBlocked(UnitType.Zerg_Guardian, false));
        assertFalse(ProductionManager.isLarvaBlocked(UnitType.Zerg_Devourer, false));
    }

    @Test
    void aLarvaMorphIsBlockedWithoutFreeLarva() {
        assertTrue(ProductionManager.isLarvaBlocked(UnitType.Zerg_Zergling, false));
        assertTrue(ProductionManager.isLarvaBlocked(UnitType.Zerg_Hydralisk, false));
        assertTrue(ProductionManager.isLarvaBlocked(UnitType.Zerg_Overlord, false));
    }

    @Test
    void aLarvaMorphSchedulesWithFreeLarva() {
        assertFalse(ProductionManager.isLarvaBlocked(UnitType.Zerg_Zergling, true));
    }

    @Test
    void everyScheduledLurkerWithAHydraliskSurvives() {
        assertEquals(0, ProductionManager.excessLurkerPlans(4, 4, 0));
        assertEquals(0, ProductionManager.excessLurkerPlans(1, 6, 0));
    }

    @Test
    void onlyTheOversubscribedLurkerPlansAreCancelled() {
        assertEquals(1, ProductionManager.excessLurkerPlans(4, 3, 0));
        assertEquals(3, ProductionManager.excessLurkerPlans(3, 0, 0));
    }

    @Test
    void aMorphingHydraliskStillCountsAsItsPlansProducer() {
        assertEquals(0, ProductionManager.excessLurkerPlans(2, 0, 2));
        assertEquals(0, ProductionManager.excessLurkerPlans(3, 1, 2));
        assertEquals(1, ProductionManager.excessLurkerPlans(3, 0, 2));
    }

    @Test
    void aBankThatOnlyCoversTheGasStillSchedulesTheSpawningPoolFirst() {
        Plan pool = new BuildingPlan(UnitType.Zerg_Spawning_Pool, FRAME);
        Plan extractor = new BuildingPlan(UnitType.Zerg_Extractor, FRAME + 1);
        Bank bank = new Bank(UnitType.Zerg_Extractor.mineralPrice());

        ScanOutcome outcome = ProductionManager.scanPlans(Arrays.asList(pool, extractor), bank);

        assertEquals(Collections.singletonList(pool), outcome.scheduled);
        assertEquals(Collections.singletonList(extractor), outcome.requeued);
    }

    @Test
    void theSameBankCoversTheGasWithNoSpawningPoolAheadOfIt() {
        Plan extractor = new BuildingPlan(UnitType.Zerg_Extractor, FRAME + 1);
        Bank bank = new Bank(UnitType.Zerg_Extractor.mineralPrice());

        ScanOutcome outcome = ProductionManager.scanPlans(Collections.singletonList(extractor), bank);

        assertEquals(Collections.singletonList(extractor), outcome.scheduled);
    }
}
