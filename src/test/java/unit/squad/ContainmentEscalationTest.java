package unit.squad;

import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.ContainmentEscalation.ENTRY_HOLD_FRAMES;
import static unit.squad.ContainmentEscalation.ESCALATE_AFTER_REENTRIES;
import static unit.squad.ContainmentEscalation.REENTRY_WINDOW_FRAMES;
import static unit.squad.SquadManager.ContainmentVerdict;
import static unit.squad.SquadManager.escalatedVerdict;

class ContainmentEscalationTest {

    private static final boolean STATIC_ONLY = true;
    private static final boolean ARMY_OUTSIDE = false;
    private static final boolean TIMED_OUT = true;
    private static final boolean IN_TIME = false;
    private static final int FIRST_ENTRY = 14452;
    private static final int TIMEOUT_INTERVAL = 1401;

    private static int timeoutFrame(int index) {
        return FIRST_ENTRY + (index + 1) * TIMEOUT_INTERVAL;
    }

    private static ContainmentEscalation afterReentries(int reentries, boolean staticOnly) {
        ContainmentEscalation escalation = new ContainmentEscalation();
        escalation.onEntered(FIRST_ENTRY);
        for (int i = 0; i < reentries; i++) {
            assertFalse(escalation.onTimedOut(staticOnly, timeoutFrame(i)));
            escalation.onEntered(timeoutFrame(i) + 1);
        }
        return escalation;
    }

    private static BooleanSupplier reads(boolean staticOnly, int[] calls) {
        return () -> {
            calls[0]++;
            return staticOnly;
        };
    }

    @Test
    void aStaticOnlyDefenceEscalatesOnTheTimeoutAfterTheSecondReentry() {
        ContainmentEscalation escalation = afterReentries(ESCALATE_AFTER_REENTRIES, STATIC_ONLY);
        assertEquals(ESCALATE_AFTER_REENTRIES, escalation.getReentries());
        assertTrue(escalation.onTimedOut(STATIC_ONLY, timeoutFrame(ESCALATE_AFTER_REENTRIES)));
    }

    @Test
    void aStaticOnlyDefenceDoesNotEscalateBeforeTheReentryLimit() {
        ContainmentEscalation escalation = afterReentries(ESCALATE_AFTER_REENTRIES - 1, STATIC_ONLY);
        assertFalse(escalation.onTimedOut(STATIC_ONLY, timeoutFrame(ESCALATE_AFTER_REENTRIES - 1)));
    }

    @Test
    void anArmyOutsideTheStaticDefenceNeverEscalatesButTheRunKeepsCounting() {
        ContainmentEscalation escalation = afterReentries(ESCALATE_AFTER_REENTRIES + 2, ARMY_OUTSIDE);
        int timeout = timeoutFrame(ESCALATE_AFTER_REENTRIES + 2);
        assertFalse(escalation.onTimedOut(ARMY_OUTSIDE, timeout));
        escalation.onEntered(timeout + 1);
        assertTrue(escalation.onTimedOut(STATIC_ONLY, timeout + TIMEOUT_INTERVAL));
    }

    @Test
    void anEntryWithNoTimeoutBeforeItIsNotAReentry() {
        ContainmentEscalation escalation = new ContainmentEscalation();
        escalation.onEntered(FIRST_ENTRY);
        escalation.onEntered(FIRST_ENTRY + 1);
        assertEquals(0, escalation.getReentries());
    }

    @Test
    void anEntryInsideTheWindowAfterATimeoutIsAReentryAndOneAfterItStartsANewRun() {
        ContainmentEscalation escalation = afterReentries(1, STATIC_ONLY);
        int timeout = timeoutFrame(1);
        assertFalse(escalation.onTimedOut(STATIC_ONLY, timeout));
        escalation.onEntered(timeout + REENTRY_WINDOW_FRAMES);
        assertEquals(2, escalation.getReentries());

        ContainmentEscalation late = afterReentries(1, STATIC_ONLY);
        assertFalse(late.onTimedOut(STATIC_ONLY, timeout));
        late.onEntered(timeout + REENTRY_WINDOW_FRAMES + 1);
        assertEquals(0, late.getReentries());
        assertFalse(late.onTimedOut(STATIC_ONLY, timeout + TIMEOUT_INTERVAL));
    }

    @Test
    void anyOtherEndToTheContainClearsTheRun() {
        ContainmentEscalation escalation = afterReentries(ESCALATE_AFTER_REENTRIES, STATIC_ONLY);
        escalation.onEndedOtherwise();
        assertEquals(0, escalation.getReentries());
        assertFalse(escalation.onTimedOut(STATIC_ONLY, timeoutFrame(ESCALATE_AFTER_REENTRIES)));
    }

    @Test
    void anEscalationBarsEntryForTheHoldWindowAndStartsANewRun() {
        ContainmentEscalation escalation = afterReentries(ESCALATE_AFTER_REENTRIES, STATIC_ONLY);
        int escalated = timeoutFrame(ESCALATE_AFTER_REENTRIES);
        assertFalse(escalation.holdsEntry(escalated - 1));
        assertTrue(escalation.onTimedOut(STATIC_ONLY, escalated));
        assertTrue(escalation.holdsEntry(escalated));
        assertTrue(escalation.holdsEntry(escalated + ENTRY_HOLD_FRAMES - 1));
        assertFalse(escalation.holdsEntry(escalated + ENTRY_HOLD_FRAMES));
        assertEquals(0, escalation.getReentries());
        escalation.onEntered(escalated + 1);
        assertEquals(0, escalation.getReentries());
    }

    @Test
    void escalatesNeedsBothTheReentryLimitAndAStaticOnlyDefence() {
        assertTrue(ContainmentEscalation.escalates(ESCALATE_AFTER_REENTRIES, STATIC_ONLY));
        assertFalse(ContainmentEscalation.escalates(ESCALATE_AFTER_REENTRIES - 1, STATIC_ONLY));
        assertFalse(ContainmentEscalation.escalates(ESCALATE_AFTER_REENTRIES, ARMY_OUTSIDE));
    }

    @Test
    void theThirdTimeoutRetreatAgainstAStaticOnlyDefenceBecomesAnEscalation() {
        ContainmentEscalation escalation = new ContainmentEscalation();
        escalation.onEntered(FIRST_ENTRY);
        int[] calls = {0};
        for (int i = 0; i < ESCALATE_AFTER_REENTRIES; i++) {
            assertEquals(ContainmentVerdict.RETREAT, escalatedVerdict(ContainmentVerdict.RETREAT, TIMED_OUT,
                    escalation, reads(STATIC_ONLY, calls), timeoutFrame(i)));
            escalation.onEntered(timeoutFrame(i) + 1);
        }
        assertEquals(ContainmentVerdict.ESCALATE, escalatedVerdict(ContainmentVerdict.RETREAT, TIMED_OUT,
                escalation, reads(STATIC_ONLY, calls), timeoutFrame(ESCALATE_AFTER_REENTRIES)));
        assertEquals(ESCALATE_AFTER_REENTRIES + 1, calls[0]);
    }

    @Test
    void onlyATimeoutRetreatIsRecordedOrReadsTheStaticOnlyTest() {
        ContainmentEscalation escalation = afterReentries(ESCALATE_AFTER_REENTRIES, STATIC_ONLY);
        int frame = timeoutFrame(ESCALATE_AFTER_REENTRIES);
        int[] calls = {0};
        assertEquals(ContainmentVerdict.RETREAT, escalatedVerdict(ContainmentVerdict.RETREAT, IN_TIME,
                escalation, reads(STATIC_ONLY, calls), frame));
        assertEquals(ContainmentVerdict.BREAK_ALL, escalatedVerdict(ContainmentVerdict.BREAK_ALL, TIMED_OUT,
                escalation, reads(STATIC_ONLY, calls), frame));
        assertEquals(ContainmentVerdict.HOLD, escalatedVerdict(ContainmentVerdict.HOLD, TIMED_OUT,
                escalation, reads(STATIC_ONLY, calls), frame));
        assertEquals(0, calls[0]);
        assertEquals(ESCALATE_AFTER_REENTRIES, escalation.getReentries());
        assertFalse(escalation.holdsEntry(frame));
    }

    @Test
    void aTimeoutRetreatWithArmyOutsideStaysARetreat() {
        ContainmentEscalation escalation = afterReentries(ESCALATE_AFTER_REENTRIES, ARMY_OUTSIDE);
        int[] calls = {0};
        assertEquals(ContainmentVerdict.RETREAT, escalatedVerdict(ContainmentVerdict.RETREAT, TIMED_OUT,
                escalation, reads(ARMY_OUTSIDE, calls), timeoutFrame(ESCALATE_AFTER_REENTRIES)));
        assertEquals(1, calls[0]);
    }
}
