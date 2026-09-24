package macro;

import bwapi.TechType;
import bwapi.TilePosition;
import bwapi.UnitType;
import bwapi.UpgradeType;
import macro.ProductionManager.PlanScheduler;
import macro.ProductionManager.ScanOutcome;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.PlanCancelSource;
import macro.plan.PlanState;
import macro.plan.PlanType;
import macro.plan.TechPlan;
import macro.plan.UnitPlan;
import macro.plan.UpgradePlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import strategy.buildorder.BuildOrder;
import telemetry.PlanEventSink;
import telemetry.PlanEvents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionManagerTest {

    private static final int FRAME = 6253;

    /** A drone walking to a far expansion; its claim-time hold ends near 815 frames. */
    private static final int HATCHERY_TRAVEL_FRAMES = 455;

    private static final int STARVED_BANK = 397;

    private static final int QUEUED_COLONY_PRIORITY = 5;

    private static final TilePosition MAIN_TILE = new TilePosition(117, 119);

    private static final TilePosition REMOTE_EXPANSION_TILE = new TilePosition(7, 6);

    private Plan spire(PlanState state) {
        Plan plan = new BuildingPlan(UnitType.Zerg_Spire, 1000);
        plan.setState(state);
        return plan;
    }

    private Plan lair() {
        Plan plan = new BuildingPlan(UnitType.Zerg_Lair, 1000);
        plan.setState(PlanState.SCHEDULE);
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

    /** Lowest free supply at which the planner inserts no overlord for these plans. */
    private int quietFreeSupply(List<Plan> scheduledPlans, List<Plan> queuedPlans) {
        int freeSupply = 0;
        while (!ProductionManager.overlordInsertPriorities(scheduledPlans, queuedPlans, freeSupply, 0, 20).isEmpty()) {
            freeSupply++;
        }
        return freeSupply;
    }

    /** Supply the planner charges a queued plan of this type, measured ahead of a trailing hydralisk. */
    private int queuedPlannerCharge(UnitType unitType) {
        List<Plan> trailing = hydralisks(1, 2000);
        List<Plan> withPlan = new ArrayList<>();
        withPlan.add(new UnitPlan(unitType, 1000));
        withPlan.addAll(trailing);
        return quietFreeSupply(Collections.<Plan>emptyList(), withPlan)
                - quietFreeSupply(Collections.<Plan>emptyList(), trailing);
    }

    /** Supply the planner charges a scheduled plan of this type, measured against a queued hydralisk. */
    private int scheduledPlannerCharge(UnitType unitType) {
        List<Plan> queued = hydralisks(1, 2000);
        return quietFreeSupply(Collections.<Plan>singletonList(new UnitPlan(unitType, 1000)), queued)
                - quietFreeSupply(Collections.<Plan>emptyList(), queued);
    }

    /** Lowest free supply at which the scheduling gate lets a morph of this type start. */
    private int supplyGateCost(UnitType unitType) {
        int freeSupply = 0;
        while (ProductionManager.isSupplyBlocked(unitType, freeSupply)) {
            freeSupply++;
        }
        return freeSupply;
    }

    private static final class Recorder implements PlanScheduler {

        private final Map<Plan, PlanBlocker> blockers = new HashMap<>();
        private final Map<Plan, Boolean> bankClaimedAhead = new HashMap<>();
        private final Map<Plan, Boolean> larvaClaimedAhead = new HashMap<>();
        private final Map<Plan, Boolean> researchClaimedAhead = new HashMap<>();
        private final List<Plan> examined = new ArrayList<>();

        private Recorder block(Plan plan, PlanBlocker blocker) {
            blockers.put(plan, blocker);
            return this;
        }

        @Override
        public PlanBlocker schedule(Plan plan, boolean claimedAhead, boolean larvaClaimed, boolean researchClaimed) {
            examined.add(plan);
            bankClaimedAhead.put(plan, claimedAhead);
            larvaClaimedAhead.put(plan, larvaClaimed);
            researchClaimedAhead.put(plan, researchClaimed);
            return blockers.getOrDefault(plan, PlanBlocker.NONE);
        }
    }

    /**
     * Upgrades, research and larva morphs over one bank, gated as the real schedulers gate: the
     * research claim first, then the research shortfall or the unit bank gate. A research plan
     * pays its cost once the bank covers it; income arrives between scans through {@link #mine}.
     */
    private static final class ResearchBank implements PlanScheduler {

        private final BuildAheadSlot slot = new BuildAheadSlot();

        private final boolean colonyReady;

        private int minerals;

        private int gas;

        private ResearchBank(int minerals, int gas, boolean colonyReady) {
            this.minerals = minerals;
            this.gas = gas;
            this.colonyReady = colonyReady;
        }

        private void mine(int mineralIncome) {
            minerals += mineralIncome;
        }

        @Override
        public PlanBlocker schedule(Plan plan, boolean bankClaimedAhead, boolean larvaClaimedAhead,
                                    boolean researchClaimedAhead) {
            boolean ready = colonyReady && plan.getPlannedUnit() == UnitType.Zerg_Sunken_Colony;
            if (ProductionManager.isHeldByResearchClaim(plan, researchClaimedAhead, ready)) {
                return PlanBlocker.RESEARCH_CLAIM;
            }
            boolean cannotAfford = minerals < plan.mineralPrice() || gas < plan.gasPrice();
            PlanBlocker blocker;
            if (plan.getType() == PlanType.TECH || plan.getType() == PlanType.UPGRADE) {
                blocker = cannotAfford
                        ? ProductionManager.researchShortfallBlocker(FRAME, FRAME + 100, gas >= plan.gasPrice(), true)
                        : PlanBlocker.NONE;
            } else if (plan.getType() == PlanType.BUILDING) {
                blocker = ProductionManager.buildAheadBlocker(
                        slot, plan, FRAME, cannotAfford, bankClaimedAhead, FRAME + 100);
            } else {
                blocker = ProductionManager.unitAheadBlocker(
                        slot, plan, FRAME, cannotAfford, bankClaimedAhead, false, FRAME + 100);
            }
            if (blocker != PlanBlocker.NONE) {
                return blocker;
            }
            minerals -= plan.mineralPrice();
            gas -= plan.gasPrice();
            return PlanBlocker.NONE;
        }
    }

    private Plan lurkerAspect() {
        return new TechPlan(TechType.Lurker_Aspect, 100, false);
    }

    private Plan emergency(UnitType unitType) {
        if (unitType.isBuilding()) {
            return new BuildingPlan(unitType, BuildOrder.EMERGENCY_DEFENSE_PRIORITY);
        }
        return new UnitPlan(unitType, BuildOrder.EMERGENCY_DEFENSE_PRIORITY);
    }

    /**
     * The building path of the scheduler over a mineral bank. A plan that clears buildAheadBlocker
     * takes the slot and reserves its cost, leaving the bank short for everything behind it. A plan
     * that cannot pay first takes the slot from any holder yielding to it, refunding their cost.
     */
    private static final class Bank implements PlanScheduler {

        private final BuildAheadSlot slot = new BuildAheadSlot();

        private final List<Plan> evicted = new ArrayList<>();

        private final Set<Plan> readyColonyMorphs = new HashSet<>();

        private int minerals;

        private Bank(int minerals) {
            this.minerals = minerals;
        }

        private Bank colonyReady(Plan plan) {
            readyColonyMorphs.add(plan);
            return this;
        }

        private void hold(Plan plan) {
            slot.claim(plan, FRAME, FRAME + 100);
            minerals -= plan.getPlannedUnit().mineralPrice();
        }

        @Override
        public PlanBlocker schedule(Plan plan, boolean bankClaimedAhead, boolean larvaClaimedAhead,
                                    boolean researchClaimedAhead) {
            UnitType building = plan.getPlannedUnit();
            boolean cannotAfford = minerals < building.mineralPrice();
            boolean colonyReady = readyColonyMorphs.contains(plan);
            PlanBlocker blocker = ProductionManager.buildAheadBlocker(
                    slot,
                    plan,
                    FRAME,
                    cannotAfford,
                    bankClaimedAhead,
                    FRAME + 100,
                    colonyReady);
            if (blocker != PlanBlocker.NONE) {
                return blocker;
            }
            if (cannotAfford) {
                for (Plan holder : slot.holdersYieldingTo(plan, colonyReady)) {
                    slot.release(holder);
                    minerals += holder.getPlannedUnit().mineralPrice();
                    evicted.add(holder);
                }
            }
            slot.claim(plan, FRAME, FRAME + 100);
            minerals -= building.mineralPrice();
            return PlanBlocker.NONE;
        }
    }

    /**
     * The unit path of the scheduler over a larva pool and a mineral bank, gated in the order
     * scheduleUnitItem gates: larva, then supply, then the bank. A scheduled plan takes a larva and
     * its cost.
     */
    private static final class Larva implements PlanScheduler {

        private final BuildAheadSlot slot = new BuildAheadSlot();

        private final boolean buildingHoldsBank;

        private int larva;

        private int minerals;

        private int freeSupply;

        private Larva(int larva, int minerals, int freeSupply, boolean buildingHoldsBank) {
            this.larva = larva;
            this.minerals = minerals;
            this.freeSupply = freeSupply;
            this.buildingHoldsBank = buildingHoldsBank;
        }

        @Override
        public PlanBlocker schedule(Plan plan, boolean bankClaimedAhead, boolean larvaClaimedAhead,
                                    boolean researchClaimedAhead) {
            UnitType unit = plan.getPlannedUnit();
            if (ProductionManager.isLarvaBlocked(unit, larva > 0, larvaClaimedAhead)) {
                return PlanBlocker.NO_LARVA;
            }
            if (ProductionManager.isSupplyBlocked(unit, freeSupply)) {
                return PlanBlocker.SUPPLY;
            }
            boolean cannotAfford = minerals < unit.mineralPrice();
            boolean barred = ProductionManager.isBarredByBuildingReservation(plan, buildingHoldsBank, 0, freeSupply);
            PlanBlocker blocker = ProductionManager.unitAheadBlocker(
                    slot, plan, FRAME, cannotAfford, bankClaimedAhead, barred, FRAME + 100);
            if (blocker != PlanBlocker.NONE) {
                return blocker;
            }
            larva -= 1;
            minerals -= unit.mineralPrice();
            freeSupply -= unit.supplyRequired();
            return PlanBlocker.NONE;
        }
    }

    private Plan drone(int priority) {
        return new UnitPlan(UnitType.Zerg_Drone, priority);
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
    void anEarlyRushDelayDropsOnlyTheScheduledLair() {
        Plan lair = lair();
        Plan scheduledSpire = spire(PlanState.SCHEDULE);

        assertEquals(Collections.singleton(lair),
                ProductionManager.delayedLairPlans(true, new HashSet<>(Arrays.asList(lair, scheduledSpire, extractor()))));
    }

    @Test
    void noScheduledLairIsDroppedWhileTheLairIsNotDelayed() {
        assertTrue(ProductionManager.delayedLairPlans(false, new HashSet<>(Collections.singletonList(lair()))).isEmpty());
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
    void aQueuedZerglingPairIsChargedForBothUnits() {
        assertEquals(2 * UnitType.Zerg_Zergling.supplyRequired(), queuedPlannerCharge(UnitType.Zerg_Zergling));
        assertEquals(UnitType.Zerg_Drone.supplyRequired(), queuedPlannerCharge(UnitType.Zerg_Drone));
    }

    @Test
    void aScheduledZerglingPairIsChargedForBothUnits() {
        assertEquals(2 * UnitType.Zerg_Zergling.supplyRequired(), scheduledPlannerCharge(UnitType.Zerg_Zergling));
        assertEquals(UnitType.Zerg_Drone.supplyRequired(), scheduledPlannerCharge(UnitType.Zerg_Drone));
    }

    @Test
    void thePlannerAndTheSupplyGateChargeThePlanTheSame() {
        for (UnitType unitType : Arrays.asList(
                UnitType.Zerg_Zergling, UnitType.Zerg_Scourge, UnitType.Zerg_Drone, UnitType.Zerg_Hydralisk)) {
            assertEquals(supplyGateCost(unitType), queuedPlannerCharge(unitType), unitType + " queued");
            assertEquals(supplyGateCost(unitType), scheduledPlannerCharge(unitType), unitType + " scheduled");
        }
    }

    @Test
    void threeQueuedZerglingPairsInsertTheOverlordAheadOfTheSecondPair() {
        List<Plan> queued = Arrays.<Plan>asList(
                new UnitPlan(UnitType.Zerg_Zergling, 3388),
                new UnitPlan(UnitType.Zerg_Zergling, 3389),
                new UnitPlan(UnitType.Zerg_Zergling, 3858));

        List<Integer> priorities = ProductionManager.overlordInsertPriorities(
                Collections.<Plan>emptyList(), queued, 6, 0, 28);

        assertEquals(Collections.singletonList(3388), priorities);
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
            boolean expected = blocker == PlanBlocker.RESOURCES || blocker == PlanBlocker.RESEARCH_MINERALS;
            assertEquals(expected, ProductionManager.claimsBank(blocker), blocker.name());
        }
    }

    @Test
    void aMineralBlockedTechPlanAtTheHeadHoldsALaterDroneUntilFunded() {
        Plan lurkerAspect = lurkerAspect();
        Plan drone = drone(7100);
        ResearchBank bank = new ResearchBank(
                TechType.Lurker_Aspect.mineralPrice() - 1, TechType.Lurker_Aspect.gasPrice(), false);
        PlanEvents.register(blockerRecorder());

        ScanOutcome held = ProductionManager.scanPlans(Arrays.asList(lurkerAspect, drone), bank);
        bank.mine(1 + UnitType.Zerg_Drone.mineralPrice());
        ScanOutcome funded = ProductionManager.scanPlans(held.requeued, bank);

        assertTrue(held.scheduled.isEmpty());
        assertEquals(Arrays.asList(lurkerAspect, drone), reportedPlans.subList(0, 2));
        assertEquals(Arrays.asList(PlanBlocker.RESEARCH_MINERALS, PlanBlocker.RESEARCH_CLAIM), reportedBlockers.subList(0, 2));
        assertEquals(Arrays.asList(lurkerAspect, drone), funded.scheduled);
    }

    @Test
    void aDroneTheBankCoversIsSpentBeforeTheResearchWithoutTheClaim() {
        Plan lurkerAspect = lurkerAspect();
        Plan drone = drone(7100);
        Recorder scheduler = new Recorder().block(lurkerAspect, PlanBlocker.RESOURCES);

        ProductionManager.scanPlans(Arrays.asList(lurkerAspect, drone), scheduler);

        assertFalse(scheduler.researchClaimedAhead.get(drone));
    }

    private static final int OPENING_ZERGLING_PLANS = 6;

    private static final int POOL_COMPLETE_FRAME = 3726;

    private List<Plan> openingZerglings(ProductionQueue queue) {
        List<Plan> zerglings = new ArrayList<>();
        for (int i = 0; i < OPENING_ZERGLING_PLANS; i++) {
            Plan zergling = new UnitPlan(UnitType.Zerg_Zergling, POOL_COMPLETE_FRAME + i);
            zerglings.add(zergling);
            queue.add(zergling);
        }
        return zerglings;
    }

    /**
     * IA-403: SpeedlingAllIn queues Metabolic Boost behind its six opening zergling plans, so a
     * research claim the upgrade raises reaches none of them, even when they cannot schedule.
     */
    @Test
    void speedQueuedBehindTheOpeningZerglingsClaimsNoneOfThem() {
        ProductionQueue queue = new ProductionQueue();
        List<Plan> zerglings = openingZerglings(queue);
        Plan speed = new UpgradePlan(UpgradeType.Metabolic_Boost, POOL_COMPLETE_FRAME + OPENING_ZERGLING_PLANS);
        queue.add(speed);
        Recorder scheduler = new Recorder().block(speed, PlanBlocker.RESEARCH_MINERALS);
        zerglings.forEach(zergling -> scheduler.block(zergling, PlanBlocker.NO_LARVA));
        PlanEvents.register(blockerRecorder());

        ProductionManager.scanPlans(queue.toSortedList(), scheduler);

        for (Plan zergling : zerglings) {
            assertFalse(scheduler.researchClaimedAhead.get(zergling));
        }
        assertFalse(reportedBlockers.contains(PlanBlocker.RESEARCH_CLAIM));
        assertEquals(Collections.singletonList(PlanBlocker.RESEARCH_MINERALS),
                reportedBlockers.subList(reportedBlockers.size() - 1, reportedBlockers.size()));
    }

    @Test
    void theOpeningZerglingsScheduleAheadOfAMineralShortSpeedUpgrade() {
        ProductionQueue queue = new ProductionQueue();
        List<Plan> zerglings = openingZerglings(queue);
        Plan speed = new UpgradePlan(UpgradeType.Metabolic_Boost, POOL_COMPLETE_FRAME + OPENING_ZERGLING_PLANS);
        queue.add(speed);
        int zerglingMinerals = zerglings.stream().mapToInt(Plan::mineralPrice).sum();
        ResearchBank bank = new ResearchBank(zerglingMinerals + speed.mineralPrice() - 1, speed.gasPrice(), false);
        PlanEvents.register(blockerRecorder());

        ScanOutcome outcome = ProductionManager.scanPlans(queue.toSortedList(), bank);

        assertEquals(zerglings, outcome.scheduled);
        assertEquals(Collections.singletonList(speed), reportedPlans);
        assertEquals(Collections.singletonList(PlanBlocker.RESEARCH_MINERALS), reportedBlockers);
    }

    /**
     * The ordering IA-403 removes: an upgrade pulled to the reaction's priority ahead of the opening
     * zerglings holds every one of them behind its claim, though the bank covers each zergling.
     */
    @Test
    void speedPulledAheadOfTheOpeningZerglingsHoldsThemAll() {
        ProductionQueue queue = new ProductionQueue();
        List<Plan> zerglings = openingZerglings(queue);
        Plan speed = new UpgradePlan(UpgradeType.Metabolic_Boost, Reactions.SPEED_UPGRADE_PRIORITY);
        queue.add(speed);
        ResearchBank bank = new ResearchBank(speed.mineralPrice() - 1, speed.gasPrice(), false);
        PlanEvents.register(blockerRecorder());

        ScanOutcome outcome = ProductionManager.scanPlans(queue.toSortedList(), bank);

        assertTrue(outcome.scheduled.isEmpty());
        assertEquals(PlanBlocker.RESEARCH_MINERALS, reportedBlockers.get(0));
        assertEquals(Collections.nCopies(OPENING_ZERGLING_PLANS, PlanBlocker.RESEARCH_CLAIM),
                reportedBlockers.subList(1, reportedBlockers.size()));
    }

    @Test
    void aResearchClaimReachesEveryPlanBehindItAndNoneAhead() {
        Plan drone = drone(90);
        Plan lurkerAspect = lurkerAspect();
        Plan ling = zergling();
        Recorder scheduler = new Recorder().block(lurkerAspect, PlanBlocker.RESEARCH_MINERALS);

        ProductionManager.scanPlans(Arrays.asList(drone, lurkerAspect, ling), scheduler);

        assertFalse(scheduler.researchClaimedAhead.get(drone));
        assertFalse(scheduler.researchClaimedAhead.get(lurkerAspect));
        assertTrue(scheduler.researchClaimedAhead.get(ling));
        assertTrue(scheduler.bankClaimedAhead.get(ling));
    }

    @Test
    void anOverlordAndAPriorityOneSunkenPassAHeldResearchClaim() {
        Plan lurkerAspect = lurkerAspect();
        Plan overlord = overlord(7100);
        Plan sunken = emergency(UnitType.Zerg_Sunken_Colony);
        int minerals = UnitType.Zerg_Overlord.mineralPrice() + UnitType.Zerg_Sunken_Colony.mineralPrice();
        ResearchBank bank = new ResearchBank(minerals, TechType.Lurker_Aspect.gasPrice(), false);
        assertTrue(minerals < TechType.Lurker_Aspect.mineralPrice());

        ScanOutcome outcome = ProductionManager.scanPlans(Arrays.asList(lurkerAspect, overlord, sunken), bank);

        assertEquals(Arrays.asList(overlord, sunken), outcome.scheduled);
        assertEquals(Collections.singletonList(lurkerAspect), outcome.requeued);
        assertFalse(ProductionManager.isHeldByResearchClaim(overlord, true, false));
        assertFalse(ProductionManager.isHeldByResearchClaim(sunken, true, false));
    }

    @Test
    void aSunkenMorphWhoseCreepColonyIsCompletePassesAHeldResearchClaim() {
        Plan lurkerAspect = lurkerAspect();
        Plan sunken = new BuildingPlan(UnitType.Zerg_Sunken_Colony, QUEUED_COLONY_PRIORITY);
        ResearchBank bank = new ResearchBank(
                UnitType.Zerg_Sunken_Colony.mineralPrice(), TechType.Lurker_Aspect.gasPrice(), true);

        ScanOutcome outcome = ProductionManager.scanPlans(Arrays.asList(lurkerAspect, sunken), bank);

        assertEquals(Collections.singletonList(sunken), outcome.scheduled);
        assertFalse(ProductionManager.isHeldByResearchClaim(sunken, true, true));
        assertTrue(ProductionManager.isHeldByResearchClaim(sunken, true, false));
    }

    @Test
    void queuedZerglingsExtractorsAndUpgradesAreHeldByAResearchClaim() {
        assertTrue(ProductionManager.isHeldByResearchClaim(zergling(), true, false));
        assertTrue(ProductionManager.isHeldByResearchClaim(extractor(), true, false));
        assertTrue(ProductionManager.isHeldByResearchClaim(metabolicBoost(), true, false));
        assertFalse(ProductionManager.isHeldByResearchClaim(emergency(UnitType.Zerg_Zergling), true, false));
        assertFalse(ProductionManager.isHeldByResearchClaim(zergling(), false, false));
    }

    @Test
    void onlyAMineralShortfallWithAFreeProducerWithinTheHoldHoldsTheBank() {
        int soon = FRAME + BuildAheadSlot.MAX_HOLD_FRAMES;

        assertEquals(PlanBlocker.RESEARCH_MINERALS, ProductionManager.researchShortfallBlocker(FRAME, soon, true, true));
        assertEquals(PlanBlocker.RESOURCES, ProductionManager.researchShortfallBlocker(FRAME, soon, false, true));
        assertEquals(PlanBlocker.RESOURCES, ProductionManager.researchShortfallBlocker(FRAME, soon, true, false));
        assertEquals(PlanBlocker.RESOURCES, ProductionManager.researchShortfallBlocker(FRAME, soon + 1, true, true));
        assertEquals(PlanBlocker.NO_INCOME,
                ProductionManager.researchShortfallBlocker(FRAME, Integer.MAX_VALUE, true, true));
    }

    @Test
    void aGasShortResearchLeavesTheDroneBehindItFreeToSpend() {
        Plan lurkerAspect = lurkerAspect();
        Plan drone = drone(7100);
        ResearchBank bank = new ResearchBank(UnitType.Zerg_Drone.mineralPrice(), 0, false);

        ScanOutcome outcome = ProductionManager.scanPlans(Arrays.asList(lurkerAspect, drone), bank);

        assertEquals(Collections.singletonList(drone), outcome.scheduled);
    }

    @Test
    void aHeldPlanDoesNotClaimTheLarvaForThePlansBehindIt() {
        assertFalse(ProductionManager.claimsLarva(drone(7100), PlanBlocker.RESEARCH_CLAIM));
        assertFalse(ProductionManager.claimsLarva(drone(7100), PlanBlocker.RESEARCH_MINERALS));
    }

    @Test
    void anAffordableBuildingBehindALarvaBlockedPlanIsScheduledTheSameScan() {
        Plan drone = new UnitPlan(UnitType.Zerg_Drone, 1);
        Plan extractor = extractor();
        Recorder scheduler = new Recorder().block(drone, PlanBlocker.NO_LARVA);

        ScanOutcome outcome = ProductionManager.scanPlans(Arrays.asList(drone, extractor), scheduler);

        assertEquals(Collections.singletonList(extractor), outcome.scheduled);
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
            if (blocker == PlanBlocker.NONE || ProductionManager.claimsBank(blocker)) {
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
        PlanScheduler scheduler = (plan, claimedAhead, larvaClaimed, researchClaimed) -> {
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
    void anOverlordFacingASupplyBlockIsNotBarredByAScheduledBuildingsReservation() {
        int predicted = FRAME + UnitType.Zerg_Overlord.buildTime() - 1;
        boolean barred = ProductionManager.isBarredByBuildingReservation(
                overlord(1), true, 0, ProductionManager.SUPPLY_BUFFER - 1);

        PlanBlocker blocker = ProductionManager.unitAheadBlocker(
                new BuildAheadSlot(), overlord(1), FRAME, true, false, barred, predicted);

        assertFalse(barred);
        assertEquals(PlanBlocker.NONE, blocker);
    }

    @Test
    void anOverlordWithSupplyToSpareWaitsBehindAScheduledBuildingsReservation() {
        int predicted = FRAME + UnitType.Zerg_Overlord.buildTime() - 1;
        boolean barred = ProductionManager.isBarredByBuildingReservation(
                overlord(1), true, 0, ProductionManager.SUPPLY_BUFFER);

        PlanBlocker blocker = ProductionManager.unitAheadBlocker(
                new BuildAheadSlot(), overlord(1), FRAME, true, false, barred, predicted);

        assertTrue(barred);
        assertEquals(PlanBlocker.BUILD_AHEAD_SLOT_TAKEN, blocker);
    }

    @Test
    void onlyABuildingHoldingTheBankBarsAUnit() {
        assertTrue(ProductionManager.isBarredByBuildingReservation(drone(FRAME), true, 0, 0));
        assertFalse(ProductionManager.isBarredByBuildingReservation(drone(FRAME), false, 0, 0));
        assertFalse(ProductionManager.isBarredByBuildingReservation(overlord(FRAME), false, 0, 0));
        assertFalse(ProductionManager.isBarredByBuildingReservation(
                emergency(UnitType.Zerg_Zergling), false, Reactions.EARLY_RUSH_SAFE_ZERGLINGS, 0));
    }

    @Test
    void anEmergencyZerglingBelowTheRushFloorIsNotBarredByABuildingHoldingTheBank() {
        int predicted = FRAME + UnitType.Zerg_Zergling.buildTime() - 1;
        Plan ling = emergency(UnitType.Zerg_Zergling);
        boolean barred = ProductionManager.isBarredByBuildingReservation(
                ling, true, Reactions.EARLY_RUSH_SAFE_ZERGLINGS - 1, 0);

        PlanBlocker blocker = ProductionManager.unitAheadBlocker(
                new BuildAheadSlot(), ling, FRAME, true, false, barred, predicted);

        assertFalse(barred);
        assertEquals(PlanBlocker.NONE, blocker);
    }

    @Test
    void anEmergencyZerglingAtTheRushFloorCannotSpendABuildingsReservation() {
        int predicted = FRAME + UnitType.Zerg_Zergling.buildTime() - 1;
        Plan ling = emergency(UnitType.Zerg_Zergling);
        boolean barred = ProductionManager.isBarredByBuildingReservation(
                ling, true, Reactions.EARLY_RUSH_SAFE_ZERGLINGS, 0);

        PlanBlocker blocker = ProductionManager.unitAheadBlocker(
                new BuildAheadSlot(), ling, FRAME, true, false, barred, predicted);

        assertTrue(barred);
        assertEquals(PlanBlocker.BUILD_AHEAD_SLOT_TAKEN, blocker);
    }

    @Test
    void emergencyColoniesAreNeverBarredByABuildingHoldingTheBank() {
        int manyZerglings = Reactions.EARLY_RUSH_SAFE_ZERGLINGS * 3;

        assertFalse(ProductionManager.isBarredByBuildingReservation(
                emergency(UnitType.Zerg_Creep_Colony), true, manyZerglings, 0));
        assertFalse(ProductionManager.isBarredByBuildingReservation(
                emergency(UnitType.Zerg_Sunken_Colony), true, manyZerglings, 0));
    }

    @Test
    void aQueuedZerglingIsStillBarredByABuildingHoldingTheBank() {
        int predicted = FRAME + UnitType.Zerg_Zergling.buildTime() - 1;

        PlanBlocker blocker = ProductionManager.unitAheadBlocker(
                new BuildAheadSlot(), zergling(), FRAME, true, false, true, predicted);

        assertEquals(PlanBlocker.BUILD_AHEAD_SLOT_TAKEN, blocker);
    }

    @Test
    void anEmergencySunkenIsNotBlockedByALowerPriorityLairHoldingTheSlot() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan lair = lair();
        slot.claim(lair, FRAME, FRAME + 100);
        Plan sunken = emergency(UnitType.Zerg_Sunken_Colony);

        PlanBlocker blocker = ProductionManager.buildAheadBlocker(slot, sunken, FRAME, true, false, FRAME + 100);

        assertEquals(PlanBlocker.NONE, blocker);
        assertEquals(Collections.singletonList(lair), slot.holdersYieldingTo(sunken));
    }

    @Test
    void anEmergencyCreepColonyTakesTheSlotFromALowerPrioritySpire() {
        Bank bank = new Bank(0);
        Plan spire = spire(PlanState.SCHEDULE);
        bank.hold(spire);
        Plan colony = emergency(UnitType.Zerg_Creep_Colony);

        ScanOutcome outcome = ProductionManager.scanPlans(Collections.singletonList(colony), bank);

        assertEquals(Collections.singletonList(colony), outcome.scheduled);
        assertEquals(Collections.singletonList(spire), bank.evicted);
        assertEquals(Collections.singletonList(colony), bank.slot.claimedPlans());
    }

    @Test
    void aNonEmergencyPlanBehindTheSameHolderIsStillBlocked() {
        BuildAheadSlot slot = new BuildAheadSlot();
        slot.claim(lair(), FRAME, FRAME + 100);
        Plan queuedSunken = new BuildingPlan(UnitType.Zerg_Sunken_Colony, FRAME);
        Plan liftedHatchery = hatchery();

        assertEquals(PlanBlocker.BUILD_AHEAD_SLOT_TAKEN,
                ProductionManager.buildAheadBlocker(slot, queuedSunken, FRAME, true, false, FRAME + 100));
        assertEquals(PlanBlocker.BUILD_AHEAD_SLOT_TAKEN,
                ProductionManager.buildAheadBlocker(slot, liftedHatchery, FRAME, true, false, FRAME + 100));
        assertTrue(slot.holdersYieldingTo(queuedSunken).isEmpty());
        assertTrue(slot.holdersYieldingTo(liftedHatchery).isEmpty());
    }

    @Test
    void anEmergencyPlanBehindAHigherPriorityBankClaimStillWaits() {
        BuildAheadSlot slot = new BuildAheadSlot();
        slot.claim(lair(), FRAME, FRAME + 100);

        PlanBlocker blocker = ProductionManager.buildAheadBlocker(
                slot, emergency(UnitType.Zerg_Sunken_Colony), FRAME, true, true, FRAME + 100);

        assertEquals(PlanBlocker.BUILD_AHEAD_SLOT_TAKEN, blocker);
    }

    @Test
    void anAffordableEmergencyPlanLeavesTheHolderInPlace() {
        Bank bank = new Bank(UnitType.Zerg_Spire.mineralPrice() + UnitType.Zerg_Creep_Colony.mineralPrice());
        Plan spire = spire(PlanState.SCHEDULE);
        bank.hold(spire);
        Plan colony = emergency(UnitType.Zerg_Creep_Colony);

        ProductionManager.scanPlans(Collections.singletonList(colony), bank);

        assertTrue(bank.evicted.isEmpty());
        assertEquals(Arrays.asList(spire, colony), bank.slot.claimedPlans());
    }

    @Test
    void twoEmergencyPlansNeitherEvictNorStarveEachOther() {
        Bank bank = new Bank(0);
        Plan lair = lair();
        bank.hold(lair);
        Plan sunken = emergency(UnitType.Zerg_Sunken_Colony);
        Plan colony = emergency(UnitType.Zerg_Creep_Colony);

        ScanOutcome first = ProductionManager.scanPlans(Arrays.asList(sunken, colony), bank);
        List<Plan> queue = new ArrayList<>(first.requeued);
        queue.addAll(bank.evicted);
        ScanOutcome second = ProductionManager.scanPlans(queue, bank);
        ScanOutcome third = ProductionManager.scanPlans(second.requeued, bank);

        assertEquals(Collections.singletonList(sunken), first.scheduled);
        assertEquals(Collections.singletonList(lair), bank.evicted);
        assertTrue(second.scheduled.isEmpty());
        assertTrue(third.scheduled.isEmpty());
        assertEquals(Arrays.asList(colony, lair), third.requeued);
        assertEquals(Collections.singletonList(sunken), bank.slot.claimedPlans());
        assertTrue(bank.slot.holdersYieldingTo(colony).isEmpty());
    }

    private Plan colonyMorph(UnitType morph, int priority) {
        return new BuildingPlan(morph, priority);
    }

    private Plan holder(UnitType building, int priority, PlanState state) {
        Plan plan = new BuildingPlan(building, priority);
        plan.setState(state);
        return plan;
    }

    @Test
    void aScheduledLairYieldsToASunkenWhoseCreepColonyIsComplete() {
        Bank bank = new Bank(0);
        Plan lair = holder(UnitType.Zerg_Lair, 3, PlanState.SCHEDULE);
        bank.hold(lair);
        Plan sunken = colonyMorph(UnitType.Zerg_Sunken_Colony, 5);
        bank.colonyReady(sunken);

        ScanOutcome outcome = ProductionManager.scanPlans(Collections.singletonList(sunken), bank);

        assertEquals(Collections.singletonList(sunken), outcome.scheduled);
        assertEquals(Collections.singletonList(lair), bank.evicted);
        assertEquals(Collections.singletonList(sunken), bank.slot.claimedPlans());
    }

    @Test
    void aSporeWhoseCreepColonyIsCompleteTakesTheSlotFromAScheduledHatchery() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan hatchery = holder(UnitType.Zerg_Hatchery, 2, PlanState.SCHEDULE);
        slot.claim(hatchery, FRAME, FRAME + 100);
        Plan spore = colonyMorph(UnitType.Zerg_Spore_Colony, 5);

        assertEquals(PlanBlocker.NONE, ProductionManager.buildAheadBlocker(slot, spore, FRAME, true, false, FRAME + 100, true));
        assertEquals(Collections.singletonList(hatchery), slot.holdersYieldingTo(spore, true));
    }

    @Test
    void aHolderWhoseBuilderIsDispatchedKeepsTheSlotFromAReadySunken() {
        BuildAheadSlot slot = new BuildAheadSlot();
        slot.claim(spire(PlanState.BUILDING), FRAME, FRAME + 100);
        Plan sunken = colonyMorph(UnitType.Zerg_Sunken_Colony, 5);

        assertEquals(PlanBlocker.BUILD_AHEAD_SLOT_TAKEN,
                ProductionManager.buildAheadBlocker(slot, sunken, FRAME, true, false, FRAME + 100, true));
        assertTrue(slot.holdersYieldingTo(sunken, true).isEmpty());
    }

    @Test
    void aHolderWhoseMorphIsIssuedKeepsTheSlotFromAReadySunken() {
        BuildAheadSlot slot = new BuildAheadSlot();
        slot.claim(holder(UnitType.Zerg_Lair, 3, PlanState.MORPHING), FRAME, FRAME + 100);
        Plan sunken = colonyMorph(UnitType.Zerg_Sunken_Colony, 5);

        assertEquals(PlanBlocker.BUILD_AHEAD_SLOT_TAKEN,
                ProductionManager.buildAheadBlocker(slot, sunken, FRAME, true, false, FRAME + 100, true));
        assertTrue(slot.holdersYieldingTo(sunken, true).isEmpty());
    }

    @Test
    void anEmergencyHolderKeepsTheSlotFromANonEmergencyReadySunken() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan colony = emergency(UnitType.Zerg_Creep_Colony);
        colony.setState(PlanState.SCHEDULE);
        slot.claim(colony, FRAME, FRAME + 100);

        assertTrue(slot.holdersYieldingTo(colonyMorph(UnitType.Zerg_Sunken_Colony, 5), true).isEmpty());
    }

    @Test
    void twoReadyColonyMorphsDoNotEvictEachOther() {
        BuildAheadSlot slot = new BuildAheadSlot();
        slot.claim(holder(UnitType.Zerg_Sunken_Colony, 5, PlanState.SCHEDULE), FRAME, FRAME + 100);

        assertTrue(slot.holdersYieldingTo(colonyMorph(UnitType.Zerg_Sunken_Colony, 3), true).isEmpty());
    }

    @Test
    void aSunkenWhoseCreepColonyIsNotReadyTakesNothing() {
        BuildAheadSlot slot = new BuildAheadSlot();
        slot.claim(lair(), FRAME, FRAME + 100);

        assertTrue(slot.holdersYieldingTo(colonyMorph(UnitType.Zerg_Sunken_Colony, 5), false).isEmpty());
    }

    @Test
    void aReadySunkenInBackoffStillWaitsItOut() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan sunken = colonyMorph(UnitType.Zerg_Sunken_Colony, 5);
        slot.claim(sunken, FRAME, FRAME + 100);
        slot.releaseWithBackoff(sunken, FRAME);
        slot.claim(lair(), FRAME, FRAME + 100);

        assertEquals(PlanBlocker.BUILD_AHEAD_BACKOFF,
                ProductionManager.buildAheadBlocker(slot, sunken, FRAME, true, false, FRAME + 100, true));
    }

    @Test
    void aReadySunkenWinsAnEqualPriorityTieWithACreepColonyHoldingTheSlot() {
        Bank bank = new Bank(0);
        Plan creepColony = holder(UnitType.Zerg_Creep_Colony, 5, PlanState.SCHEDULE);
        bank.hold(creepColony);
        Plan sunken = colonyMorph(UnitType.Zerg_Sunken_Colony, 5);
        bank.colonyReady(sunken);

        ScanOutcome outcome = ProductionManager.scanPlans(Collections.singletonList(sunken), bank);

        assertEquals(Collections.singletonList(sunken), outcome.scheduled);
        assertEquals(Collections.singletonList(creepColony), bank.evicted);
    }

    @Test
    void aReadySunkenIsScannedAheadOfAnEqualPriorityCreepColony() {
        ProductionQueue queue = new ProductionQueue();
        Plan creepColony = new BuildingPlan(UnitType.Zerg_Creep_Colony, 5);
        Plan sunken = colonyMorph(UnitType.Zerg_Sunken_Colony, 5);
        queue.add(creepColony);
        queue.add(sunken);
        Bank bank = new Bank(0).colonyReady(sunken);

        ScanOutcome outcome = ProductionManager.scanPlans(queue.toSortedList(), bank);

        assertEquals(Arrays.asList(sunken, creepColony), queue.toSortedList());
        assertEquals(Collections.singletonList(sunken), outcome.scheduled);
        assertEquals(Collections.singletonList(creepColony), outcome.requeued);
        assertTrue(bank.evicted.isEmpty());
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
        assertFalse(ProductionManager.isLarvaBlocked(UnitType.Zerg_Lurker, false, false));
        assertFalse(ProductionManager.isLarvaBlocked(UnitType.Zerg_Guardian, false, false));
        assertFalse(ProductionManager.isLarvaBlocked(UnitType.Zerg_Devourer, false, false));
    }

    @Test
    void aLarvaMorphIsBlockedWithoutFreeLarva() {
        assertTrue(ProductionManager.isLarvaBlocked(UnitType.Zerg_Zergling, false, false));
        assertTrue(ProductionManager.isLarvaBlocked(UnitType.Zerg_Hydralisk, false, false));
        assertTrue(ProductionManager.isLarvaBlocked(UnitType.Zerg_Overlord, false, false));
    }

    @Test
    void aLarvaMorphSchedulesWithFreeLarva() {
        assertFalse(ProductionManager.isLarvaBlocked(UnitType.Zerg_Zergling, true, false));
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

    @Test
    void aMutaliskWaitingOnABuildingsBankKeepsTheLarvaFromALaterDrone() {
        Plan muta = mutalisk();
        Plan drone = drone(7709);
        Larva scheduler = new Larva(1, UnitType.Zerg_Drone.mineralPrice(), 20, true);

        ScanOutcome outcome = ProductionManager.scanPlans(Arrays.asList(muta, drone), scheduler);

        assertTrue(outcome.scheduled.isEmpty());
        assertEquals(Arrays.asList(muta, drone), outcome.requeued);
        assertEquals(1, scheduler.larva);
    }

    @Test
    void aSupplyBlockedMutaliskKeepsTheLarvaFromALaterDrone() {
        Plan muta = mutalisk();
        Plan drone = drone(7709);
        Larva scheduler = new Larva(1, 1000, UnitType.Zerg_Drone.supplyRequired(), false);

        ScanOutcome outcome = ProductionManager.scanPlans(Arrays.asList(muta, drone), scheduler);

        assertTrue(outcome.scheduled.isEmpty());
        assertEquals(Arrays.asList(muta, drone), outcome.requeued);
    }

    @Test
    void aLarvaBlockedPlanClaimsTheLarvaForEveryPlanBehindIt() {
        Plan muta = mutalisk();
        Plan drone = drone(7709);
        Plan ling = zergling();
        Recorder scheduler = new Recorder().block(muta, PlanBlocker.NO_LARVA);

        ProductionManager.scanPlans(Arrays.asList(muta, drone, ling), scheduler);

        assertFalse(scheduler.larvaClaimedAhead.get(muta));
        assertTrue(scheduler.larvaClaimedAhead.get(drone));
        assertTrue(scheduler.larvaClaimedAhead.get(ling));
    }

    @Test
    void aClaimedLarvaStillLetsTheOverlordThroughAndBarsOtherMorphs() {
        assertTrue(ProductionManager.isLarvaBlocked(UnitType.Zerg_Drone, true, true));
        assertTrue(ProductionManager.isLarvaBlocked(UnitType.Zerg_Zergling, true, true));
        assertFalse(ProductionManager.isLarvaBlocked(UnitType.Zerg_Overlord, true, true));
        assertFalse(ProductionManager.isLarvaBlocked(UnitType.Zerg_Lurker, true, true));
    }

    @Test
    void anOverlordBehindASupplyBlockedMutaliskIsStillScheduled() {
        Plan muta = mutalisk();
        Plan overlord = overlord(151);
        Larva scheduler = new Larva(1, 1000, 0, false);

        ScanOutcome outcome = ProductionManager.scanPlans(Arrays.asList(muta, overlord), scheduler);

        assertEquals(Collections.singletonList(overlord), outcome.scheduled);
    }

    /**
     * Game LBIDH0GO: a Drone derived before the Spire finished and a Mutalisk derived after it.
     * With no larva the Mutalisk waits on NO_LARVA, and the next larva goes to it rather than to
     * the Drone queued first.
     */
    @Test
    void aMutaliskWaitingOnLarvaClaimsTheNextLarvaOverALaterPriorityDrone() {
        ProductionQueue queue = new ProductionQueue();
        Plan drone = drone(8178);
        Plan muta = mutalisk();
        queue.add(drone);
        queue.add(muta);
        Larva scheduler = new Larva(0, 1000, 20, false);

        PlanEvents.register(blockerRecorder());

        ScanOutcome waiting = ProductionManager.scanPlans(queue.toSortedList(), scheduler);

        assertTrue(waiting.scheduled.isEmpty());
        assertEquals(Arrays.asList(muta, drone), waiting.requeued);
        assertEquals(muta, reportedPlans.get(0));
        assertEquals(PlanBlocker.NO_LARVA, reportedBlockers.get(0));

        scheduler.larva = 1;
        ScanOutcome hatched = ProductionManager.scanPlans(waiting.requeued, scheduler);

        assertEquals(Collections.singletonList(muta), hatched.scheduled);
        assertEquals(Collections.singletonList(drone), hatched.requeued);
        assertEquals(0, scheduler.larva);
    }

    @Test
    void withNoBlockedPlanAheadTheDroneTakesTheLarva() {
        Plan drone = drone(7709);
        Larva scheduler = new Larva(1, UnitType.Zerg_Drone.mineralPrice(), 20, true);

        ScanOutcome outcome = ProductionManager.scanPlans(Collections.singletonList(drone), scheduler);

        assertEquals(Collections.singletonList(drone), outcome.scheduled);
        assertEquals(0, scheduler.larva);
    }

    @Test
    void onlyAWaitThatClearsIntoALarvaMorphClaimsTheLarva() {
        for (PlanBlocker blocker : PlanBlocker.values()) {
            boolean expected = blocker == PlanBlocker.NO_LARVA
                    || blocker == PlanBlocker.SUPPLY
                    || blocker == PlanBlocker.BUILD_AHEAD_SLOT_TAKEN;
            assertEquals(expected, ProductionManager.claimsLarva(mutalisk(), blocker), blocker.name());
        }
    }

    @Test
    void aPlanThatTakesNoLarvaNeverClaimsTheLarva() {
        Plan lurker = new UnitPlan(UnitType.Zerg_Lurker, UnitPlan.ADVANCED_UNIT_PRIORITY);
        for (PlanBlocker blocker : PlanBlocker.values()) {
            assertFalse(ProductionManager.claimsLarva(hatchery(), blocker), blocker.name());
            assertFalse(ProductionManager.claimsLarva(metabolicBoost(), blocker), blocker.name());
            assertFalse(ProductionManager.claimsLarva(lurker, blocker), blocker.name());
        }
    }

    @Test
    void aNonClaimingBlockerLeavesTheLarvaOpenToThePlansBehindIt() {
        for (PlanBlocker blocker : PlanBlocker.values()) {
            if (blocker == PlanBlocker.NONE || ProductionManager.claimsLarva(mutalisk(), blocker)) {
                continue;
            }
            Plan muta = mutalisk();
            Plan drone = drone(7709);
            Recorder scheduler = new Recorder().block(muta, blocker);

            ProductionManager.scanPlans(Arrays.asList(muta, drone), scheduler);

            assertFalse(scheduler.larvaClaimedAhead.get(drone), blocker.name());
        }
    }

    @Test
    void theLarvaClaimEndsOnceTheBlockedPlanIsCancelled() {
        Plan muta = mutalisk();
        Plan drone = drone(7709);
        Larva scheduler = new Larva(1, UnitType.Zerg_Drone.mineralPrice(), 20, true);
        ScanOutcome claimed = ProductionManager.scanPlans(Arrays.asList(muta, drone), scheduler);

        ScanOutcome afterCancel = ProductionManager.scanPlans(Collections.singletonList(drone), scheduler);

        assertTrue(claimed.scheduled.isEmpty());
        assertEquals(Collections.singletonList(drone), afterCancel.scheduled);
    }

    private static Plan expansionHatchery(int priority) {
        return new BuildingPlan(UnitType.Zerg_Hatchery, priority, REMOTE_EXPANSION_TILE);
    }

    private static List<Plan> hatcheryPlansIn(ProductionQueue queue) {
        List<Plan> hatcheries = new ArrayList<>();
        for (Plan plan : queue) {
            if (plan.getPlannedUnit() == UnitType.Zerg_Hatchery) {
                hatcheries.add(plan);
            }
        }
        return hatcheries;
    }

    private static void applyLarvaConstraint(ProductionQueue queue) {
        Plan target = ProductionManager.larvaConstraintHatchery(0, STARVED_BANK, queue);
        if (target != null) {
            queue.setPriorityWhere(plan -> plan == target, 0);
        }
    }

    /**
     * Game LBIDH0GO at frame 9917: the only queued hatchery was a remote expansion. The rule
     * promotes it as it stands and creates no main macro hatchery of its own.
     */
    @Test
    void theLarvaConstraintPromotesAQueuedExpansionWithoutTurningItIntoAMacroHatchery() {
        ProductionQueue queue = new ProductionQueue();
        Plan expansion = expansionHatchery(FRAME);
        queue.add(expansion);

        applyLarvaConstraint(queue);

        assertEquals(Collections.singletonList(expansion), hatcheryPlansIn(queue));
        assertEquals(0, expansion.getPriority());
        assertFalse(expansion.isMacroHatchery());
        assertEquals(REMOTE_EXPANSION_TILE, expansion.getBuildPosition());
    }

    @Test
    void theLarvaConstraintKeepsTheMacroFlagAndMainTileOfTheHatcheryItPromotes() {
        ProductionQueue queue = new ProductionQueue();
        Plan macroHatchery = BuildOrder.macroHatcheryPlan(FRAME, MAIN_TILE);
        queue.add(macroHatchery);

        applyLarvaConstraint(queue);

        assertEquals(0, macroHatchery.getPriority());
        assertTrue(macroHatchery.isMacroHatchery());
        assertEquals(MAIN_TILE, macroHatchery.getBuildPosition());
    }

    @Test
    void theLarvaConstraintLeavesAMacroHatcheryBehindAnExpansionAlreadyAtPriorityZero() {
        ProductionQueue queue = new ProductionQueue();
        queue.add(expansionHatchery(0));
        queue.add(BuildOrder.macroHatcheryPlan(FRAME, MAIN_TILE));

        assertNull(ProductionManager.larvaConstraintHatchery(0, STARVED_BANK, queue));
    }

    @Test
    void theLarvaConstraintHoldsWhileLarvaIsFree() {
        ProductionQueue queue = new ProductionQueue();
        queue.add(expansionHatchery(FRAME));

        assertNull(ProductionManager.larvaConstraintHatchery(1, STARVED_BANK, queue));
    }

    @Test
    void theLarvaConstraintHoldsWhileTheBankCannotBuyAHatchery() {
        ProductionQueue queue = new ProductionQueue();
        queue.add(expansionHatchery(FRAME));

        assertNull(ProductionManager.larvaConstraintHatchery(0, UnitType.Zerg_Hatchery.mineralPrice() - 1, queue));
    }

    /**
     * Game LC0QF0BR: the main macro hatchery finished as the third hatchery, larva reached five,
     * and the excess sweep cancelled expansions 106, 111 and 146.
     */
    @Test
    void aFinishedMacroHatcheryDoesNotMakeAPlannedExpansionExcess() {
        boolean excess = HatcheryCapacity.isExcess(3, HatcheryCapacity.EXCESS_LARVA);
        boolean excessForExpansion = HatcheryCapacity.isExcessForExpansion(3, 1, HatcheryCapacity.EXCESS_LARVA);

        assertTrue(excess);
        assertFalse(ProductionManager.isExcessHatcheryPlan(hatchery(), excess, excessForExpansion));
    }

    @Test
    void aMacroHatcheryUnderConstructionDoesNotMakeAPlannedExpansionExcess() {
        boolean excess = HatcheryCapacity.isExcess(2, HatcheryCapacity.EXCESS_LARVA);
        boolean excessForExpansion = HatcheryCapacity.isExcessForExpansion(2, 0, HatcheryCapacity.EXCESS_LARVA);

        assertFalse(ProductionManager.isExcessHatcheryPlan(hatchery(), excess, excessForExpansion));
    }

    @Test
    void aFinishedMacroHatcheryStillMakesAnotherMacroHatcheryExcess() {
        boolean excess = HatcheryCapacity.isExcess(3, HatcheryCapacity.EXCESS_LARVA);
        boolean excessForExpansion = HatcheryCapacity.isExcessForExpansion(3, 1, HatcheryCapacity.EXCESS_LARVA);

        assertTrue(ProductionManager.isExcessHatcheryPlan(BuildOrder.macroHatcheryPlan(FRAME, MAIN_TILE),
                excess, excessForExpansion));
    }

    @Test
    void threeExpansionHatcheriesWithIdleLarvaStillCancelAPlannedExpansion() {
        boolean excess = HatcheryCapacity.isExcess(3, HatcheryCapacity.EXCESS_LARVA);
        boolean excessForExpansion = HatcheryCapacity.isExcessForExpansion(3, 0, HatcheryCapacity.EXCESS_LARVA);

        assertTrue(ProductionManager.isExcessHatcheryPlan(hatchery(), excess, excessForExpansion));
    }

    @Test
    void theExcessSweepLeavesNonHatcheryPlansAlone() {
        assertFalse(ProductionManager.isExcessHatcheryPlan(extractor(), true, true));
    }
}
