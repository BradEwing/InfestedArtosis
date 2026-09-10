package info;

import bwapi.TilePosition;
import bwapi.Unit;
import bwem.Base;
import info.map.GroundPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import bwapi.UnitType;
import macro.plan.PlanCancelReason;

/**
 * Unit tests for BaseData expansion selection and geyser reservation release.
 *
 * <p>bwem.Base is final with a package-private constructor and bwapi.Unit cannot be instantiated outside its own
 * package, so BaseData is exercised with no bases at all, and the reservation tests stand a null in for the geyser
 * unit: the release path identifies a reservation by its stored tile position and calls nothing on the unit.
 */
public class BaseDataTest {

    private static final int EXECUTOR_LOST_BUDGET = 10;

    private static final int GAME_FRAMES = 36000;

    private static final int WALK_FRAMES = 410;

    private static final TilePosition GEYSER_TILE = new TilePosition(20, 30);

    private static final TilePosition OTHER_TILE = new TilePosition(44, 12);

    private BaseData baseData;

    @BeforeEach
    void setUp() {
        baseData = new BaseData(new ArrayList<>());
    }

    private void setAvailableBases(HashMap<Base, GroundPath> bases) throws ReflectiveOperationException {
        Field field = BaseData.class.getDeclaredField("availableBases");
        field.setAccessible(true);
        field.set(baseData, bases);
    }

    /**
     * The reclaim runs while the unit still reports a refinery type, which is what unreserveExtractor
     * refuses to act on, so it has to return the geyser to the available pool on its own.
     */
    @Test
    void releaseReservedGeyserReturnsTheGeyserToTheAvailablePool() {
        HashSet<Unit> reserved = new HashSet<>();
        reserved.add(null);
        HashSet<Unit> available = new HashSet<>();
        HashMap<Unit, TilePosition> positions = new HashMap<>();
        positions.put(null, GEYSER_TILE);

        assertTrue(BaseData.releaseReservedGeyser(reserved, available, positions, GEYSER_TILE));

        assertTrue(reserved.isEmpty());
        assertEquals(1, available.size());
    }

    /**
     * The plan sweep drops the reservation without making the geyser available again, so a release
     * keyed off the reservation set would find nothing and strand the geyser in neither set.
     */
    @Test
    void releaseReservedGeyserRecoversAGeyserThatIsNoLongerReserved() {
        HashSet<Unit> available = new HashSet<>();
        HashMap<Unit, TilePosition> positions = new HashMap<>();
        positions.put(null, GEYSER_TILE);

        assertTrue(BaseData.releaseReservedGeyser(new HashSet<>(), available, positions, GEYSER_TILE));

        assertEquals(1, available.size());
    }

    @Test
    void releaseReservedGeyserReportsNothingNewWhenTheGeyserIsAlreadyAvailable() {
        HashSet<Unit> available = new HashSet<>();
        available.add(null);
        HashMap<Unit, TilePosition> positions = new HashMap<>();
        positions.put(null, GEYSER_TILE);

        assertFalse(BaseData.releaseReservedGeyser(new HashSet<>(), available, positions, GEYSER_TILE));

        assertEquals(1, available.size());
    }

    @Test
    void releaseReservedGeyserIgnoresATileItTracksNoGeyserFor() {
        HashSet<Unit> reserved = new HashSet<>();
        reserved.add(null);
        HashSet<Unit> available = new HashSet<>();
        HashMap<Unit, TilePosition> positions = new HashMap<>();
        positions.put(null, GEYSER_TILE);

        assertFalse(BaseData.releaseReservedGeyser(reserved, available, positions, OTHER_TILE));

        assertEquals(1, reserved.size());
        assertTrue(available.isEmpty());
    }

    @Test
    void releaseReservedGeyserToleratesAMissingTilePosition() {
        assertFalse(BaseData.releaseReservedGeyser(new HashSet<>(), new HashSet<>(), new HashMap<>(), null));
    }

    @Test
    void testFindNewBaseWithNoBasesReturnsNull() {
        assertTrue(baseData.getMyBases().isEmpty());
        assertNull(baseData.findNewBase());
    }

    /**
     * A candidate with no known ground path is not a viable expansion and must simply be skipped.
     */
    @Test
    void testFindNewBaseSkipsCandidatesWithoutAGroundPath() throws ReflectiveOperationException {
        HashMap<Base, GroundPath> pool = new HashMap<>();
        pool.put(null, null);
        setAvailableBases(pool);

        assertNull(baseData.findNewBase());
    }

    /**
     * bwem.Base cannot be constructed here, so the reservation counters are exercised on a null
     * key. The lookups are plain maps and treat it as they would any other base.
     */
    @Test
    void releasingASporeReservationReturnsTheTotalToItsPrePlanValue() {
        int before = baseData.getTotalSporeCount();

        baseData.reserveSporeColony(null);
        assertEquals(before + 1, baseData.getTotalSporeCount());

        baseData.unreserveSporeColony(null);
        assertEquals(before, baseData.getTotalSporeCount());
    }

    @Test
    void aSporeReservationReleasedTwiceCannotDriveTheTotalBelowZero() {
        baseData.reserveSporeColony(null);
        baseData.unreserveSporeColony(null);
        baseData.unreserveSporeColony(null);

        assertEquals(0, baseData.getTotalSporeCount());
    }

    @Test
    void testOnlyALostHatcheryBuilderBacksOffExpansion() {
        assertTrue(BaseData.shouldBackoffExpansion(UnitType.Zerg_Hatchery, PlanCancelReason.EXECUTOR_LOST));
        assertFalse(BaseData.shouldBackoffExpansion(UnitType.Zerg_Hatchery, PlanCancelReason.EXCESS_HATCHERY));
        assertFalse(BaseData.shouldBackoffExpansion(UnitType.Zerg_Hatchery, PlanCancelReason.UNKNOWN));
        assertFalse(BaseData.shouldBackoffExpansion(UnitType.Zerg_Extractor, PlanCancelReason.EXECUTOR_LOST));
    }

    @Test
    void testBackoffWindowRunsToItsLastFrame() {
        int lostAt = 14000;
        int until = lostAt + BaseData.expansionHold(1);

        assertFalse(BaseData.isExpansionAvailable(until, lostAt));
        assertFalse(BaseData.isExpansionAvailable(until, until - 1));
        assertTrue(BaseData.isExpansionAvailable(until, until));
    }

    /**
     * A base that has never lost a builder is not held at all, which is what keeps the opening
     * expansion untouched.
     */
    @Test
    void testNoLostBuilderMeansNoHold() {
        assertEquals(0, BaseData.expansionHold(0));
        assertTrue(BaseData.isExpansionAvailable(0, 0));
    }

    @Test
    void testHoldGrowsWithLostBuildersThenStops() {
        assertEquals(BaseData.EXPANSION_BACKOFF_FRAMES, BaseData.expansionHold(1));
        assertEquals(2 * BaseData.EXPANSION_BACKOFF_FRAMES, BaseData.expansionHold(2));

        int capped = BaseData.MAX_EXPANSION_BACKOFF_STEPS * BaseData.EXPANSION_BACKOFF_FRAMES;
        assertEquals(capped, BaseData.expansionHold(BaseData.MAX_EXPANSION_BACKOFF_STEPS));
        assertEquals(capped, BaseData.expansionHold(BaseData.MAX_EXPANSION_BACKOFF_STEPS + 40));
    }

    /**
     * IA-331 acceptance criterion 3: when the hold lifts, the base that lost the builder must
     * still be excluded, or the retry goes straight back to the same unreachable location.
     */
    @Test
    void theBaseThatLostTheBuilderOutlastsTheHold() {
        int lostAt = 14000;
        int baseHeldUntil = lostAt + BaseData.expansionHold(BaseData.MAX_EXPANSION_BACKOFF_STEPS);

        for (int lostBuilders = 1; lostBuilders < BaseData.MAX_EXPANSION_BACKOFF_STEPS; lostBuilders++) {
            int expansionsResumeAt = lostAt + BaseData.expansionHold(lostBuilders);
            assertFalse(BaseData.isExpansionAvailable(baseHeldUntil, expansionsResumeAt));
        }
    }

    /**
     * IA-331 acceptance criterion 4: a game may not cancel more than
     * {@link #EXECUTOR_LOST_BUDGET} Hatchery plans with cancel_source PRODUCTION_EXECUTOR_LOST.
     * L41YX09D cancelled 51, which is what an unheld retry produces: the expansion request stayed
     * true, so a hatchery went back on the queue the frame after each cancel.
     */
    @Test
    void testLostBuilderCancelsStayWithinTheGameBudget() {
        assertTrue(retriesPerGame(true) > EXECUTOR_LOST_BUDGET);
        assertTrue(retriesPerGame(false) <= EXECUTOR_LOST_BUDGET);
    }

    /**
     * Replays a game whose expansion request never goes false and whose builder never survives the
     * walk, which is the L41YX09D regime.
     *
     * @param unheld true to model the behaviour before the hold existed
     * @return hatchery plans cancelled with a lost builder over one game
     */
    private int retriesPerGame(boolean unheld) {
        int heldUntil = 0;
        int lostBuilders = 0;
        int cancels = 0;
        for (int frame = 0; frame < GAME_FRAMES; frame++) {
            if (!unheld && !BaseData.isExpansionAvailable(heldUntil, frame)) {
                continue;
            }
            lostBuilders += 1;
            cancels += 1;
            heldUntil = frame + WALK_FRAMES + BaseData.expansionHold(lostBuilders);
            frame += WALK_FRAMES;
        }
        return cancels;
    }
}
