package unit.squad;

import org.junit.jupiter.api.Test;
import telemetry.DecisionPath;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainHeldTimerTest {

    private static final int START = 5000;

    private static final int TIMEOUT_FRAMES = 1400;

    private static ContainHeldTimer containedFor(int frames) {
        ContainHeldTimer timer = new ContainHeldTimer();
        for (int frame = START; frame <= START + frames; frame++) {
            timer.update(frame, true);
        }
        return timer;
    }

    @Test
    void noChainRunsBeforeAnySquadContains() {
        ContainHeldTimer timer = new ContainHeldTimer();

        timer.update(START, false);

        assertEquals(ContainHeldTimer.NO_CHAIN, timer.getChainStartFrame());
        assertEquals(0, timer.heldFrames(START));
        assertFalse(timer.isHeld(START));
    }

    @Test
    void theContainIsHeldOnceTheChainLastsTwentySeconds() {
        ContainHeldTimer timer = containedFor(ContainHeldTimer.HELD_FRAMES - 1);
        assertFalse(timer.isHeld(START + ContainHeldTimer.HELD_FRAMES - 1));

        timer.update(START + ContainHeldTimer.HELD_FRAMES, true);

        assertTrue(timer.isHeld(START + ContainHeldTimer.HELD_FRAMES));
        assertEquals(START, timer.getChainStartFrame());
    }

    @Test
    void aTimeoutAndImmediateReEntryDoNotResetTheHeldTimer() {
        ContainHeldTimer timer = containedFor(TIMEOUT_FRAMES);
        int exit = START + TIMEOUT_FRAMES + 1;
        for (int frame = exit; frame < exit + ContainHeldTimer.REENTRY_GAP_FRAMES; frame++) {
            timer.update(frame, false);
        }

        int reentry = exit + ContainHeldTimer.REENTRY_GAP_FRAMES;
        timer.update(reentry, true);

        assertEquals(START, timer.getChainStartFrame());
        assertEquals(reentry - START, timer.heldFrames(reentry));
        assertTrue(timer.isHeld(reentry));
    }

    @Test
    void theChainStaysHeldThroughTheGapItself() {
        ContainHeldTimer timer = containedFor(ContainHeldTimer.HELD_FRAMES);
        int last = START + ContainHeldTimer.HELD_FRAMES;

        timer.update(last + ContainHeldTimer.REENTRY_GAP_FRAMES, false);

        assertTrue(timer.isHeld(last + ContainHeldTimer.REENTRY_GAP_FRAMES));
    }

    @Test
    void aGapLongerThanTheReEntryGapEndsTheChain() {
        ContainHeldTimer timer = containedFor(ContainHeldTimer.HELD_FRAMES);
        int last = START + ContainHeldTimer.HELD_FRAMES;

        timer.update(last + ContainHeldTimer.REENTRY_GAP_FRAMES + 1, false);

        assertEquals(ContainHeldTimer.NO_CHAIN, timer.getChainStartFrame());
        assertFalse(timer.isHeld(last + ContainHeldTimer.REENTRY_GAP_FRAMES + 1));
    }

    @Test
    void aReEntryAfterTheChainEndedStartsANewChain() {
        ContainHeldTimer timer = containedFor(ContainHeldTimer.HELD_FRAMES);
        int reentry = START + ContainHeldTimer.HELD_FRAMES + ContainHeldTimer.REENTRY_GAP_FRAMES + 1;
        timer.update(reentry, false);

        timer.update(reentry + 1, true);

        assertEquals(reentry + 1, timer.getChainStartFrame());
        assertFalse(timer.isHeld(reentry + 1));
    }

    @Test
    void anEnemyBreakEndsTheChainAndASquadStillContainingStartsANewOne() {
        ContainHeldTimer timer = containedFor(ContainHeldTimer.HELD_FRAMES);
        int breakFrame = START + ContainHeldTimer.HELD_FRAMES + 1;

        timer.broken();
        assertEquals(ContainHeldTimer.NO_CHAIN, timer.getChainStartFrame());

        timer.update(breakFrame, true);
        assertEquals(breakFrame, timer.getChainStartFrame());
        assertFalse(timer.isHeld(breakFrame));
    }

    @Test
    void onlyAGroundSquadInContainCountsAsContaining() {
        Squad containing = new GroundSquad();
        containing.setStatus(SquadStatus.CONTAIN);
        Squad rallying = new GroundSquad();
        rallying.setStatus(SquadStatus.RALLY);
        Squad air = new AirSquad();
        air.setStatus(SquadStatus.CONTAIN);

        assertTrue(SquadManager.anyGroundSquadContaining(Arrays.asList(rallying, containing)));
        assertFalse(SquadManager.anyGroundSquadContaining(Arrays.asList(rallying, air)));
        assertFalse(SquadManager.anyGroundSquadContaining(Collections.emptyList()));
    }

    @Test
    void anEnemyForcedRetreatBreaksTheHeldContainButATimeoutRetreatIsBridged() {
        SquadManager.ContainmentVerdict retreat = SquadManager.ContainmentVerdict.RETREAT;
        assertTrue(SquadManager.breaksHeldContain(retreat, DecisionPath.CONTAIN_ATTRITION));
        assertTrue(SquadManager.breaksHeldContain(retreat, DecisionPath.CONTAIN_OUTRANGED));
        assertFalse(SquadManager.breaksHeldContain(retreat, DecisionPath.CONTAIN_RETREAT));
    }

    @Test
    void everyBreakAllBreaksTheHeldContainIncludingTheStrengthGate() {
        boolean timedOut = false;
        boolean canBreak = true;
        SquadManager.ContainmentVerdict strengthGate = SquadManager.containmentVerdict(false, false,
                SquadManager.OutrangedHit.NONE, false, false, timedOut, canBreak, true);
        assertEquals(SquadManager.ContainmentVerdict.BREAK_ALL, strengthGate);
        assertTrue(SquadManager.breaksHeldContain(strengthGate, DecisionPath.CONTAIN_RETREAT));

        SquadManager.ContainmentVerdict baseAttack = SquadManager.containmentVerdict(true, false,
                SquadManager.OutrangedHit.NONE, false, false, false, false, true);
        assertTrue(SquadManager.breaksHeldContain(baseAttack, DecisionPath.CONTAIN_RETREAT));
    }

    @Test
    void holdingPushingBackOrRepositioningNeverBreaksTheHeldContain() {
        assertFalse(SquadManager.breaksHeldContain(SquadManager.ContainmentVerdict.HOLD, DecisionPath.CONTAIN_ATTRITION));
        assertFalse(SquadManager.breaksHeldContain(SquadManager.ContainmentVerdict.PUSH_BACK,
                DecisionPath.CONTAIN_OUTRANGED));
        assertFalse(SquadManager.breaksHeldContain(SquadManager.ContainmentVerdict.REPOSITION,
                DecisionPath.CONTAIN_RETREAT));
    }

    @Test
    void theTimeoutRetreatIsTheBridgedPath() {
        SquadManager.ContainmentVerdict timeout = SquadManager.containmentVerdict(false, false,
                SquadManager.OutrangedHit.NONE, false, false, true, false, true);
        assertEquals(SquadManager.ContainmentVerdict.RETREAT, timeout);
        assertEquals(DecisionPath.CONTAIN_RETREAT, SquadManager.containmentExitPath(false, false));
        assertFalse(SquadManager.breaksHeldContain(timeout, SquadManager.containmentExitPath(false, false)));
    }
}
