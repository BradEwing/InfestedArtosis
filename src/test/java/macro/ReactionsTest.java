package macro;

import bwapi.Race;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.UpgradeType;
import bwem.Base;
import info.BaseData;
import info.ResourceCount;
import info.TechProgression;
import info.UnitTypeCount;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.PlanCancelSource;
import macro.plan.PlanState;
import macro.plan.PlanType;
import macro.plan.UpgradePlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import strategy.buildorder.SunkenTargets;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the main-base sunken gate, the early rush guards and the raw extractor cancel loop.
 *
 * <p>bwem.Base is final with a package-private constructor and bwapi.Unit cannot be instantiated outside its own
 * package, so base counts are simulated by swapping in sets that report a fixed size. For the same reason the gate
 * tests construct Reactions on a null GameState: the seam they drive never reads it.
 *
 * <p>The geyser reservation tests stand a null in for the geyser unit. Every method they exercise identifies a
 * reservation by its stored tile position and calls nothing on the unit itself, so the null never resolves.
 */
public class ReactionsTest {

    private static final boolean WITHIN_RUSH_WINDOW = true;

    private static final int QUEUED_ZERGLING_PLANS = 6;

    private static final int SUSTAINED_RUSH_FRAMES = 2000;

    private static final TilePosition GEYSER_TILE = new TilePosition(20, 30);

    private static final TilePosition OTHER_TILE = new TilePosition(44, 12);

    private static final int CANCEL_FRAME = 5000;

    private static final boolean COMPLETE = true;

    private static final boolean INCOMPLETE = false;

    private static final boolean UNDER_BARRACKS_PRESSURE = true;

    private static final boolean NO_BARRACKS_PRESSURE = false;

    private static final boolean HAVE_EXTRACTOR = true;

    private static final boolean NO_EXTRACTOR = false;

    private static final boolean SPEED_PLANNED = true;

    private static final boolean SPEED_NOT_PLANNED = false;

    private static final boolean SPEED_STARTED = true;

    private static final boolean SPEED_NOT_STARTED = false;

    private static final int FREE_MINERALS_AFTER_LAIR_CLAIM = 43;

    private static final boolean RESEARCHED = true;

    private static final boolean NOT_RESEARCHED = false;

    private BaseData baseData;

    @BeforeEach
    void setUp() {
        baseData = new BaseData(new ArrayList<>());
    }

    private static class CountingSet<T> extends HashSet<T> {
        private final int count;

        CountingSet(int count) {
            this.count = count;
        }

        @Override
        public int size() {
            return count;
        }
    }

    private void setBaseCounts(int completed, int reserved) throws ReflectiveOperationException {
        setSetSize("baseHatcheries", completed);
        setSetSize("myBases", completed);
        setSetSize("reservedBases", reserved);
    }

    private void setSetSize(String fieldName, int size) throws ReflectiveOperationException {
        Field field = BaseData.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(baseData, new CountingSet<Base>(size));
    }

    private static UnitTypeCount withQueuedZerglingPlans(int plans) {
        UnitTypeCount count = new UnitTypeCount();
        for (int i = 0; i < plans; i++) {
            count.planUnit(UnitType.Zerg_Zergling);
        }
        return count;
    }

    private static UnitTypeCount withLivingZerglings(int zerglings) {
        UnitTypeCount count = new UnitTypeCount();
        for (int i = 0; i < zerglings; i++) {
            count.addUnit(UnitType.Zerg_Zergling);
        }
        return count;
    }

    /**
     * The case broken before IA-307: every opener queues the natural around 2:06, which reserved a base and
     * locked the main out of static defense until the hatchery completed.
     */
    @Test
    void testMainIsEligibleWhileNaturalIsOnlyReserved() throws ReflectiveOperationException {
        setBaseCounts(1, 1);

        Reactions.allowSunkenAtMainIfSingleBase(baseData);

        assertTrue(baseData.isAllowSunkenAtMain());
    }

    @Test
    void testMainIsEligibleWithASoleBase() throws ReflectiveOperationException {
        setBaseCounts(1, 0);

        Reactions.allowSunkenAtMainIfSingleBase(baseData);

        assertTrue(baseData.isAllowSunkenAtMain());
    }

    @Test
    void testMainIsNotMadeEligibleWithTwoCompletedBases() throws ReflectiveOperationException {
        setBaseCounts(2, 0);

        Reactions.allowSunkenAtMainIfSingleBase(baseData);

        assertFalse(baseData.isAllowSunkenAtMain());
    }

    /**
     * The flag survives until clearMainSunkenOnExpansion runs, so a second call once the natural has
     * completed must not be the thing that clears it.
     */
    @Test
    void testFlagIsNotClearedWhenTheNaturalCompletes() throws ReflectiveOperationException {
        setBaseCounts(1, 1);
        Reactions.allowSunkenAtMainIfSingleBase(baseData);

        setBaseCounts(2, 0);
        Reactions.allowSunkenAtMainIfSingleBase(baseData);

        assertTrue(baseData.isAllowSunkenAtMain());
    }

    @Test
    void testMainIsClearedOnceTheNaturalIsUpAndNothingIsPushing() throws ReflectiveOperationException {
        setBaseCounts(2, 0);
        baseData.setAllowSunkenAtMain(true);

        assertTrue(Reactions.shouldClearMainSunken(baseData, NO_BARRACKS_PRESSURE));
    }

    /**
     * Closing the gate cancels the main's queued creep colonies, so the reaction that raised the
     * sunken count on three Barracks must hold it open on the same threshold.
     */
    @Test
    void testMainStaysOpenUnderBarracksPressureWithTheNaturalUp() throws ReflectiveOperationException {
        setBaseCounts(2, 0);
        baseData.setAllowSunkenAtMain(true);

        assertFalse(Reactions.shouldClearMainSunken(baseData, UNDER_BARRACKS_PRESSURE));
    }

    @Test
    void testBarracksPressureGateMatchesTheCountTheBuildOrdersAskFor() {
        assertFalse(SunkenTargets.isBarracksPressure(SunkenTargets.BARRACKS_PRESSURE_COUNT - 1));
        assertTrue(SunkenTargets.isBarracksPressure(SunkenTargets.BARRACKS_PRESSURE_COUNT));
    }

    @Test
    void testNothingIsClearedWhileTheMainIsOurOnlyBase() throws ReflectiveOperationException {
        setBaseCounts(1, 1);
        baseData.setAllowSunkenAtMain(true);

        assertFalse(Reactions.shouldClearMainSunken(baseData, NO_BARRACKS_PRESSURE));
    }

    @Test
    void aQueuedZerglingPlanIsTwoFutureZerglingsAndNoLivingOne() {
        UnitTypeCount count = withQueuedZerglingPlans(QUEUED_ZERGLING_PLANS);

        assertEquals(2 * QUEUED_ZERGLING_PLANS, count.get(UnitType.Zerg_Zergling));
        assertEquals(0, count.livingCount(UnitType.Zerg_Zergling));
    }

    @Test
    void staysArmedWhileAFullZerglingQueueHasHatchedNothing() {
        UnitTypeCount count = withQueuedZerglingPlans(QUEUED_ZERGLING_PLANS);

        assertTrue(Reactions.isPreparingForEarlyRush(WITHIN_RUSH_WINDOW, count.livingCount(UnitType.Zerg_Zergling)));
        assertFalse(Reactions.isPreparingForEarlyRush(WITHIN_RUSH_WINDOW, count.get(UnitType.Zerg_Zergling)));
    }

    @Test
    void standsDownOnceTheSafeZerglingCountHatches() {
        UnitTypeCount count = withLivingZerglings(Reactions.EARLY_RUSH_SAFE_ZERGLINGS);

        assertFalse(Reactions.isPreparingForEarlyRush(WITHIN_RUSH_WINDOW, count.livingCount(UnitType.Zerg_Zergling)));
    }

    @Test
    void standsDownOutsideTheRushWindow() {
        assertFalse(Reactions.isPreparingForEarlyRush(false, 0));
    }

    @Test
    void cutsDronesWhileAFullZerglingQueueHasHatchedNothing() {
        UnitTypeCount count = withQueuedZerglingPlans(QUEUED_ZERGLING_PLANS);

        assertTrue(Reactions.shouldCutDrones(Reactions.EARLY_RUSH_DRONE_FLOOR, count.livingCount(UnitType.Zerg_Zergling)));
        assertFalse(Reactions.shouldCutDrones(Reactions.EARLY_RUSH_DRONE_FLOOR, count.get(UnitType.Zerg_Zergling)));
    }

    @Test
    void holdsTheDroneCutBelowTheDroneFloor() {
        assertFalse(Reactions.shouldCutDrones(Reactions.EARLY_RUSH_DRONE_FLOOR - 1, 0));
    }

    @Test
    void queuedDronesDoNotReachTheDroneFloor() {
        UnitTypeCount count = new UnitTypeCount();
        for (int i = 0; i < Reactions.EARLY_RUSH_DRONE_FLOOR; i++) {
            count.planUnit(UnitType.Zerg_Drone);
        }

        assertFalse(Reactions.shouldCutDrones(count.livingCount(UnitType.Zerg_Drone), 0));
        assertTrue(Reactions.shouldCutDrones(count.get(UnitType.Zerg_Drone), 0));
    }

    @Test
    void releasesTheDroneCutOnceTheZerglingsHatch() {
        UnitTypeCount count = withLivingZerglings(Reactions.EARLY_RUSH_CUT_ZERGLINGS);

        assertFalse(Reactions.shouldCutDrones(Reactions.EARLY_RUSH_DRONE_FLOOR, count.livingCount(UnitType.Zerg_Zergling)));
    }

    @Test
    void cutsDronesOnceForOneSustainedRush() {
        Reactions reactions = new Reactions(null);

        int cuts = 0;
        for (int frame = 0; frame < SUSTAINED_RUSH_FRAMES; frame++) {
            if (reactions.shouldFireDroneCut(Reactions.EARLY_RUSH_DRONE_FLOOR, 0)) {
                cuts++;
            }
        }

        assertEquals(1, cuts);
    }

    @Test
    void cutsDronesAgainAfterTheReactionStandsDown() {
        Reactions reactions = new Reactions(null);
        assertTrue(reactions.shouldFireDroneCut(Reactions.EARLY_RUSH_DRONE_FLOOR, 0));
        assertFalse(reactions.shouldFireDroneCut(Reactions.EARLY_RUSH_DRONE_FLOOR, 0));

        reactions.rearmEarlyRushCuts();

        assertTrue(reactions.shouldFireDroneCut(Reactions.EARLY_RUSH_DRONE_FLOOR, 0));
    }

    /**
     * The trigger is evaluated before the gate, so frames below the drone floor leave the cut
     * available. Reversing the two would spend it on a frame that cancels nothing.
     */
    @Test
    void holdsTheCutAvailableWhileTheTriggerIsFalse() {
        Reactions reactions = new Reactions(null);

        assertFalse(reactions.shouldFireDroneCut(Reactions.EARLY_RUSH_DRONE_FLOOR - 1, 0));
        assertTrue(reactions.shouldFireDroneCut(Reactions.EARLY_RUSH_DRONE_FLOOR, 0));
    }

    /**
     * IA-328: an extractor whose plan already reached COMPLETE, which for a building means the morph
     * was issued rather than finished, is in none of the plan sets cancelAllExtractors sweeps. Only
     * the raw cancelMorph loop reaches it, so that loop is what has to release the geyser.
     */
    @Test
    void theRawCancelReturnsTheGeyserToItsPrePlanState() throws ReflectiveOperationException {
        BaseData withGeyser = baseDataWithOneGeyser(GEYSER_TILE);
        withGeyser.reserveExtractor();
        assertEquals(1, withGeyser.numExtractor());

        assertTrue(Reactions.reclaimCancelledExtractor(withGeyser, GEYSER_TILE, CANCEL_FRAME));

        assertEquals(0, withGeyser.numExtractor());
        assertTrue(withGeyser.canReserveExtractor());
    }

    /**
     * The plan sweep that runs first drops the reservation but leaves the geyser unavailable, because
     * unreserveExtractor will not re-add a geyser whose unit still reports a refinery type. The raw
     * cancel is what has to finish the job, so it must not key off the reservation being present.
     */
    @Test
    void theRawCancelReclaimsAGeyserTheSweepAlreadyUnreserved() throws ReflectiveOperationException {
        BaseData withGeyser = baseDataWithOneGeyser(GEYSER_TILE);
        withGeyser.reserveExtractor();
        dropReservationWithoutReleasing(withGeyser);
        assertEquals(0, withGeyser.numExtractor());
        assertFalse(withGeyser.canReserveExtractor());

        assertTrue(Reactions.reclaimCancelledExtractor(withGeyser, GEYSER_TILE, CANCEL_FRAME));

        assertTrue(withGeyser.canReserveExtractor());
    }

    /**
     * The reaction fires every frame while it holds, so reclaiming twice must not hand the same geyser
     * out as two reservations, and must not write a second telemetry row.
     */
    @Test
    void repeatingTheRawCancelReclaimsTheGeyserOnlyOnce() throws ReflectiveOperationException {
        BaseData withGeyser = baseDataWithOneGeyser(GEYSER_TILE);
        withGeyser.reserveExtractor();

        assertTrue(Reactions.reclaimCancelledExtractor(withGeyser, GEYSER_TILE, CANCEL_FRAME));
        assertFalse(Reactions.reclaimCancelledExtractor(withGeyser, GEYSER_TILE, CANCEL_FRAME));
        assertEquals(0, withGeyser.numExtractor());
    }

    /**
     * IA-338: the raw cancel is the path that repeats. It hands the geyser back with nothing
     * recording that the release came from a cancellation, so the first-extractor branch re-opens
     * and the request lands again within a few hundred frames. Arming the hold is what stops it.
     */
    @Test
    void theRawCancelHoldsTheGeyserAgainstAnImmediateReplan() throws ReflectiveOperationException {
        BaseData withGeyser = baseDataWithOneGeyser(GEYSER_TILE);
        withGeyser.reserveExtractor();

        assertTrue(Reactions.reclaimCancelledExtractor(withGeyser, GEYSER_TILE, CANCEL_FRAME));

        assertTrue(withGeyser.getExtractorReplanBackoffUntil() > CANCEL_FRAME);
    }

    /**
     * A tile the reclaim tracks no geyser for is not a cancellation, so it must not push the hold
     * out. The reaction fires every frame while it holds and sweeps tiles it has already released.
     */
    @Test
    void aReclaimThatFindsNothingDoesNotArmTheHold() throws ReflectiveOperationException {
        BaseData withGeyser = baseDataWithOneGeyser(GEYSER_TILE);
        withGeyser.reserveExtractor();

        assertFalse(Reactions.reclaimCancelledExtractor(withGeyser, OTHER_TILE, CANCEL_FRAME));

        assertEquals(0, withGeyser.getExtractorReplanBackoffUntil());
    }

    @Test
    void theRawCancelIgnoresATileItTracksNoGeyserFor() throws ReflectiveOperationException {
        BaseData withGeyser = baseDataWithOneGeyser(GEYSER_TILE);
        withGeyser.reserveExtractor();

        assertFalse(Reactions.reclaimCancelledExtractor(withGeyser, OTHER_TILE, CANCEL_FRAME));

        assertEquals(1, withGeyser.numExtractor());
    }

    /**
     * A finished extractor is not cancelled, so its geyser stays reserved and no second extractor is
     * planned onto it.
     */
    @Test
    void aCompletedExtractorIsNotReached() throws ReflectiveOperationException {
        BaseData withGeyser = baseDataWithOneGeyser(GEYSER_TILE);
        withGeyser.reserveExtractor();

        if (Reactions.isCancellableExtractorMorph(UnitType.Zerg_Extractor, COMPLETE)) {
            Reactions.reclaimCancelledExtractor(withGeyser, GEYSER_TILE, CANCEL_FRAME);
        }

        assertEquals(1, withGeyser.numExtractor());
        assertFalse(withGeyser.canReserveExtractor());
    }

    @Test
    void onlyAnUnfinishedExtractorIsCancellable() {
        assertTrue(Reactions.isCancellableExtractorMorph(UnitType.Zerg_Extractor, INCOMPLETE));
        assertFalse(Reactions.isCancellableExtractorMorph(UnitType.Zerg_Extractor, COMPLETE));
        assertFalse(Reactions.isCancellableExtractorMorph(UnitType.Zerg_Drone, INCOMPLETE));
        assertFalse(Reactions.isCancellableExtractorMorph(UnitType.Zerg_Hatchery, INCOMPLETE));
    }

    /**
     * IA-341: the early rush reaction plans speed in every matchup instead of cancelling gas, so an
     * Extractor already in the queue is still there after the reaction has run.
     */
    @Test
    void theEarlyRushQueuesSpeedAheadOfProductionAndKeepsTheExtractor() {
        ProductionQueue queue = new ProductionQueue();
        Plan extractor = new BuildingPlan(UnitType.Zerg_Extractor, CANCEL_FRAME);
        queue.add(extractor);
        TechProgression techProgression = withSpawningPool();

        Reactions.planSpeedUpgrade(queue, techProgression, HAVE_EXTRACTOR, techProgression.canPlanMetabolicBoost(), CANCEL_FRAME);

        assertTrue(queue.toSortedList().contains(extractor));
        assertNull(extractor.getCancelSource());
        assertEquals(1, speedPlans(queue));
        assertEquals(Reactions.SPEED_UPGRADE_PRIORITY, queue.toSortedList().get(0).getPriority());
        assertTrue(techProgression.isPlannedMetabolicBoost());
    }

    /**
     * The reaction fires every frame while it holds, so the upgrade it plans must be queued once.
     */
    @Test
    void theEarlyRushQueuesSpeedOnceWhileItHolds() {
        ProductionQueue queue = new ProductionQueue();
        TechProgression techProgression = withSpawningPool();

        for (int frame = CANCEL_FRAME; frame < CANCEL_FRAME + SUSTAINED_RUSH_FRAMES; frame++) {
            Reactions.planSpeedUpgrade(queue, techProgression, HAVE_EXTRACTOR, techProgression.canPlanMetabolicBoost(), frame);
        }

        assertEquals(1, speedPlans(queue));
    }

    @Test
    void theEarlyRushPullsAnAlreadyQueuedSpeedUpgradeForward() {
        ProductionQueue queue = new ProductionQueue();
        queue.add(new UpgradePlan(UpgradeType.Metabolic_Boost, CANCEL_FRAME));
        TechProgression techProgression = withSpawningPool();
        techProgression.setPlannedMetabolicBoost(true);

        Reactions.planSpeedUpgrade(queue, techProgression, HAVE_EXTRACTOR, techProgression.canPlanMetabolicBoost(), CANCEL_FRAME);

        assertEquals(1, speedPlans(queue));
        assertEquals(Reactions.SPEED_UPGRADE_PRIORITY, queue.toSortedList().get(0).getPriority());
    }

    @Test
    void theEarlyRushWaitsForAnExtractorBeforePlanningSpeed() {
        ProductionQueue queue = new ProductionQueue();
        Plan extractor = new BuildingPlan(UnitType.Zerg_Extractor, CANCEL_FRAME);
        queue.add(extractor);
        TechProgression techProgression = withSpawningPool();

        Reactions.planSpeedUpgrade(queue, techProgression, NO_EXTRACTOR, techProgression.canPlanMetabolicBoost(), CANCEL_FRAME);

        assertEquals(0, speedPlans(queue));
        assertTrue(queue.toSortedList().contains(extractor));
        assertFalse(techProgression.isPlannedMetabolicBoost());
    }

    /**
     * A scheduled Lair's claim leaves too few free minerals for Metabolic Boost, so the upgrade is
     * swept for want of income. While speed is pending against Zerg the reaction drops the queued
     * Lair, and ProductionManager selects the scheduled one and cancels it through
     * GameState.cancelPlan, which hands its claim back; the bank then covers the upgrade.
     */
    @Test
    void theZvZEarlyRushCancelsLairSoMetabolicBoostSchedulesFirst() {
        ProductionQueue queue = new ProductionQueue();
        Plan queuedLair = new BuildingPlan(UnitType.Zerg_Lair, CANCEL_FRAME);
        queue.add(queuedLair);
        Plan scheduledLair = new BuildingPlan(UnitType.Zerg_Lair, CANCEL_FRAME);
        scheduledLair.setState(PlanState.SCHEDULE);
        Set<Plan> plansScheduled = new HashSet<>(Collections.singletonList(scheduledLair));
        TechProgression techProgression = withSpawningPool();
        Reactions.planSpeedUpgrade(queue, techProgression, HAVE_EXTRACTOR, techProgression.canPlanMetabolicBoost(), CANCEL_FRAME);
        Plan speed = speedPlan(queue);

        ResourceCount resourceCount = new ResourceCount(null);
        resourceCount.reserveUnit(scheduledLair.getPlannedUnit());
        int bankMinerals = UnitType.Zerg_Lair.mineralPrice() + FREE_MINERALS_AFTER_LAIR_CLAIM;
        int bankGas = UnitType.Zerg_Lair.gasPrice() + speed.gasPrice();
        assertFalse(canAfford(speed, bankMinerals, bankGas, resourceCount));

        boolean speedStarted = Reactions.isSpeedStarted(NOT_RESEARCHED, new HashSet<>());
        boolean delayLair = Reactions.shouldDelayLair(Race.Zerg, techProgression.isPlannedMetabolicBoost(), speedStarted);
        assertTrue(delayLair);
        assertTrue(new Reactions(null).shouldFireLairCancel(delayLair));

        List<Plan> cancelled = new ArrayList<>();
        Reactions.cancelQueuedLairs(queue, cancelled::add);
        Set<Plan> scheduledCancels = ProductionManager.delayedLairPlans(delayLair, plansScheduled);
        for (Plan plan : scheduledCancels) {
            resourceCount.unreserveUnit(plan.getPlannedUnit());
        }

        assertEquals(Collections.singletonList(queuedLair), cancelled);
        assertEquals(PlanCancelSource.REACTION_EARLY_RUSH_LAIR, queuedLair.getCancelSource());
        assertEquals(Collections.singleton(scheduledLair), scheduledCancels);
        assertEquals(Collections.singletonList(speed), queue.toSortedList());
        assertTrue(canAfford(speed, bankMinerals, bankGas, resourceCount));
    }

    @Test
    void theZvZEarlyRushAllowsTheLairOnceSpeedHasStarted() {
        Plan speed = new UpgradePlan(UpgradeType.Metabolic_Boost, CANCEL_FRAME);
        Set<Plan> plansBuilding = new HashSet<>();
        Reactions reactions = new Reactions(null);
        boolean pending = Reactions.shouldDelayLair(Race.Zerg, SPEED_PLANNED, Reactions.isSpeedStarted(NOT_RESEARCHED, plansBuilding));
        assertTrue(reactions.shouldFireLairCancel(pending));

        plansBuilding.add(speed);
        boolean researching = Reactions.shouldDelayLair(Race.Zerg, SPEED_PLANNED, Reactions.isSpeedStarted(NOT_RESEARCHED, plansBuilding));
        boolean researched = Reactions.shouldDelayLair(Race.Zerg, SPEED_NOT_PLANNED, Reactions.isSpeedStarted(RESEARCHED, new HashSet<>()));

        assertFalse(researching);
        assertFalse(researched);
        assertFalse(reactions.shouldFireLairCancel(researching));
        assertTrue(ProductionManager.delayedLairPlans(researching, new HashSet<>(Collections.singletonList(lair(PlanState.SCHEDULE)))).isEmpty());
    }

    /**
     * Only a plan in the building set has begun research. A scheduled speed plan still holds its
     * claim, so reading it as started would free the Lair while the upgrade is unpaid.
     */
    @Test
    void speedCountsAsStartedOnlyOnceResearchBegins() {
        Plan speed = new UpgradePlan(UpgradeType.Metabolic_Boost, CANCEL_FRAME);

        assertFalse(Reactions.isSpeedStarted(NOT_RESEARCHED, new HashSet<>()));
        assertFalse(Reactions.isSpeedStarted(NOT_RESEARCHED, new HashSet<>(Collections.singletonList(lair(PlanState.BUILDING)))));
        assertTrue(Reactions.isSpeedStarted(NOT_RESEARCHED, new HashSet<>(Collections.singletonList(speed))));
        assertTrue(Reactions.isSpeedStarted(RESEARCHED, new HashSet<>()));
    }

    private static Plan lair(PlanState state) {
        Plan plan = new BuildingPlan(UnitType.Zerg_Lair, CANCEL_FRAME);
        plan.setState(state);
        return plan;
    }

    @Test
    void theZvZEarlyRushDoesNotDelayTheLairBeforeSpeedIsPlanned() {
        assertFalse(Reactions.shouldDelayLair(Race.Zerg, SPEED_NOT_PLANNED, SPEED_NOT_STARTED));
    }

    @Test
    void theEarlyRushDelaysTheLairAgainstProtossWhateverTheSpeedState() {
        assertTrue(Reactions.shouldDelayLair(Race.Protoss, SPEED_NOT_PLANNED, SPEED_NOT_STARTED));
        assertTrue(Reactions.shouldDelayLair(Race.Protoss, SPEED_PLANNED, SPEED_NOT_STARTED));
        assertTrue(Reactions.shouldDelayLair(Race.Protoss, SPEED_PLANNED, SPEED_STARTED));
        assertTrue(Reactions.shouldDelayLair(Race.Protoss, SPEED_NOT_PLANNED, SPEED_STARTED));
    }

    @Test
    void theEarlyRushDoesNotDelayTheLairAgainstTerranOrAnUnresolvedRace() {
        assertFalse(Reactions.shouldDelayLair(Race.Terran, SPEED_PLANNED, SPEED_NOT_STARTED));
        assertFalse(Reactions.shouldDelayLair(Race.Unknown, SPEED_PLANNED, SPEED_NOT_STARTED));
    }

    @Test
    void cancelsQueuedLairsOncePerDelayWindow() {
        Reactions reactions = new Reactions(null);

        int cancels = 0;
        for (int frame = 0; frame < SUSTAINED_RUSH_FRAMES; frame++) {
            if (reactions.shouldFireLairCancel(true)) {
                cancels++;
            }
        }

        assertEquals(1, cancels);
    }

    /**
     * A speed plan swept and planned again reopens the window, and a Lair queued while it was shut
     * must be dropped again.
     */
    @Test
    void cancelsQueuedLairsAgainWhenTheDelayReopens() {
        Reactions reactions = new Reactions(null);
        assertTrue(reactions.shouldFireLairCancel(true));
        assertFalse(reactions.shouldFireLairCancel(true));

        assertFalse(reactions.shouldFireLairCancel(false));

        assertTrue(reactions.shouldFireLairCancel(true));
    }

    private static boolean canAfford(Plan plan, int bankMinerals, int bankGas, ResourceCount resourceCount) {
        return bankMinerals - resourceCount.getReservedMinerals() >= plan.mineralPrice()
                && bankGas - resourceCount.getReservedGas() >= plan.gasPrice();
    }

    private static Plan speedPlan(ProductionQueue queue) {
        return queue.toSortedList()
                .stream()
                .filter(p -> p.getType() == PlanType.UPGRADE)
                .filter(p -> ((UpgradePlan) p).getPlannedUpgrade() == UpgradeType.Metabolic_Boost)
                .findFirst()
                .orElseThrow(IllegalStateException::new);
    }

    private static TechProgression withSpawningPool() {
        TechProgression techProgression = new TechProgression();
        techProgression.setSpawningPool(true);
        return techProgression;
    }

    private static long speedPlans(ProductionQueue queue) {
        return queue.toSortedList()
                .stream()
                .filter(p -> p.getType() == PlanType.UPGRADE)
                .filter(p -> ((UpgradePlan) p).getPlannedUpgrade() == UpgradeType.Metabolic_Boost)
                .count();
    }

    private static BaseData baseDataWithOneGeyser(TilePosition tile) throws ReflectiveOperationException {
        BaseData baseData = new BaseData(new ArrayList<>());
        HashSet<Unit> available = new HashSet<>();
        available.add(null);
        HashMap<Unit, TilePosition> positions = new HashMap<>();
        positions.put(null, tile);
        setField(baseData, "availableGeysers", available);
        setField(baseData, "geyserPositionLookup", positions);
        return baseData;
    }

    private static void dropReservationWithoutReleasing(BaseData baseData) throws ReflectiveOperationException {
        setField(baseData, "extractors", new HashSet<Unit>());
    }

    private static void setField(BaseData baseData, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = BaseData.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(baseData, value);
    }
}
