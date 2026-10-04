package unit.squad;

import bwapi.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirStallDetectorTest {

    private static final int START = 11000;
    private static final int STEP = 100;

    private static int flap(AirStallDetector detector, int frame, int crossings) {
        int now = frame;
        SquadStatus side = SquadStatus.FIGHT;
        detector.observe(now, side);
        for (int i = 0; i < crossings; i++) {
            now += STEP;
            side = side == SquadStatus.FIGHT ? SquadStatus.RETREAT : SquadStatus.FIGHT;
            detector.observe(now, side);
        }
        return now;
    }

    @Test
    void aSquadCrossingTheThresholdKTimesWithinTheWindowWithNoKillsIsStalled() {
        AirStallDetector detector = new AirStallDetector();

        int now = flap(detector, START, AirStallDetector.CROSSINGS - 1);
        assertFalse(detector.isStalled(now));

        now = flap(detector, now, 1);
        assertTrue(AirStallDetector.CROSSINGS * STEP <= AirStallDetector.WINDOW_FRAMES);
        assertTrue(detector.isStalled(now));
    }

    @Test
    void aStalledSquadIsOfferedTheHarassDespiteAHoldOnADifferentTarget() {
        AirStallDetector detector = new AirStallDetector();
        int now = flap(detector, START, AirStallDetector.CROSSINGS);
        Position failedBase = new Position(3808, 2096);
        int broke = now - 40;

        assertTrue(detector.isStalled(now));
        assertEquals(AirHarassEvaluator.ReentryHold.TARGET,
                AirHarassEvaluator.reentryHold(broke, failedBase, detector.isStalled(now), now));
        assertEquals(AirHarassEvaluator.ReentryHold.NONE,
                AirHarassEvaluator.reentryHold(broke, null, detector.isStalled(now), now));
        assertEquals(AirHarassEvaluator.ReentryHold.ALL, AirHarassEvaluator.reentryHold(broke, null, false, now));
        assertTrue(AirHarassEvaluator.isFailedTarget(failedBase, failedBase));
        assertFalse(AirHarassEvaluator.isFailedTarget(failedBase, new Position(1000, 400)));
        int tick = now - now % AirHarassEvaluator.HARASS_TICK;
        boolean retreatLocked = true;
        assertFalse(AirHarassEvaluator.entryCheckDue(true, retreatLocked, false, tick));
        assertTrue(AirHarassEvaluator.entryCheckDue(true, retreatLocked && !detector.isStalled(now), false, tick));
    }

    @Test
    void aKillSinceTheOldestCrossingEndsTheStall() {
        AirStallDetector detector = new AirStallDetector();
        int now = flap(detector, START, AirStallDetector.CROSSINGS);

        detector.onKill(now);

        assertFalse(detector.isStalled(now));
    }

    @Test
    void aKillOlderThanTheLastKCrossingsDoesNotEndTheStall() {
        AirStallDetector detector = new AirStallDetector();
        int now = flap(detector, START, AirStallDetector.CROSSINGS + 1);

        detector.onKill(START + STEP + 1);

        assertTrue(detector.isStalled(now));
    }

    @Test
    void aMergedDetectorKeepsTheCrossingsOfItsSources() {
        AirStallDetector first = new AirStallDetector();
        AirStallDetector second = new AirStallDetector();
        int now = flap(first, START, AirStallDetector.CROSSINGS);
        AirStallDetector merged = new AirStallDetector();

        merged.absorb(first);
        merged.absorb(second);

        assertTrue(merged.isStalled(now));
        second.onKill(now);
        AirStallDetector killed = new AirStallDetector();
        killed.absorb(first);
        killed.absorb(second);
        assertFalse(killed.isStalled(now));
    }

    @Test
    void aKillBeforeTheRunDoesNotEndTheStall() {
        AirStallDetector detector = new AirStallDetector();
        detector.onKill(START - 1);

        int now = flap(detector, START, AirStallDetector.CROSSINGS);

        assertTrue(detector.isStalled(now));
    }

    @Test
    void crossingsOlderThanTheWindowDoNotCount() {
        AirStallDetector detector = new AirStallDetector();
        int now = flap(detector, START, AirStallDetector.CROSSINGS);

        assertFalse(detector.isStalled(now + AirStallDetector.WINDOW_FRAMES));
    }

    @Test
    void leavingFightAndRetreatEndsTheRun() {
        AirStallDetector detector = new AirStallDetector();
        int now = flap(detector, START, AirStallDetector.CROSSINGS);

        detector.observe(now + 1, SquadStatus.HARASS);

        assertFalse(detector.isStalled(now + 1));
    }

    @Test
    void aSquadStayingInOneStatusNeverStalls() {
        AirStallDetector detector = new AirStallDetector();
        for (int frame = START; frame < START + 2000; frame += 12) {
            detector.observe(frame, SquadStatus.FIGHT);
        }

        assertFalse(detector.isStalled(START + 2000));
    }
}
