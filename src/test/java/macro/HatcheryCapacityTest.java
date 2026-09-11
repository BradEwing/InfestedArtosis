package macro;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HatcheryCapacityTest {

    private static final int SATURATED_HATCHERIES = HatcheryCapacity.EXCESS_HATCHERIES;

    private static final int IDLE_LARVA = HatcheryCapacity.EXCESS_LARVA;

    /** Far longer than the cooldown, so only the outstanding count can hold the request. */
    private static final int A_LONG_HOLD = 1800;

    @Test
    void hatcheriesAreExcessOnceTheirLarvaGoUnspent() {
        assertTrue(HatcheryCapacity.isExcess(SATURATED_HATCHERIES, IDLE_LARVA));
    }

    @Test
    void hatcheriesAreNotExcessWhileLarvaAreSpent() {
        assertFalse(HatcheryCapacity.isExcess(SATURATED_HATCHERIES, IDLE_LARVA - 1));
    }

    @Test
    void hatcheriesAreNotExcessBelowTheHatcheryFloor() {
        assertFalse(HatcheryCapacity.isExcess(SATURATED_HATCHERIES - 1, IDLE_LARVA * 2));
    }

    /**
     * The excess rule reads capacity alone, so a mineral pile that asks for an expansion cannot
     * switch off the rule that cancels the expansion it asked for.
     */
    @Test
    void theExcessRuleFiresWhileMineralsFloat() {
        assertTrue(HatcheryCapacity.isFloatingMinerals(2000, SATURATED_HATCHERIES, true));
        assertTrue(HatcheryCapacity.isExcess(SATURATED_HATCHERIES, IDLE_LARVA));
    }

    @Test
    void weAreNotBehindOnHatcheriesWeCannotKeepBusy() {
        boolean excess = HatcheryCapacity.isExcess(SATURATED_HATCHERIES, IDLE_LARVA);

        assertFalse(HatcheryCapacity.isBehind(SATURATED_HATCHERIES, SATURATED_HATCHERIES + 1, excess, false));
    }

    @Test
    void weAreBehindWhenTheEnemyOutExpandsUsAndOurLarvaAreSpent() {
        boolean excess = HatcheryCapacity.isExcess(SATURATED_HATCHERIES, IDLE_LARVA - 1);

        assertTrue(HatcheryCapacity.isBehind(SATURATED_HATCHERIES, SATURATED_HATCHERIES + 1, excess, false));
    }

    /**
     * No count below SATURATED_HATCHERIES is excess. Parity therefore always expands from one or
     * two bases.
     */
    @Test
    void parityStillExpandsFromOneAndTwoBases() {
        for (int ourTotal = 1; ourTotal < SATURATED_HATCHERIES; ourTotal++) {
            boolean excess = HatcheryCapacity.isExcess(ourTotal, IDLE_LARVA * 2);

            assertTrue(HatcheryCapacity.isBehind(ourTotal, ourTotal + 1, excess, false));
        }
    }

    @Test
    void weAreNotBehindAtParity() {
        assertFalse(HatcheryCapacity.isBehind(2, 2, false, false));
    }

    /**
     * Saturated hatcheries with idle larva stop the parity request and the floating-minerals
     * request alike, however large the mineral pile is.
     */
    @Test
    void floatingMineralsDoNotExpandAtSaturation() {
        boolean excess = HatcheryCapacity.isExcess(SATURATED_HATCHERIES, IDLE_LARVA);

        assertFalse(HatcheryCapacity.isBehind(SATURATED_HATCHERIES, SATURATED_HATCHERIES + 1, excess, false));
        assertFalse(HatcheryCapacity.isQueueable(excess, false));
    }

    @Test
    void theTwoRulesNeverDisagree() {
        for (int hatcheries = 0; hatcheries <= 6; hatcheries++) {
            for (int larva = 0; larva <= 12; larva++) {
                boolean excess = HatcheryCapacity.isExcess(hatcheries, larva);
                boolean behind = HatcheryCapacity.isBehind(hatcheries, hatcheries + 1, excess, false);

                assertFalse(excess && behind);
            }
        }
    }

    @Test
    void noParityRequestWhileTheEarlyRushReactionDeletesExpansions() {
        boolean excess = HatcheryCapacity.isExcess(SATURATED_HATCHERIES - 1, IDLE_LARVA - 1);

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
                    boolean excess = HatcheryCapacity.isExcess(hatcheries, larva);
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
            boolean excess = HatcheryCapacity.isExcess(completedHatcheries, IDLE_LARVA);
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
            boolean excess = HatcheryCapacity.isExcess(SATURATED_HATCHERIES - 1, IDLE_LARVA - 1);
            if (HatcheryCapacity.isBehind(SATURATED_HATCHERIES - 1, SATURATED_HATCHERIES, excess, true)) {
                enqueues++;
            }
            if (HatcheryCapacity.isFloatingExpansion(true, true)) {
                enqueues++;
            }
        }

        assertEquals(0, enqueues);
    }

    @Test
    void theQueueGateOpensWhenNoRuleDeletesAHatchery() {
        assertTrue(HatcheryCapacity.isQueueable(false, false));
    }

    @Test
    void theQueueGateClosesOnExcessHatcheries() {
        assertFalse(HatcheryCapacity.isQueueable(true, false));
    }

    @Test
    void theQueueGateClosesWhileAReactionDeletesHatcheries() {
        assertFalse(HatcheryCapacity.isQueueable(false, true));
    }

    /**
     * The gate and the excess sweep run in one frame, the gate before the sweep. A state that
     * opens the gate never also satisfies the sweep, whatever the matchup.
     */
    @Test
    void aStateThatOpensTheQueueGateNeverSatisfiesTheExcessSweep() {
        for (int hatcheries = 0; hatcheries <= 6; hatcheries++) {
            for (int larva = 0; larva <= 12; larva++) {
                for (int minerals = 0; minerals <= 2800; minerals += 50) {
                    boolean floating = HatcheryCapacity.isFloatingMinerals(minerals, hatcheries, true);
                    boolean excess = HatcheryCapacity.isExcess(hatcheries, larva);
                    boolean queueable = HatcheryCapacity.isQueueable(excess, false);

                    assertFalse(excess && queueable);
                    assertFalse(excess && HatcheryCapacity.isFloatingExpansion(floating, false) && queueable);
                }
            }
        }
    }

    @Test
    void mineralsAtTheBarAreNotFloating() {
        int bar = HatcheryCapacity.MINERALS_PER_HATCHERY * 4;

        assertFalse(HatcheryCapacity.isFloatingMinerals(bar, 3, true));
        assertTrue(HatcheryCapacity.isFloatingMinerals(bar + 1, 3, true));
    }

    @Test
    void eachCompletedHatcheryRaisesTheFloatingBar() {
        int oneHatcheryBar = HatcheryCapacity.MINERALS_PER_HATCHERY * 2;
        int twoHatcheryBar = HatcheryCapacity.MINERALS_PER_HATCHERY * 3;

        assertTrue(HatcheryCapacity.isFloatingMinerals(oneHatcheryBar + 1, 1, true));
        assertFalse(HatcheryCapacity.isFloatingMinerals(oneHatcheryBar + 1, 2, true));
        assertTrue(HatcheryCapacity.isFloatingMinerals(twoHatcheryBar + 1, 2, true));
    }

    @Test
    void mineralsAreNotFloatingBeforeTheWindowOpens() {
        assertFalse(HatcheryCapacity.isFloatingMinerals(5000, 1, false));
    }

    /**
     * The floating-minerals request holds for many frames and nothing it reads moves when it is
     * answered. The plan it produced leaves the queue on its enqueue frame and lands in the
     * building set, so the outstanding count is what holds the request until the hatchery is up.
     */
    @Test
    void aRequestThatHoldsForManyFramesProducesOnePlan() {
        int outstanding = 0;
        int lastEnqueueFrame = -HatcheryCapacity.ENQUEUE_COOLDOWN_FRAMES;
        int hatcheries = 1;
        int enqueues = 0;

        for (int frame = 0; frame < A_LONG_HOLD; frame++) {
            boolean floating = HatcheryCapacity.isFloatingMinerals(706, hatcheries, true);
            boolean excess = HatcheryCapacity.isExcess(hatcheries, 0);
            boolean rearmed = HatcheryCapacity.isEnqueueRearmed(outstanding, frame - lastEnqueueFrame);

            if (HatcheryCapacity.isFloatingExpansion(floating, false)
                    && rearmed
                    && HatcheryCapacity.isQueueable(excess, false)) {
                enqueues++;
                outstanding++;
                lastEnqueueFrame = frame;
            }
        }

        assertEquals(1, enqueues);
        assertEquals(1, outstanding);
    }

    @Test
    void anOutstandingHatcheryHoldsTheRequestHoweverLongTheCooldownHasRun() {
        assertFalse(HatcheryCapacity.isEnqueueRearmed(1, HatcheryCapacity.ENQUEUE_COOLDOWN_FRAMES * 10));
        assertFalse(HatcheryCapacity.isEnqueueRearmed(2, HatcheryCapacity.ENQUEUE_COOLDOWN_FRAMES * 10));
    }

    @Test
    void theCooldownHoldsTheRequestAfterAPlanLeavesTheSystem() {
        assertFalse(HatcheryCapacity.isEnqueueRearmed(0, 0));
        assertFalse(HatcheryCapacity.isEnqueueRearmed(0, HatcheryCapacity.ENQUEUE_COOLDOWN_FRAMES - 1));
        assertTrue(HatcheryCapacity.isEnqueueRearmed(0, HatcheryCapacity.ENQUEUE_COOLDOWN_FRAMES));
    }

    /**
     * A cancel does not touch the cooldown, so the request cannot answer itself on the frame
     * after a canceller took its plan away.
     */
    @Test
    void aCancelDoesNotRearmTheRequest() {
        int lastEnqueueFrame = 100;
        int cancelFrame = 105;

        assertFalse(HatcheryCapacity.isEnqueueRearmed(0, cancelFrame + 1 - lastEnqueueFrame));
    }
}
