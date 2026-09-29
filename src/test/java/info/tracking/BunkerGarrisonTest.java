package info.tracking;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BunkerGarrisonTest {

    private static final int UNKNOWN = -1;

    @Test
    void twoUnseenBunkersShareTheMarinesThatExistInsteadOfEachHoldingAFullGarrison() {
        assertArrayEquals(new int[] {4, 0}, BunkerGarrison.believed(Arrays.asList(UNKNOWN, UNKNOWN), 4));
        assertEquals(4, BunkerGarrison.believedTotal(Arrays.asList(UNKNOWN, UNKNOWN), 4));
    }

    @Test
    void anUnseenBunkerHoldsAtMostAFullGarrison() {
        assertArrayEquals(new int[] {4, 4}, BunkerGarrison.believed(Arrays.asList(UNKNOWN, UNKNOWN), 12));
    }

    @Test
    void aBunkerSeenEmptyHoldsNothingAndLeavesTheInfantryToTheUnseenOne() {
        assertArrayEquals(new int[] {0, 4}, BunkerGarrison.believed(Arrays.asList(0, UNKNOWN), 4));
    }

    @Test
    void aSeenGarrisonIsClaimedBeforeTheUnseenBunkersShareTheRest() {
        assertArrayEquals(new int[] {1, 2}, BunkerGarrison.believed(Arrays.asList(UNKNOWN, 2), 3));
    }

    @Test
    void aSeenGarrisonNeverExceedsTheInfantryStillAlive() {
        assertArrayEquals(new int[] {1}, BunkerGarrison.believed(Collections.singletonList(4), 1));
    }

    @Test
    void noBunkersHoldNothing() {
        assertEquals(0, BunkerGarrison.believedTotal(Collections.emptyList(), 8));
    }

    @Test
    void anEstimateIsTrustedUpToTheTrustWindowAndUnknownAfterIt() {
        assertEquals(2, BunkerGarrison.trustedEstimate(2, 1000, 1000 + BunkerGarrison.TRUST_FRAMES));
        assertEquals(UNKNOWN, BunkerGarrison.trustedEstimate(2, 1000, 1001 + BunkerGarrison.TRUST_FRAMES));
    }

    @Test
    void aBunkerNeverEstimatedOrNeverCheckedIsUnknown() {
        assertEquals(UNKNOWN, BunkerGarrison.trustedEstimate(UNKNOWN, 1000, 1000));
        assertEquals(UNKNOWN, BunkerGarrison.trustedEstimate(3, UNKNOWN, 1000));
    }
}
