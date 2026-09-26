package util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeleeOverflowGateTest {

    private static final int START = 1000;

    private static int report(MeleeOverflowGate gate, boolean saturated, int fromFrame, int frames) {
        for (int i = 0; i < frames; i++) {
            gate.observe(saturated, fromFrame + i);
        }
        return fromFrame + frames;
    }

    private static int enterOverflow(MeleeOverflowGate gate) {
        int next = report(gate, true, START, MeleeOverflowGate.ENTER_FRAMES);
        assertTrue(gate.isOverflowing());
        return next;
    }

    @Test
    void entersOnlyAfterEnterFramesOfConsecutiveSaturatedPicks() {
        MeleeOverflowGate gate = new MeleeOverflowGate();

        int next = report(gate, true, START, MeleeOverflowGate.ENTER_FRAMES - 1);
        assertFalse(gate.isOverflowing());

        assertTrue(gate.observe(true, next));
        assertTrue(gate.isOverflowLocked(next));
    }

    @Test
    void anUnsaturatedPickRestartsTheEnterStreak() {
        MeleeOverflowGate gate = new MeleeOverflowGate();

        int next = report(gate, true, START, MeleeOverflowGate.ENTER_FRAMES - 1);
        next = report(gate, false, next, 1);
        next = report(gate, true, next, MeleeOverflowGate.ENTER_FRAMES - 1);

        assertFalse(gate.isOverflowing());
        assertTrue(gate.observe(true, next));
    }

    @Test
    void aFrameWithoutAReportRestartsTheEnterStreak() {
        MeleeOverflowGate gate = new MeleeOverflowGate();

        int next = report(gate, true, START, MeleeOverflowGate.ENTER_FRAMES - 1);
        report(gate, true, next + 1, 1);

        assertFalse(gate.isOverflowing());
    }

    @Test
    void aSecondReportOnOneFrameDoesNotExtendTheStreak() {
        MeleeOverflowGate gate = new MeleeOverflowGate();

        for (int i = 0; i < MeleeOverflowGate.ENTER_FRAMES; i++) {
            gate.observe(true, START);
        }

        assertFalse(gate.isOverflowing());
    }

    @Test
    void holdsThroughTheMinimumHoldEvenWithOpenPicks() {
        MeleeOverflowGate gate = new MeleeOverflowGate();
        int entered = enterOverflow(gate) - 1;

        report(gate, false, entered + 1, MeleeOverflowGate.MIN_HOLD_FRAMES - 1);

        assertTrue(MeleeOverflowGate.MIN_HOLD_FRAMES - 1 >= MeleeOverflowGate.EXIT_FRAMES);
        assertTrue(gate.isOverflowing());
        assertTrue(gate.observe(false, entered + MeleeOverflowGate.MIN_HOLD_FRAMES));
    }

    @Test
    void openPicksDuringTheHoldDoNotCountTowardLeaving() {
        MeleeOverflowGate gate = new MeleeOverflowGate();
        int entered = enterOverflow(gate) - 1;
        int lockExpires = entered + MeleeOverflowGate.MIN_HOLD_FRAMES;

        int next = report(gate, false, entered + 1, lockExpires - entered - 1);
        assertFalse(gate.isOverflowLocked(next));
        next = report(gate, false, next, MeleeOverflowGate.EXIT_FRAMES - 1);

        assertTrue(gate.isOverflowing());
        assertFalse(gate.observe(false, next));
        assertTrue(next - lockExpires + 1 == MeleeOverflowGate.EXIT_FRAMES);
    }

    @Test
    void aSaturatedReTargetEntersOnItsFirstFrame() {
        MeleeOverflowGate gate = new MeleeOverflowGate();

        assertTrue(gate.observe(true, true, START));
        assertTrue(gate.isOverflowLocked(START));
    }

    @Test
    void aSaturatedReTargetEntersAfterOpenFramesOnThePreviousTarget() {
        MeleeOverflowGate gate = new MeleeOverflowGate();
        int next = report(gate, false, START, 30);

        assertTrue(gate.observe(true, true, next));
    }

    @Test
    void anUnsaturatedReTargetLeavesOverflowAtOnceEvenDuringTheHold() {
        MeleeOverflowGate gate = new MeleeOverflowGate();
        int next = enterOverflow(gate);
        assertTrue(gate.isOverflowLocked(next));

        assertFalse(gate.observe(false, true, next));
        assertFalse(gate.isOverflowLocked(next));
    }

    @Test
    void aSaturatedReTargetDuringTheHoldStaysInOverflow() {
        MeleeOverflowGate gate = new MeleeOverflowGate();
        int next = enterOverflow(gate);

        assertTrue(gate.observe(true, true, next));
    }

    @Test
    void anUnsaturatedReTargetDoesNotEnter() {
        MeleeOverflowGate gate = new MeleeOverflowGate();

        assertFalse(gate.observe(false, true, START));
        assertFalse(gate.isOverflowing());
    }

    @Test
    void aHeldTargetThatBecomesSaturatedStillNeedsTheEnterStreak() {
        MeleeOverflowGate gate = new MeleeOverflowGate();
        gate.observe(false, true, START);

        for (int i = 1; i < MeleeOverflowGate.ENTER_FRAMES; i++) {
            assertFalse(gate.observe(true, false, START + i));
        }
        assertTrue(gate.observe(true, false, START + MeleeOverflowGate.ENTER_FRAMES));
    }

    @Test
    void leavesOnlyAfterExitFramesOfConsecutiveOpenPicksOnceTheHoldHasRunOut() {
        MeleeOverflowGate gate = new MeleeOverflowGate();
        int next = enterOverflow(gate);
        next = report(gate, true, next, MeleeOverflowGate.MIN_HOLD_FRAMES);
        assertFalse(gate.isOverflowLocked(next));

        next = report(gate, false, next, MeleeOverflowGate.EXIT_FRAMES - 1);
        assertTrue(gate.isOverflowing());

        assertFalse(gate.observe(false, next));
    }

    @Test
    void aSaturatedPickRestartsTheExitStreak() {
        MeleeOverflowGate gate = new MeleeOverflowGate();
        int next = enterOverflow(gate);
        next = report(gate, true, next, MeleeOverflowGate.MIN_HOLD_FRAMES);

        next = report(gate, false, next, MeleeOverflowGate.EXIT_FRAMES - 1);
        next = report(gate, true, next, 1);
        next = report(gate, false, next, MeleeOverflowGate.EXIT_FRAMES - 1);

        assertTrue(gate.isOverflowing());
        assertFalse(gate.observe(false, next));
    }

    @Test
    void staysInOverflowWhileThePickStaysSaturated() {
        MeleeOverflowGate gate = new MeleeOverflowGate();
        int next = enterOverflow(gate);

        report(gate, true, next, MeleeOverflowGate.MIN_HOLD_FRAMES * 4);

        assertTrue(gate.isOverflowing());
    }

    @Test
    void aFrameWithoutAReportEndsOverflow() {
        MeleeOverflowGate gate = new MeleeOverflowGate();
        int next = enterOverflow(gate);

        assertFalse(gate.observe(true, next + 1));
    }

    @Test
    void clearingDropsOverflowAndItsLock() {
        MeleeOverflowGate gate = new MeleeOverflowGate();
        int next = enterOverflow(gate);

        gate.clearOverflowStart();

        assertFalse(gate.isOverflowing());
        assertFalse(gate.isOverflowLocked(next));
    }
}
