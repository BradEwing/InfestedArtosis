package unit.squad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.ContainmentEscalation.ENTRY_HOLD_FRAMES;
import static unit.squad.ContainmentEscalation.ESCALATE_AFTER_REENTRIES;

class ContainmentEscalationTest {

    private static final boolean STATIC_ONLY = true;
    private static final boolean ARMY_OUTSIDE = false;
    private static final int FIRST_TIMEOUT = 15852;
    private static final int TIMEOUT_INTERVAL = 1401;

    private static int timeoutFrame(int index) {
        return FIRST_TIMEOUT + index * TIMEOUT_INTERVAL;
    }

    private static ContainmentEscalation afterReentries(int reentries, boolean staticOnly) {
        ContainmentEscalation escalation = new ContainmentEscalation();
        escalation.onEntered();
        for (int i = 0; i < reentries; i++) {
            assertFalse(escalation.onTimedOut(staticOnly, timeoutFrame(i)));
            escalation.onEntered();
        }
        return escalation;
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
        assertFalse(escalation.onTimedOut(ARMY_OUTSIDE, timeoutFrame(ESCALATE_AFTER_REENTRIES + 2)));
        escalation.onEntered();
        assertTrue(escalation.onTimedOut(STATIC_ONLY, timeoutFrame(ESCALATE_AFTER_REENTRIES + 3)));
    }

    @Test
    void anEntryWithNoTimeoutBeforeItIsNotAReentry() {
        ContainmentEscalation escalation = new ContainmentEscalation();
        escalation.onEntered();
        escalation.onEntered();
        assertEquals(0, escalation.getReentries());
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
        escalation.onEntered();
        assertEquals(0, escalation.getReentries());
    }

    @Test
    void escalatesNeedsBothTheReentryLimitAndAStaticOnlyDefence() {
        assertTrue(ContainmentEscalation.escalates(ESCALATE_AFTER_REENTRIES, STATIC_ONLY));
        assertFalse(ContainmentEscalation.escalates(ESCALATE_AFTER_REENTRIES - 1, STATIC_ONLY));
        assertFalse(ContainmentEscalation.escalates(ESCALATE_AFTER_REENTRIES, ARMY_OUTSIDE));
    }
}
