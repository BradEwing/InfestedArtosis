package macro;

import bwapi.UnitType;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildAheadSlotTest {

    private static final int CLAIM_FRAME = 5806;

    private static final int AFFORDABLE_SOON = CLAIM_FRAME + 200;

    private Plan spire() {
        return new BuildingPlan(UnitType.Zerg_Spire, CLAIM_FRAME);
    }

    @Test
    void anIncomePredictionAtTheEndOfTimeIsUnreachable() {
        assertTrue(BuildAheadSlot.isUnreachable(Integer.MAX_VALUE));
    }

    @Test
    void anIncomePredictionWeCanGatherTowardsIsReachable() {
        assertFalse(BuildAheadSlot.isUnreachable(AFFORDABLE_SOON));
    }

    @Test
    void anUnreachablePredictionEarnsOnlyTheMinimumHold() {
        assertEquals(CLAIM_FRAME + BuildAheadSlot.MIN_HOLD_FRAMES,
                BuildAheadSlot.deadline(CLAIM_FRAME, Integer.MAX_VALUE));
    }

    @Test
    void theDeadlineFollowsTheIncomePredictionItWasClaimedOn() {
        int predicted = CLAIM_FRAME + BuildAheadSlot.MIN_HOLD_FRAMES * 2;

        assertEquals(predicted + BuildAheadSlot.PREDICTION_GRACE_FRAMES,
                BuildAheadSlot.deadline(CLAIM_FRAME, predicted));
    }

    @Test
    void theDeadlineNeverPrecedesTheMinimumHold() {
        int alreadyElapsed = CLAIM_FRAME - BuildAheadSlot.MAX_HOLD_FRAMES;

        assertEquals(CLAIM_FRAME + BuildAheadSlot.MIN_HOLD_FRAMES,
                BuildAheadSlot.deadline(CLAIM_FRAME, alreadyElapsed));
    }

    @Test
    void theDeadlineNeverOutlastsTheMaximumHold() {
        int predicted = CLAIM_FRAME + BuildAheadSlot.MAX_HOLD_FRAMES * 4;

        assertEquals(CLAIM_FRAME + BuildAheadSlot.MAX_HOLD_FRAMES,
                BuildAheadSlot.deadline(CLAIM_FRAME, predicted));
    }

    @Test
    void everyPredictionYieldsABoundedHold() {
        for (int predicted = 0; predicted < CLAIM_FRAME * 4; predicted += 97) {
            int deadline = BuildAheadSlot.deadline(CLAIM_FRAME, predicted);

            assertTrue(deadline >= CLAIM_FRAME + BuildAheadSlot.MIN_HOLD_FRAMES);
            assertTrue(deadline <= CLAIM_FRAME + BuildAheadSlot.MAX_HOLD_FRAMES);
        }
    }

    @Test
    void aClaimOccupiesTheSlot() {
        BuildAheadSlot slot = new BuildAheadSlot();

        slot.claim(spire(), CLAIM_FRAME, AFFORDABLE_SOON);

        assertTrue(slot.isOccupied());
        assertEquals(1, slot.occupancy());
    }

    @Test
    void aReleasedClaimFreesTheSlot() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan plan = spire();
        slot.claim(plan, CLAIM_FRAME, AFFORDABLE_SOON);

        slot.release(plan);

        assertFalse(slot.isOccupied());
    }

    @Test
    void aClaimIsNotStalledBeforeItsDeadline() {
        BuildAheadSlot slot = new BuildAheadSlot();
        slot.claim(spire(), CLAIM_FRAME, AFFORDABLE_SOON);

        int deadline = BuildAheadSlot.deadline(CLAIM_FRAME, AFFORDABLE_SOON);

        assertTrue(slot.stalled(deadline - 1).isEmpty());
    }

    @Test
    void aClaimIsStalledOnceItsDeadlinePasses() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan plan = spire();
        slot.claim(plan, CLAIM_FRAME, AFFORDABLE_SOON);

        List<Plan> stalled = slot.stalled(CLAIM_FRAME + 4277);

        assertEquals(1, stalled.size());
        assertSame(plan, stalled.get(0));
    }

    @Test
    void noClaimOutlivesTheMaximumHold() {
        BuildAheadSlot slot = new BuildAheadSlot();
        slot.claim(spire(), CLAIM_FRAME, CLAIM_FRAME + BuildAheadSlot.MAX_HOLD_FRAMES * 10);

        assertFalse(slot.stalled(CLAIM_FRAME + BuildAheadSlot.MAX_HOLD_FRAMES).isEmpty());
    }

    @Test
    void anAbnormalReleaseBarsTheBuildingFromTheSlot() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan plan = spire();
        slot.claim(plan, CLAIM_FRAME, AFFORDABLE_SOON);

        slot.releaseWithBackoff(plan, CLAIM_FRAME);

        assertFalse(slot.isOccupied());
        assertTrue(slot.isInBackoff(UnitType.Zerg_Spire, CLAIM_FRAME));
    }

    @Test
    void aRederivedPlanOfTheSameTypeIsStillInBackoff() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan plan = spire();
        slot.claim(plan, CLAIM_FRAME, AFFORDABLE_SOON);
        slot.releaseWithBackoff(plan, CLAIM_FRAME);

        assertTrue(slot.isInBackoff(spire().getPlannedUnit(), CLAIM_FRAME + 1));
    }

    @Test
    void theBackoffExpires() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan plan = spire();
        slot.claim(plan, CLAIM_FRAME, AFFORDABLE_SOON);
        slot.releaseWithBackoff(plan, CLAIM_FRAME);

        assertFalse(slot.isInBackoff(UnitType.Zerg_Spire, CLAIM_FRAME + BuildAheadSlot.BACKOFF_FRAMES));
    }

    @Test
    void anotherBuildingIsNotBarredByTheBackoff() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan plan = spire();
        slot.claim(plan, CLAIM_FRAME, AFFORDABLE_SOON);
        slot.releaseWithBackoff(plan, CLAIM_FRAME);

        assertFalse(slot.isInBackoff(UnitType.Zerg_Creep_Colony, CLAIM_FRAME));
    }

    @Test
    void aReleaseThatFreedNoClaimStartsNoBackoff() {
        BuildAheadSlot slot = new BuildAheadSlot();

        slot.releaseWithBackoff(spire(), CLAIM_FRAME);

        assertFalse(slot.isInBackoff(UnitType.Zerg_Spire, CLAIM_FRAME));
    }

    @Test
    void reconcileDropsAClaimWhosePlanLeftThePipeline() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan plan = spire();
        slot.claim(plan, CLAIM_FRAME, AFFORDABLE_SOON);

        slot.reconcile(Collections.emptyList());

        assertFalse(slot.isOccupied());
    }

    @Test
    void reconcileKeepsAClaimWhosePlanIsStillActive() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan plan = spire();
        Plan colony = new BuildingPlan(UnitType.Zerg_Creep_Colony, CLAIM_FRAME);
        slot.claim(plan, CLAIM_FRAME, AFFORDABLE_SOON);

        slot.reconcile(Arrays.asList(colony, plan));

        assertTrue(slot.isOccupied());
    }

    @Test
    void releaseFirstFreesOneClaimOfTheBuildingType() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan first = new BuildingPlan(UnitType.Zerg_Extractor, CLAIM_FRAME);
        Plan second = new BuildingPlan(UnitType.Zerg_Extractor, CLAIM_FRAME + 1);
        slot.claim(first, CLAIM_FRAME, AFFORDABLE_SOON);
        slot.claim(second, CLAIM_FRAME + 1, AFFORDABLE_SOON);

        slot.releaseFirst(UnitType.Zerg_Extractor);

        assertEquals(1, slot.occupancy());
        assertEquals(0, slot.heldFrames(first, CLAIM_FRAME + 100));
    }

    @Test
    void releaseFirstIgnoresOtherBuildingTypes() {
        BuildAheadSlot slot = new BuildAheadSlot();
        slot.claim(spire(), CLAIM_FRAME, AFFORDABLE_SOON);

        slot.releaseFirst(UnitType.Zerg_Extractor);

        assertEquals(1, slot.occupancy());
    }

    @Test
    void heldFramesMeasuresTheClaimAge() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan plan = spire();
        slot.claim(plan, CLAIM_FRAME, AFFORDABLE_SOON);

        assertEquals(4277, slot.heldFrames(plan, CLAIM_FRAME + 4277));
    }

    @Test
    void aHoldIsReportedOnceAnIntervalHasPassed() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan plan = spire();
        slot.claim(plan, CLAIM_FRAME, AFFORDABLE_SOON);

        assertTrue(slot.holdReportsDue(CLAIM_FRAME + BuildAheadSlot.HOLD_REPORT_INTERVAL_FRAMES - 1).isEmpty());
        assertEquals(1, slot.holdReportsDue(CLAIM_FRAME + BuildAheadSlot.HOLD_REPORT_INTERVAL_FRAMES).size());
    }

    @Test
    void aHoldIsNotReportedTwiceInOneInterval() {
        BuildAheadSlot slot = new BuildAheadSlot();
        Plan plan = spire();
        slot.claim(plan, CLAIM_FRAME, AFFORDABLE_SOON);
        int reportFrame = CLAIM_FRAME + BuildAheadSlot.HOLD_REPORT_INTERVAL_FRAMES;
        slot.holdReportsDue(reportFrame);

        assertTrue(slot.holdReportsDue(reportFrame + 1).isEmpty());
    }

    @Test
    void anEvictedPlanReentersTheQueueDeprioritised() {
        assertTrue(BuildAheadSlot.requeuePriority(CLAIM_FRAME) > CLAIM_FRAME);
    }

    @Test
    void deprioritisingAPlanNeverOverflows() {
        assertEquals(Integer.MAX_VALUE, BuildAheadSlot.requeuePriority(Integer.MAX_VALUE));
    }
}
