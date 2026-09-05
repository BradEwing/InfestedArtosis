package macro;

import bwapi.UnitType;
import bwem.Base;
import info.BaseData;
import info.UnitTypeCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import util.OneShotGate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the main-base sunken gate and the early rush guards.
 *
 * <p>bwem.Base is final with a package-private constructor and bwapi.Unit cannot be instantiated outside its own
 * package, so base counts are simulated by swapping in sets that report a fixed size.
 */
public class ReactionsTest {

    private static final boolean WITHIN_RUSH_WINDOW = true;

    private static final int QUEUED_ZERGLING_PLANS = 6;

    private static final int SUSTAINED_RUSH_FRAMES = 2000;

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

    /**
     * The IA-313 livelock: the cut removes the drone production that would clear its own trigger, so
     * every frame of a sustained rush cancelled the queue again. One detection is one cut.
     */
    @Test
    void cutsDronesOnceForOneSustainedDetection() {
        OneShotGate droneCut = new OneShotGate();

        int cuts = 0;
        for (int frame = 0; frame < SUSTAINED_RUSH_FRAMES; frame++) {
            if (Reactions.shouldCutDrones(Reactions.EARLY_RUSH_DRONE_FLOOR, 0) && droneCut.fire()) {
                cuts++;
            }
        }

        assertEquals(1, cuts);
    }

    @Test
    void cutsDronesAgainForTheNextDetection() {
        OneShotGate droneCut = new OneShotGate();
        assertTrue(droneCut.fire());
        assertFalse(droneCut.fire());

        droneCut.rearm();

        assertTrue(droneCut.fire());
    }
}
