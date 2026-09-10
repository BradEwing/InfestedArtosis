package info;

import bwapi.UnitType;
import bwem.Base;
import info.map.GroundPath;
import macro.plan.PlanCancelReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for BaseData expansion selection.
 *
 * <p>bwem.Base is final with a package-private constructor and bwapi.Unit cannot be instantiated outside its own
 * package, so BaseData is exercised with no bases at all.
 */
public class BaseDataTest {

    private static final int EXECUTOR_LOST_BUDGET = 10;

    private static final int GAME_FRAMES = 36000;

    private static final int WALK_FRAMES = 410;

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
