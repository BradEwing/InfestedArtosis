package macro;

import bwapi.TilePosition;
import bwapi.UnitType;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.PlanState;
import macro.plan.UnitPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractorTrickTest {

    private static final int ARMED_AT = 2000;

    private static final int PRIORITY = 1;

    private static final TilePosition GEYSER = new TilePosition(10, 20);

    private static Plan extractorPlan() {
        return new BuildingPlan(UnitType.Zerg_Extractor, PRIORITY, GEYSER);
    }

    private static Plan dronePlan() {
        return new UnitPlan(UnitType.Zerg_Drone, PRIORITY);
    }

    @Test
    void anArmedTrickStartsOnceItsExtractorStands() {
        assertEquals(ExtractorTrick.Step.WAIT,
                ExtractorTrick.step(ExtractorTrick.Phase.ARMED, 0, 0, false, PlanState.PLANNED));
        assertEquals(ExtractorTrick.Step.START,
                ExtractorTrick.step(ExtractorTrick.Phase.ARMED, 1, 0, true, PlanState.PLANNED));
    }

    @Test
    void anArmedTrickWhoseExtractorNeverStartsIsReleased() {
        assertEquals(ExtractorTrick.Step.WAIT, ExtractorTrick.step(ExtractorTrick.Phase.ARMED,
                ExtractorTrick.START_TIMEOUT_FRAMES - 1, 0, false, PlanState.PLANNED));
        assertEquals(ExtractorTrick.Step.RELEASE, ExtractorTrick.step(ExtractorTrick.Phase.ARMED,
                ExtractorTrick.START_TIMEOUT_FRAMES, 0, false, PlanState.PLANNED));
    }

    @Test
    void theExtractorIsCancelledOnlyOnceTheDroneIsInItsEgg() {
        for (PlanState waiting : new PlanState[]{PlanState.PLANNED, PlanState.SCHEDULE, PlanState.BUILDING}) {
            assertEquals(ExtractorTrick.Step.WAIT,
                    ExtractorTrick.step(ExtractorTrick.Phase.STARTED, 0, 1, true, waiting), waiting.toString());
        }
        for (PlanState released : new PlanState[]{PlanState.MORPHING, PlanState.COMPLETE, PlanState.CANCELLED}) {
            assertEquals(ExtractorTrick.Step.CANCEL,
                    ExtractorTrick.step(ExtractorTrick.Phase.STARTED, 0, 1, true, released), released.toString());
        }
    }

    @Test
    void aStartedExtractorIsCancelledAtTheDeadlineWhateverTheDroneIsDoing() {
        assertEquals(ExtractorTrick.Step.WAIT, ExtractorTrick.step(ExtractorTrick.Phase.STARTED, 0,
                ExtractorTrick.CANCEL_DEADLINE_FRAMES - 1, true, PlanState.PLANNED));
        assertEquals(ExtractorTrick.Step.CANCEL, ExtractorTrick.step(ExtractorTrick.Phase.STARTED, 0,
                ExtractorTrick.CANCEL_DEADLINE_FRAMES, true, PlanState.PLANNED));
    }

    @Test
    void theCancelDeadlineFallsBeforeTheExtractorFinishes() {
        assertTrue(ExtractorTrick.CANCEL_DEADLINE_FRAMES > 0);
        assertTrue(ExtractorTrick.CANCEL_DEADLINE_FRAMES < UnitType.Zerg_Extractor.buildTime());
    }

    @Test
    void aStartedTrickWhoseExtractorIsGoneIsReleased() {
        assertEquals(ExtractorTrick.Step.RELEASE,
                ExtractorTrick.step(ExtractorTrick.Phase.STARTED, 0, 1, false, PlanState.PLANNED));
    }

    @Test
    void idleAndSettledTricksDoNothing() {
        assertEquals(ExtractorTrick.Step.WAIT,
                ExtractorTrick.step(ExtractorTrick.Phase.IDLE, 0, 0, true, PlanState.MORPHING));
        assertEquals(ExtractorTrick.Step.WAIT,
                ExtractorTrick.step(ExtractorTrick.Phase.DONE, 0, 0, true, PlanState.MORPHING));
    }

    @Test
    void runsExtractorStartedThenDroneMorphThenCancel() {
        ExtractorTrick trick = new ExtractorTrick();
        Plan drone = dronePlan();
        trick.arm(extractorPlan(), drone, ARMED_AT);

        assertTrue(trick.isRunning());
        assertEquals(GEYSER, trick.getGeyser());
        assertEquals(ExtractorTrick.Step.WAIT, trick.update(ARMED_AT + 1, false));
        assertEquals(ExtractorTrick.Step.START, trick.update(ARMED_AT + 2, true));
        assertEquals(ExtractorTrick.Phase.STARTED, trick.getPhase());

        drone.setState(PlanState.BUILDING);
        assertEquals(ExtractorTrick.Step.WAIT, trick.update(ARMED_AT + 3, true));

        drone.setState(PlanState.MORPHING);
        assertEquals(ExtractorTrick.Step.CANCEL, trick.update(ARMED_AT + 4, true));
        assertFalse(trick.isSettled());
        assertEquals(ExtractorTrick.Step.CANCEL, trick.update(ARMED_AT + 5, true));

        trick.cancelled();
        assertTrue(trick.isSettled());
        assertFalse(trick.isRunning());
    }

    @Test
    void theDeadlineCountsFromTheFrameTheExtractorStarted() {
        ExtractorTrick trick = new ExtractorTrick();
        trick.arm(extractorPlan(), dronePlan(), ARMED_AT);
        int startedAt = ARMED_AT + ExtractorTrick.START_TIMEOUT_FRAMES - 1;

        assertEquals(ExtractorTrick.Step.START, trick.update(startedAt, true));
        assertEquals(ExtractorTrick.Step.WAIT, trick.update(startedAt + 1, true));
        assertEquals(ExtractorTrick.Step.CANCEL,
                trick.update(startedAt + ExtractorTrick.CANCEL_DEADLINE_FRAMES, true));
    }

    @Test
    void aReleasedTrickIsSettledAndNeverCancels() {
        ExtractorTrick trick = new ExtractorTrick();
        trick.arm(extractorPlan(), dronePlan(), ARMED_AT);

        assertEquals(ExtractorTrick.Step.RELEASE,
                trick.update(ARMED_AT + ExtractorTrick.START_TIMEOUT_FRAMES, false));
        assertTrue(trick.isSettled());
        assertEquals(ExtractorTrick.Step.WAIT,
                trick.update(ARMED_AT + ExtractorTrick.START_TIMEOUT_FRAMES + 1, true));
    }

    @Test
    void runsAtMostOnce() {
        ExtractorTrick trick = new ExtractorTrick();
        trick.skip();
        assertTrue(trick.isSettled());

        trick.arm(extractorPlan(), dronePlan(), ARMED_AT);
        assertTrue(trick.isSettled());
        assertFalse(trick.isRunning());
    }

    @Test
    void skipIsIgnoredOnceArmed() {
        ExtractorTrick trick = new ExtractorTrick();
        trick.arm(extractorPlan(), dronePlan(), ARMED_AT);
        trick.skip();

        assertTrue(trick.isRunning());
    }
}
