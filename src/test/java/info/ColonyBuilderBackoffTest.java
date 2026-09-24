package info;

import bwapi.TilePosition;
import bwapi.UnitType;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.PlanCancelReason;
import macro.plan.PlanState;
import org.junit.jupiter.api.Test;
import telemetry.PlanEventSink;
import telemetry.PlanEvents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IA-406: the per-base hold on sunken planning after a lost Creep Colony builder.
 *
 * <p>bwem.Base cannot be built in a test, so the backoff is exercised over string bases; BaseData
 * and GameState hold the same class over Base.
 */
class ColonyBuilderBackoffTest {

    private static final String BASE_B = "B";

    private static final String BASE_C = "C";

    private static final int LOST_AT = 4421;

    private static final int NO_ENEMIES = 0;

    private final ColonyBuilderBackoff<String> backoff = new ColonyBuilderBackoff<>();

    private Set<String> open(int frame, int siteEnemies) {
        return backoff.openBases(new HashSet<>(Arrays.asList(BASE_B, BASE_C)), frame, base -> siteEnemies);
    }

    /** AC1: no pair at the base until the hold expires, and one after it. */
    @Test
    void aBaseThatLostABuilderIsHeldUntilItsHoldExpires() {
        int until = backoff.recordLoss(BASE_B, LOST_AT);

        assertEquals(LOST_AT + ColonyBuilderBackoff.COLONY_BUILDER_BACKOFF_FRAMES, until);
        assertFalse(open(LOST_AT + 1, NO_ENEMIES).contains(BASE_B));
        assertFalse(open(until - 1, NO_ENEMIES).contains(BASE_B));
        assertTrue(open(until, NO_ENEMIES).contains(BASE_B));
    }

    /** AC2: the hold grows by one step per loss at the same base, then stops at the cap. */
    @Test
    void theHoldGrowsWithRepeatedLossesAndIsCapped() {
        int frame = LOST_AT;
        for (int loss = 1; loss <= ColonyBuilderBackoff.MAX_COLONY_BACKOFF_STEPS + 2; loss++) {
            int until = backoff.recordLoss(BASE_B, frame);
            int steps = Math.min(loss, ColonyBuilderBackoff.MAX_COLONY_BACKOFF_STEPS);
            assertEquals(steps * ColonyBuilderBackoff.COLONY_BUILDER_BACKOFF_FRAMES, until - frame);
            assertEquals(loss, backoff.losses(BASE_B));
            frame = until;
        }
    }

    /** AC2: a colony starting to morph at the base resets the count, so the next loss is one step again. */
    @Test
    void aColonyStartingToMorphResetsTheHold() {
        backoff.recordLoss(BASE_B, LOST_AT);
        int held = backoff.recordLoss(BASE_B, LOST_AT);

        backoff.reset(BASE_B);

        assertFalse(backoff.isHeld(BASE_B, LOST_AT));
        assertFalse(backoff.hasLostBuilder(BASE_B));
        assertTrue(held - LOST_AT > ColonyBuilderBackoff.COLONY_BUILDER_BACKOFF_FRAMES);
        assertEquals(LOST_AT + ColonyBuilderBackoff.COLONY_BUILDER_BACKOFF_FRAMES, backoff.recordLoss(BASE_B, LOST_AT));
    }

    /** AC3: a loss at one base holds only that base. */
    @Test
    void aLossAtOneBaseDoesNotHoldAnother() {
        backoff.recordLoss(BASE_B, LOST_AT);

        assertEquals(Collections.singleton(BASE_C), open(LOST_AT + 1, NO_ENEMIES));
        assertFalse(backoff.hasLostBuilder(BASE_C));
    }

    /**
     * Once the hold lifts, a base that lost a builder is still refused while enemies stand at its
     * site, so no pair reserves minerals waiting on the dispatch gate. A base that lost none is
     * offered whatever stands there, the way the IA-381 carve-out sends its builder.
     */
    @Test
    void aBaseThatLostABuilderWaitsForItsSiteToClearOnceTheHoldLifts() {
        int until = backoff.recordLoss(BASE_B, LOST_AT);

        assertEquals(Collections.singleton(BASE_C), open(until, 6));
        assertTrue(open(until, NO_ENEMIES).contains(BASE_B));
    }

    @Test
    void anOpenBaseIsNeitherHeldNorContestedAfterALoss() {
        assertTrue(ColonyBuilderBackoff.isOpen(false, false, 9));
        assertTrue(ColonyBuilderBackoff.isOpen(false, true, 0));
        assertFalse(ColonyBuilderBackoff.isOpen(false, true, 1));
        assertFalse(ColonyBuilderBackoff.isOpen(true, false, 0));
        assertFalse(ColonyBuilderBackoff.isOpen(true, true, 0));
    }

    /** AC5: only a lost Creep Colony builder arms the backoff. */
    @Test
    void onlyALostCreepColonyBuilderArmsTheBackoff() {
        assertTrue(BaseData.shouldBackoffColony(UnitType.Zerg_Creep_Colony, PlanCancelReason.EXECUTOR_LOST));
        for (PlanCancelReason reason : PlanCancelReason.values()) {
            if (reason != PlanCancelReason.EXECUTOR_LOST) {
                assertFalse(BaseData.shouldBackoffColony(UnitType.Zerg_Creep_Colony, reason), reason.toString());
            }
        }
        assertFalse(BaseData.shouldBackoffColony(UnitType.Zerg_Sunken_Colony, PlanCancelReason.EXECUTOR_LOST));
        assertFalse(BaseData.shouldBackoffColony(UnitType.Zerg_Spore_Colony, PlanCancelReason.EXECUTOR_LOST));
        assertFalse(BaseData.shouldBackoffColony(UnitType.Zerg_Hatchery, PlanCancelReason.EXECUTOR_LOST));
    }

    @Test
    void anUnknownBaseArmsNothingAndLogsNothing() {
        List<int[]> rows = new ArrayList<>();
        PlanEvents.register(recorder(rows));
        try {
            new BaseData(new ArrayList<>()).backoffColony(null, LOST_AT);
        } finally {
            PlanEvents.clear();
        }
        assertTrue(rows.isEmpty());
    }

    private static PlanEventSink recorder(List<int[]> rows) {
        return new PlanEventSink() {
            @Override
            public void onEnqueue(Plan plan) {
            }

            @Override
            public void onStateChange(Plan plan, PlanState from, PlanState to) {
            }

            @Override
            public void onBlocked(Plan plan, PlanBlocker blocker) {
            }

            @Override
            public void onColonyBuilderBackoff(TilePosition base, int lostColonyBuilders, int colonyHeldUntilFrame) {
                rows.add(new int[] {lostColonyBuilders, colonyHeldUntilFrame});
            }
        };
    }
}
