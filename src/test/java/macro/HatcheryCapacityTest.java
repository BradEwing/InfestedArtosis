package macro;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HatcheryCapacityTest {

    private static final int SATURATED_HATCHERIES = HatcheryCapacity.EXCESS_HATCHERIES;

    private static final int IDLE_LARVA = HatcheryCapacity.EXCESS_LARVA;

    @Test
    void hatcheriesAreExcessOnceTheirLarvaGoUnspent() {
        assertTrue(HatcheryCapacity.isExcess(SATURATED_HATCHERIES, IDLE_LARVA, false));
    }

    @Test
    void hatcheriesAreNotExcessWhileLarvaAreSpent() {
        assertFalse(HatcheryCapacity.isExcess(SATURATED_HATCHERIES, IDLE_LARVA - 1, false));
    }

    @Test
    void hatcheriesAreNotExcessBelowTheHatcheryFloor() {
        assertFalse(HatcheryCapacity.isExcess(SATURATED_HATCHERIES - 1, IDLE_LARVA * 2, false));
    }

    @Test
    void floatingMineralsOverrideIdleLarva() {
        assertFalse(HatcheryCapacity.isExcess(SATURATED_HATCHERIES, IDLE_LARVA, true));
    }

    @Test
    void weAreNotBehindOnHatcheriesWeCannotKeepBusy() {
        boolean excess = HatcheryCapacity.isExcess(SATURATED_HATCHERIES, IDLE_LARVA, false);

        assertFalse(HatcheryCapacity.isBehind(SATURATED_HATCHERIES, SATURATED_HATCHERIES + 1, excess, false));
    }

    @Test
    void weAreBehindWhenTheEnemyOutExpandsUsAndOurLarvaAreSpent() {
        boolean excess = HatcheryCapacity.isExcess(SATURATED_HATCHERIES, IDLE_LARVA - 1, false);

        assertTrue(HatcheryCapacity.isBehind(SATURATED_HATCHERIES, SATURATED_HATCHERIES + 1, excess, false));
    }

    /**
     * No count below SATURATED_HATCHERIES is excess. Parity therefore always expands from one or
     * two bases.
     */
    @Test
    void parityStillExpandsFromOneAndTwoBases() {
        for (int ourTotal = 1; ourTotal < SATURATED_HATCHERIES; ourTotal++) {
            boolean excess = HatcheryCapacity.isExcess(ourTotal, IDLE_LARVA * 2, false);

            assertTrue(HatcheryCapacity.isBehind(ourTotal, ourTotal + 1, excess, false));
        }
    }

    @Test
    void weAreNotBehindAtParity() {
        assertFalse(HatcheryCapacity.isBehind(2, 2, false, false));
    }

    @Test
    void floatingMineralsStillExpandAtSaturation() {
        boolean excess = HatcheryCapacity.isExcess(SATURATED_HATCHERIES, IDLE_LARVA, true);

        assertTrue(HatcheryCapacity.isBehind(SATURATED_HATCHERIES, SATURATED_HATCHERIES + 1, excess, false));
    }

    @Test
    void theTwoRulesNeverDisagree() {
        for (int hatcheries = 0; hatcheries <= 6; hatcheries++) {
            for (int larva = 0; larva <= 12; larva++) {
                boolean excess = HatcheryCapacity.isExcess(hatcheries, larva, false);
                boolean behind = HatcheryCapacity.isBehind(hatcheries, hatcheries + 1, excess, false);

                assertFalse(excess && behind);
            }
        }
    }

    @Test
    void noParityRequestWhileTheEarlyRushReactionDeletesExpansions() {
        boolean excess = HatcheryCapacity.isExcess(SATURATED_HATCHERIES - 1, IDLE_LARVA - 1, false);

        assertTrue(HatcheryCapacity.isBehind(SATURATED_HATCHERIES - 1, SATURATED_HATCHERIES, excess, false));
        assertFalse(HatcheryCapacity.isBehind(SATURATED_HATCHERIES - 1, SATURATED_HATCHERIES, excess, true));
    }

    @Test
    void noFloatingRequestWhileTheEarlyRushReactionDeletesExpansions() {
        assertTrue(HatcheryCapacity.isFloatingExpansion(true, false));
        assertFalse(HatcheryCapacity.isFloatingExpansion(true, true));
        assertFalse(HatcheryCapacity.isFloatingExpansion(false, false));
    }

    /**
     * A state that requests a hatchery never simultaneously satisfies the rule that cancels
     * queued hatcheries, whatever is queued. Both rules are fed the same completed-hatchery
     * count here, exactly as GameState feeds them hatcheryCount().
     */
    @Test
    void aStateThatRequestsAHatcheryIsNeverOneTheExcessRuleCancels() {
        for (int hatcheries = 0; hatcheries <= 6; hatcheries++) {
            for (int queued = 0; queued <= 3; queued++) {
                for (int larva = 0; larva <= 12; larva++) {
                    boolean excess = HatcheryCapacity.isExcess(hatcheries, larva, false);
                    boolean behind = HatcheryCapacity.isBehind(hatcheries + queued, hatcheries + queued + 1, excess, false);

                    assertFalse(excess && behind);
                }
            }
        }
    }

    /**
     * Two larva-producing hatcheries, five idle larva, an enemy one depot ahead. The request
     * must commit once and survive, not be re-created every frame.
     */
    @Test
    void aHatcheryRequestSurvivesTheFrameThatUsedToCancelIt() {
        int completedHatcheries = SATURATED_HATCHERIES - 1;
        int queuedHatcheries = 0;
        int enemyTotal = SATURATED_HATCHERIES;
        int enqueues = 0;

        for (int frame = 0; frame < 120; frame++) {
            boolean excess = HatcheryCapacity.isExcess(completedHatcheries, IDLE_LARVA, false);
            if (excess && queuedHatcheries > 0) {
                queuedHatcheries--;
            }
            boolean behind = HatcheryCapacity.isBehind(completedHatcheries + queuedHatcheries, enemyTotal, excess, false);
            if (behind) {
                enqueues++;
                queuedHatcheries++;
            }
        }

        assertEquals(1, enqueues);
        assertEquals(1, queuedHatcheries);
    }

    /**
     * While the early-rush reaction holds, neither hatchery trigger requests anything: the
     * reaction would delete the plan on the same frame it is queued.
     */
    @Test
    void noHatcheryIsRequestedWhileTheEarlyRushReactionHolds() {
        int enqueues = 0;

        for (int frame = 0; frame < 120; frame++) {
            boolean excess = HatcheryCapacity.isExcess(SATURATED_HATCHERIES - 1, IDLE_LARVA - 1, false);
            if (HatcheryCapacity.isBehind(SATURATED_HATCHERIES - 1, SATURATED_HATCHERIES, excess, true)) {
                enqueues++;
            }
            if (HatcheryCapacity.isFloatingExpansion(true, true)) {
                enqueues++;
            }
        }

        assertEquals(0, enqueues);
    }
}
