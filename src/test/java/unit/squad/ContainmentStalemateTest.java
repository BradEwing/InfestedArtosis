package unit.squad;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.ContainmentStalemate.HOLD_FRAMES;
import static unit.squad.ContainmentStalemate.STALEMATE_AFTER_REENTRIES;
import static unit.squad.SquadManager.ContainmentVerdict;
import static unit.squad.SquadManager.escalatedVerdict;
import static unit.squad.SquadManager.stalemateVerdict;

class ContainmentStalemateTest {

    private static final boolean STATIC_ONLY = true;
    private static final boolean ARMY_OUTSIDE = false;
    private static final boolean UNREACHABLE = true;
    private static final boolean REACHABLE = false;
    private static final boolean ON_TIMEOUT = true;
    private static final int FIRST_ENTRY = 32287;
    private static final int RETREAT_LOCK = 120;
    private static final int CONTAIN_TIMEOUT = SquadManager.CONTAINMENT_TIMEOUT_FRAMES;
    private static final int CANNON_SUPPLY = 6;

    private static int supply(UnitType type, int count) {
        return type.supplyRequired() * count;
    }

    private static int cyclesTimeout(int index) {
        return FIRST_ENTRY + index * (CONTAIN_TIMEOUT + RETREAT_LOCK) + CONTAIN_TIMEOUT;
    }

    private static ContainmentVerdict timeout(ContainmentEscalation escalation, ContainmentStalemate stalemate,
                                              boolean staticOnly, boolean unreachable, int frame) {
        int reentries = escalation.getReentries();
        BooleanSupplier read = () -> staticOnly;
        ContainmentVerdict escalated = escalatedVerdict(ContainmentVerdict.RETREAT, ON_TIMEOUT, escalation, read,
                frame);
        return stalemateVerdict(escalated, ON_TIMEOUT, reentries, staticOnly, unreachable, stalemate, frame);
    }

    @Test
    void aTimeoutAgainstAnArmyIsAStalemateOnceTheRunReachesTheLimitOrTheBreakIsOutOfReach() {
        assertTrue(ContainmentStalemate.isStalemate(STALEMATE_AFTER_REENTRIES, ARMY_OUTSIDE, REACHABLE));
        assertFalse(ContainmentStalemate.isStalemate(STALEMATE_AFTER_REENTRIES - 1, ARMY_OUTSIDE, REACHABLE));
        assertTrue(ContainmentStalemate.isStalemate(0, ARMY_OUTSIDE, UNREACHABLE));
        assertFalse(ContainmentStalemate.isStalemate(STALEMATE_AFTER_REENTRIES, STATIC_ONLY, UNREACHABLE));
    }

    @Test
    void theArmyBackedLoopLeavesTheArcOnTheThirdTimeoutInsteadOfReenteringIt() {
        ContainmentEscalation escalation = new ContainmentEscalation();
        ContainmentStalemate stalemate = new ContainmentStalemate();
        escalation.onEntered(FIRST_ENTRY);
        for (int i = 0; i < STALEMATE_AFTER_REENTRIES; i++) {
            int frame = cyclesTimeout(i);
            assertEquals(ContainmentVerdict.RETREAT, timeout(escalation, stalemate, ARMY_OUTSIDE, REACHABLE, frame));
            assertFalse(stalemate.holdsEntry(frame + RETREAT_LOCK));
            escalation.onEntered(frame + RETREAT_LOCK);
        }
        int stalemateFrame = cyclesTimeout(STALEMATE_AFTER_REENTRIES);
        assertEquals(ContainmentVerdict.STALEMATE,
                timeout(escalation, stalemate, ARMY_OUTSIDE, REACHABLE, stalemateFrame));
        assertTrue(stalemate.isDetected());
        assertEquals(1, stalemate.getDetections());
        assertEquals(stalemateFrame, stalemate.getLastDetectedFrame());
        assertTrue(stalemate.holdsEntry(stalemateFrame + RETREAT_LOCK));
        assertTrue(stalemate.holdsEntry(stalemateFrame + HOLD_FRAMES - 1));
        assertFalse(stalemate.holdsEntry(stalemateFrame + HOLD_FRAMES));
    }

    @Test
    void anUnreachableBreakLeavesTheArcOnTheFirstTimeout() {
        ContainmentEscalation escalation = new ContainmentEscalation();
        ContainmentStalemate stalemate = new ContainmentStalemate();
        escalation.onEntered(FIRST_ENTRY);
        assertEquals(ContainmentVerdict.STALEMATE,
                timeout(escalation, stalemate, ARMY_OUTSIDE, UNREACHABLE, cyclesTimeout(0)));
    }

    @Test
    void aStaticOnlyDefenceStillEscalatesAndIsNeverAStalemate() {
        ContainmentEscalation escalation = new ContainmentEscalation();
        ContainmentStalemate stalemate = new ContainmentStalemate();
        escalation.onEntered(FIRST_ENTRY);
        for (int i = 0; i < ContainmentEscalation.ESCALATE_AFTER_REENTRIES; i++) {
            assertEquals(ContainmentVerdict.RETREAT,
                    timeout(escalation, stalemate, STATIC_ONLY, UNREACHABLE, cyclesTimeout(i)));
            escalation.onEntered(cyclesTimeout(i) + RETREAT_LOCK);
        }
        int frame = cyclesTimeout(ContainmentEscalation.ESCALATE_AFTER_REENTRIES);
        assertEquals(ContainmentVerdict.ESCALATE, timeout(escalation, stalemate, STATIC_ONLY, UNREACHABLE, frame));
        assertFalse(stalemate.isDetected());
        assertFalse(stalemate.holdsEntry(frame));
    }

    @Test
    void aDetectedStalemateMakesTheNextTimeoutAStalemateAtOnce() {
        ContainmentStalemate stalemate = new ContainmentStalemate();
        assertTrue(stalemate.onTimedOut(STALEMATE_AFTER_REENTRIES, ARMY_OUTSIDE, REACHABLE, FIRST_ENTRY));
        int nextTimeout = FIRST_ENTRY + HOLD_FRAMES + CONTAIN_TIMEOUT;
        assertTrue(stalemate.onTimedOut(0, ARMY_OUTSIDE, REACHABLE, nextTimeout));
        assertEquals(2, stalemate.getDetections());
        assertTrue(stalemate.holdsEntry(nextTimeout + HOLD_FRAMES - 1));
        assertFalse(stalemate.onTimedOut(0, STATIC_ONLY, REACHABLE, nextTimeout + 1));
    }

    @Test
    void anyOtherEndToAContainClearsTheStalemateButNotItsHold() {
        ContainmentStalemate stalemate = new ContainmentStalemate();
        assertTrue(stalemate.onTimedOut(0, ARMY_OUTSIDE, UNREACHABLE, FIRST_ENTRY));
        stalemate.onEndedOtherwise();
        assertFalse(stalemate.isDetected());
        assertTrue(stalemate.holdsEntry(FIRST_ENTRY + 1));
        assertFalse(stalemate.onTimedOut(0, ARMY_OUTSIDE, REACHABLE, FIRST_ENTRY + HOLD_FRAMES + CONTAIN_TIMEOUT));
    }

    @Test
    void theHoldOutlastsTheReentryWindowSoTheNextContainStartsANewRun() {
        assertTrue(HOLD_FRAMES > ContainmentEscalation.REENTRY_WINDOW_FRAMES);
        ContainmentEscalation escalation = new ContainmentEscalation();
        escalation.onEntered(FIRST_ENTRY);
        assertFalse(escalation.onTimedOut(ARMY_OUTSIDE, cyclesTimeout(0)));
        escalation.onEntered(cyclesTimeout(0) + HOLD_FRAMES);
        assertEquals(0, escalation.getReentries());
    }

    @Test
    void onlyATimeoutRetreatIsReadForAStalemate() {
        ContainmentStalemate stalemate = new ContainmentStalemate();
        assertEquals(ContainmentVerdict.ESCALATE, stalemateVerdict(ContainmentVerdict.ESCALATE, ON_TIMEOUT,
                STALEMATE_AFTER_REENTRIES, ARMY_OUTSIDE, UNREACHABLE, stalemate, FIRST_ENTRY));
        assertEquals(ContainmentVerdict.RETREAT, stalemateVerdict(ContainmentVerdict.RETREAT, false,
                STALEMATE_AFTER_REENTRIES, ARMY_OUTSIDE, UNREACHABLE, stalemate, FIRST_ENTRY));
        assertEquals(ContainmentVerdict.BREAK_ALL, stalemateVerdict(ContainmentVerdict.BREAK_ALL, ON_TIMEOUT,
                STALEMATE_AFTER_REENTRIES, ARMY_OUTSIDE, UNREACHABLE, stalemate, FIRST_ENTRY));
        assertFalse(stalemate.isDetected());
        assertFalse(stalemate.holdsEntry(FIRST_ENTRY));
    }

    @Test
    void aStalemateExitIsBridgedByTheHeldTimerLikeATimeout() {
        assertFalse(SquadManager.breaksHeldContain(ContainmentVerdict.STALEMATE,
                SquadManager.containmentExitPath(false, false)));
    }

    @Test
    void theBreakShortfallIsTheSupplyTheBreakRatioStillNeeds() {
        int enemy = supply(UnitType.Protoss_Zealot, 34) + supply(UnitType.Protoss_Dragoon, 11)
                + supply(UnitType.Protoss_High_Templar, 5) + 2 * CANNON_SUPPLY;
        int ours = 308;
        ContainmentEvaluator.BreakMeasure measure = new ContainmentEvaluator.BreakMeasure(ours, enemy);
        assertEquals(212, enemy);
        assertEquals(10, measure.shortfall());
        assertFalse(measure.breaks());
        assertFalse(measure.unreachable());
        assertEquals(0, new ContainmentEvaluator.BreakMeasure(318, enemy).shortfall());
        assertTrue(new ContainmentEvaluator.BreakMeasure(318, enemy).breaks());
        assertEquals(0, new ContainmentEvaluator.BreakMeasure(500, enemy).shortfall());
    }

    @Test
    void aBreakAboveTheSupplyCapIsUnreachable() {
        int enemy = supply(UnitType.Protoss_Zealot, 40) + supply(UnitType.Protoss_Dragoon, 31)
                + supply(UnitType.Protoss_High_Templar, 5) + 2 * CANNON_SUPPLY;
        assertEquals(316, enemy);
        assertTrue(ContainmentEvaluator.breakUnreachable(enemy));
        assertFalse(ContainmentEvaluator.breakUnreachable(266));
        assertTrue(ContainmentEvaluator.breakUnreachable(267));
        assertFalse(new ContainmentEvaluator.BreakMeasure(ContainmentEvaluator.MAX_SUPPLY, enemy).breaks());
    }

    @Test
    void theMeasuredBreakKeepsTheBreakRule() {
        assertEquals(ContainmentEvaluator.breaks(150, 100), new ContainmentEvaluator.BreakMeasure(150, 100).breaks());
        assertEquals(ContainmentEvaluator.breaks(149, 100), new ContainmentEvaluator.BreakMeasure(149, 100).breaks());
        assertTrue(ContainmentEvaluator.breaks(150, 100));
        assertFalse(ContainmentEvaluator.breaks(149, 100));
    }
}
