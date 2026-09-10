package macro;

import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwem.Base;
import info.BaseData;
import info.UnitTypeCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import strategy.buildorder.SunkenTargets;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static final boolean COMPLETE = true;

    private static final boolean INCOMPLETE = false;

    private static final boolean UNDER_BARRACKS_PRESSURE = true;

    private static final boolean NO_BARRACKS_PRESSURE = false;

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

        assertTrue(Reactions.reclaimCancelledExtractor(withGeyser, GEYSER_TILE));

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

        assertTrue(Reactions.reclaimCancelledExtractor(withGeyser, GEYSER_TILE));

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

        assertTrue(Reactions.reclaimCancelledExtractor(withGeyser, GEYSER_TILE));
        assertFalse(Reactions.reclaimCancelledExtractor(withGeyser, GEYSER_TILE));
        assertEquals(0, withGeyser.numExtractor());
    }

    @Test
    void theRawCancelIgnoresATileItTracksNoGeyserFor() throws ReflectiveOperationException {
        BaseData withGeyser = baseDataWithOneGeyser(GEYSER_TILE);
        withGeyser.reserveExtractor();

        assertFalse(Reactions.reclaimCancelledExtractor(withGeyser, OTHER_TILE));

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
            Reactions.reclaimCancelledExtractor(withGeyser, GEYSER_TILE);
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
