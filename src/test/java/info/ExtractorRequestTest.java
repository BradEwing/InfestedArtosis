package info;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the gate on requesting another Extractor.
 *
 * <p>GameState needs a bwapi.Game, so the gate is exercised through its static predicate, and the
 * replan hold through the BaseData field the predicate reads.
 */
class ExtractorRequestTest {

    private static final int NO_HOLD = 0;

    private static final int FRAME = 5000;

    @Test
    void theFirstGeyserIsClaimedWithNoGasIncomeToShowForIt() {
        assertTrue(GameState.shouldRequestExtractor(0, 0, 0, FRAME, NO_HOLD));
    }

    /**
     * IA-338: the old rule asked for another geyser whenever the mineral bank led the gas bank by
     * 100, which is close to permanently true and says nothing about the extractor already owned.
     */
    @Test
    void anExtractorStandingEmptyDoesNotEarnASecondOne() {
        assertFalse(GameState.shouldRequestExtractor(1, 1, 0, FRAME, NO_HOLD));
        assertTrue(GameState.shouldRequestExtractor(1, 1, 1, FRAME, NO_HOLD));
    }

    /**
     * A plan holds its geyser reservation from the frame it is created, so a request that ignored
     * whether the claim had finished would stack a second plan behind the first on the next frame.
     * Game L4KVD0CN queued two on consecutive frames that way.
     */
    @Test
    void aClaimStillMorphingBlocksTheNextRequest() {
        assertFalse(GameState.shouldRequestExtractor(1, 0, 0, FRAME, NO_HOLD));
        assertFalse(GameState.shouldRequestExtractor(2, 1, 3, FRAME, NO_HOLD));
    }

    @Test
    void everyStandingExtractorHasToBeWorkedBeforeTheNextIsClaimed() {
        assertFalse(GameState.shouldRequestExtractor(2, 2, 1, FRAME, NO_HOLD));
        assertTrue(GameState.shouldRequestExtractor(2, 2, 2, FRAME, NO_HOLD));
    }

    /**
     * IA-338, game L4KVD0CN: the gas-deny reaction cancelled the morph and handed the geyser back,
     * which re-opened the first-extractor branch and put the request in again 198 frames later.
     * Four morphs reached the same geyser that way, so the hold has to outrank that branch.
     */
    @Test
    void theHoldAfterACancellationOutranksTheFirstExtractorBranch() {
        assertFalse(GameState.shouldRequestExtractor(0, 0, 0, FRAME, FRAME + 1));
        assertTrue(GameState.shouldRequestExtractor(0, 0, 0, FRAME + 1, FRAME + 1));
    }

    @Test
    void aCancellationArmsTheHold() {
        BaseData baseData = new BaseData(new ArrayList<>());
        assertTrue(GameState.shouldRequestExtractor(0, 0, 0, FRAME, baseData.getExtractorReplanBackoffUntil()));

        baseData.backoffExtractor(FRAME);

        assertFalse(GameState.shouldRequestExtractor(0, 0, 0, FRAME, baseData.getExtractorReplanBackoffUntil()));
    }

    @Test
    void theHoldRunsForTheFullBackoff() {
        BaseData baseData = new BaseData(new ArrayList<>());
        baseData.backoffExtractor(FRAME);
        int heldUntil = baseData.getExtractorReplanBackoffUntil();

        int lastHeldFrame = FRAME + BaseData.EXTRACTOR_REPLAN_BACKOFF_FRAMES - 1;
        assertFalse(GameState.shouldRequestExtractor(0, 0, 0, lastHeldFrame, heldUntil));
        assertTrue(GameState.shouldRequestExtractor(0, 0, 0, lastHeldFrame + 1, heldUntil));
    }
}
