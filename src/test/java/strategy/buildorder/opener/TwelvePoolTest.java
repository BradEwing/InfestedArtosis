package strategy.buildorder.opener;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwelvePoolTest {

    private static final int NO_POOL = 0;

    private static final int ONE_POOL = 1;

    private static final int NO_ZERGLINGS = 0;

    private static final int ELEVEN_DRONES = 11;

    private static final int TWELVE_DRONES = 12;

    private static final int ZERGLINGS_PER_PLAN = 2;

    /** LVOUC0M9 frame 2062: the pool was queued the frame before and the opener handed off. */
    @Test
    void holdsOnACommittedButUnfinishedSpawningPool() {
        assertFalse(TwelvePool.openerComplete(ONE_POOL, ELEVEN_DRONES, NO_ZERGLINGS));
        assertFalse(TwelvePool.openerComplete(ONE_POOL, TWELVE_DRONES, NO_ZERGLINGS));
    }

    @Test
    void holdsWhileFewerThanSixZerglingsAreQueued() {
        assertFalse(TwelvePool.openerComplete(ONE_POOL, ELEVEN_DRONES, TwelvePool.OPENING_ZERGLINGS - ZERGLINGS_PER_PLAN));
    }

    @Test
    void handsOffOnceSixZerglingsAreQueued() {
        assertTrue(TwelvePool.openerComplete(ONE_POOL, ELEVEN_DRONES, TwelvePool.OPENING_ZERGLINGS));
    }

    @Test
    void holdsWhileNoSpawningPoolIsCommittedTo() {
        assertFalse(TwelvePool.openerComplete(NO_POOL, ELEVEN_DRONES, NO_ZERGLINGS));
    }

    @Test
    void recoversOnTwelveLivingDronesWithoutASpawningPool() {
        assertTrue(TwelvePool.openerComplete(NO_POOL, TWELVE_DRONES, NO_ZERGLINGS));
    }

    @Test
    void takesDronesToTwelveBeforeThePool() {
        assertTrue(TwelvePool.shouldPlanDrone(ELEVEN_DRONES, NO_POOL, NO_ZERGLINGS));
        assertFalse(TwelvePool.shouldPlanDrone(TWELVE_DRONES, NO_POOL, NO_ZERGLINGS));
    }

    /** LVOUC0M9: replacement Drones #13-#15 took every larva made during the pool build. */
    @Test
    void holdsReplacementDronesOnceThePoolIsCommitted() {
        assertFalse(TwelvePool.shouldPlanDrone(ELEVEN_DRONES, ONE_POOL, NO_ZERGLINGS));
        assertFalse(TwelvePool.shouldPlanDrone(ELEVEN_DRONES, ONE_POOL, TwelvePool.OPENING_ZERGLINGS - ZERGLINGS_PER_PLAN));
    }

    @Test
    void releasesReplacementDronesOnceTheOpeningZerglingsAreQueued() {
        assertTrue(TwelvePool.shouldPlanDrone(ELEVEN_DRONES, ONE_POOL, TwelvePool.OPENING_ZERGLINGS));
    }

    /**
     * The army branch waits on a finished pool. A pool that is only committed to cannot morph a
     * zergling, so a plan queued against one holds a larva for the rest of the pool build.
     */
    @Test
    void withholdsZerglingsUntilTheSpawningPoolStands() {
        assertFalse(TwelvePool.shouldPlanZergling(NO_POOL, NO_ZERGLINGS));
    }

    @Test
    void queuesExactlyThreeZerglingPlansAtZeroTwoAndFour() {
        List<Integer> queuedAt = new ArrayList<>();
        int zerglingCount = NO_ZERGLINGS;
        for (int frame = 0; frame < 10; frame++) {
            if (TwelvePool.shouldPlanZergling(ONE_POOL, zerglingCount)) {
                queuedAt.add(zerglingCount);
                zerglingCount += ZERGLINGS_PER_PLAN;
            }
        }
        assertEquals(Arrays.asList(0, 2, 4), queuedAt);
        assertFalse(TwelvePool.shouldPlanZergling(ONE_POOL, TwelvePool.OPENING_ZERGLINGS));
    }
}
