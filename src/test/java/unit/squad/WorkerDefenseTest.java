package unit.squad;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerDefenseTest {

    private static final List<String> MAIN_LINE = Arrays.asList("m1", "m2", "m3", "m4", "m5", "m6");
    private static final List<String> NO_GATHERERS = Collections.emptyList();
    private static final Predicate<String> ANY = gatherer -> true;

    private static Predicate<List<String>> winsWithAtLeast(int defenders, List<List<String>> evaluated) {
        return group -> {
            evaluated.add(new ArrayList<>(group));
            return group.size() >= defenders;
        };
    }

    @Test
    void aDefenceThatLosesWithEveryCandidatePullsNoDrones() {
        List<List<String>> evaluated = new ArrayList<>();
        Predicate<List<String>> neverWins = winsWithAtLeast(Integer.MAX_VALUE, evaluated);

        WorkerDefense.Outcome<String> outcome = WorkerDefense.decide(NO_GATHERERS, MAIN_LINE, neverWins, neverWins);

        assertTrue(outcome.isAbandoned());
        assertTrue(outcome.getPulled().isEmpty());
        assertTrue(outcome.getReleased().isEmpty());
        assertEquals(1, evaluated.size());
        assertEquals(MAIN_LINE, evaluated.get(0));
    }

    @Test
    void aWinnableDefencePullsOnlyUntilTheThreatIsCleared() {
        Predicate<List<String>> winsWithThree = winsWithAtLeast(3, new ArrayList<>());

        WorkerDefense.Outcome<String> outcome = WorkerDefense.decide(NO_GATHERERS, MAIN_LINE, winsWithThree,
                winsWithThree);

        assertFalse(outcome.isAbandoned());
        assertEquals(Arrays.asList("m1", "m2", "m3"), outcome.getPulled());
    }

    @Test
    void aStricterClearThresholdPullsMoreWithoutAbandoning() {
        WorkerDefense.Outcome<String> outcome = WorkerDefense.decide(NO_GATHERERS, MAIN_LINE,
                winsWithAtLeast(3, new ArrayList<>()), winsWithAtLeast(5, new ArrayList<>()));

        assertFalse(outcome.isAbandoned());
        assertEquals(5, outcome.getPulled().size());
    }

    @Test
    void assignedDefendersAreReleasedWhenTheReevaluatedDefenceLoses() {
        List<String> defenders = Arrays.asList("d1", "d2", "d3");
        List<List<String>> evaluated = new ArrayList<>();
        Predicate<List<String>> neverWins = winsWithAtLeast(Integer.MAX_VALUE, evaluated);

        WorkerDefense.Outcome<String> outcome = WorkerDefense.decide(defenders, NO_GATHERERS, neverWins, neverWins);

        assertTrue(outcome.isAbandoned());
        assertEquals(defenders, outcome.getReleased());
        assertTrue(outcome.getPulled().isEmpty());
        assertEquals(defenders, evaluated.get(0));
    }

    @Test
    void assignedDefendersStayWhileTheReevaluatedDefenceWins() {
        List<String> defenders = Arrays.asList("d1", "d2", "d3");
        Predicate<List<String>> winsWithThree = winsWithAtLeast(3, new ArrayList<>());

        WorkerDefense.Outcome<String> outcome = WorkerDefense.decide(defenders, MAIN_LINE, winsWithThree,
                winsWithThree);

        assertFalse(outcome.isAbandoned());
        assertTrue(outcome.getReleased().isEmpty());
        assertTrue(outcome.getPulled().isEmpty());
    }

    @Test
    void theReevaluationCountsCandidatesTowardsTheFullCommitment() {
        List<String> defenders = Arrays.asList("d1", "d2");
        Predicate<List<String>> winsWithFour = winsWithAtLeast(4, new ArrayList<>());

        WorkerDefense.Outcome<String> outcome = WorkerDefense.decide(defenders, MAIN_LINE, winsWithFour,
                winsWithFour);

        assertFalse(outcome.isAbandoned());
        assertEquals(Arrays.asList("m1", "m2"), outcome.getPulled());
    }

    @Test
    void nothingToDecideIsNotAnAbandon() {
        WorkerDefense.Outcome<String> outcome = WorkerDefense.decide(NO_GATHERERS, NO_GATHERERS,
                group -> false, group -> false);

        assertFalse(outcome.isAbandoned());
        assertTrue(outcome.getPulled().isEmpty());
    }

    @Test
    void gatherersAtAnotherBaseAreNotPulledIntoACombatUnitThreat() {
        assertTrue(WorkerDefense.candidates(NO_GATHERERS, MAIN_LINE, true, false, ANY).isEmpty());
    }

    @Test
    void gatherersAtAnotherBaseArePulledNoFurtherThanTheCap() {
        List<String> candidates = WorkerDefense.candidates(NO_GATHERERS, MAIN_LINE, false, false, ANY);

        assertEquals(WorkerDefense.CROSS_BASE_DEFENDER_CAP, candidates.size());
        assertEquals(MAIN_LINE.subList(0, WorkerDefense.CROSS_BASE_DEFENDER_CAP), candidates);
    }

    @Test
    void theCrossBaseCapHoldsEvenWhenEveryCandidateWouldBeNeeded() {
        List<String> candidates = WorkerDefense.candidates(NO_GATHERERS, MAIN_LINE, false, false, ANY);
        WorkerDefense.Outcome<String> outcome = WorkerDefense.decide(NO_GATHERERS, candidates,
                group -> true, group -> false);

        assertEquals(WorkerDefense.CROSS_BASE_DEFENDER_CAP, outcome.getPulled().size());
    }

    @Test
    void aCannonRushLiftsTheCrossBaseCap() {
        assertEquals(MAIN_LINE, WorkerDefense.candidates(NO_GATHERERS, MAIN_LINE, true, true, ANY));
    }

    @Test
    void aBaseDefendsWithItsOwnGatherersFirst() {
        List<String> own = Arrays.asList("n1", "n2", "n3", "n4");

        assertEquals(own, WorkerDefense.candidates(own, MAIN_LINE, true, false, ANY));
    }

    @Test
    void aSourceBaseBelowTheMinimumGivesNoGatherers() {
        assertTrue(WorkerDefense.candidates(Arrays.asList("n1", "n2"), MAIN_LINE, false, false, ANY).isEmpty());
        assertTrue(WorkerDefense.candidates(NO_GATHERERS, Arrays.asList("m1", "m2"), false, true, ANY).isEmpty());
    }

    @Test
    void aHeldBackGathererStillCountsTowardTheMinimum() {
        List<String> own = Arrays.asList("builder", "n2", "n3");

        assertEquals(Arrays.asList("n2", "n3"),
                WorkerDefense.candidates(own, MAIN_LINE, false, false, gatherer -> !gatherer.equals("builder")));
    }

    @Test
    void aHeldBackGathererIsNeverOffered() {
        List<String> own = Arrays.asList("n1", "builder", "n3", "n4");

        assertFalse(WorkerDefense.candidates(own, MAIN_LINE, false, false, gatherer -> !gatherer.equals("builder"))
                .contains("builder"));
    }

    @Test
    void theCrossBaseCapFillsFromPullableGatherers() {
        List<String> candidates = WorkerDefense.candidates(NO_GATHERERS, MAIN_LINE, false, false,
                gatherer -> !gatherer.equals("m1"));

        assertEquals(Arrays.asList("m2", "m3"), candidates);
    }

    @Test
    void aDefenceWinsWhenEveryEnemyDiesOrEnoughDefendersSurvive() {
        assertTrue(WorkerDefense.defenceWins(0, 0, 0, 0.5));
        assertTrue(WorkerDefense.defenceWins(6, 1, 0, 0.5));
        assertTrue(WorkerDefense.defenceWins(6, 3, 2, 0.5));
        assertFalse(WorkerDefense.defenceWins(6, 2, 2, 0.5));
        assertFalse(WorkerDefense.defenceWins(6, 0, 2, 0.5));
        assertFalse(WorkerDefense.defenceWins(0, 0, 2, 0.5));
    }

    @Test
    void anUnsimulatedDefenceNeverWins() {
        assertFalse(DefenseSim.unsimulated(6, 0.5).wins());
    }

    @Test
    void anAbandonedBaseWaitsOutItsHold() {
        int abandonFrame = 6242;
        Integer holdUntil = abandonFrame + WorkerDefense.ABANDON_HOLD_FRAMES;

        assertTrue(WorkerDefense.abandonHeld(holdUntil, abandonFrame + 1));
        assertFalse(WorkerDefense.abandonHeld(holdUntil, holdUntil));
        assertFalse(WorkerDefense.abandonHeld(null, abandonFrame));
    }
}
