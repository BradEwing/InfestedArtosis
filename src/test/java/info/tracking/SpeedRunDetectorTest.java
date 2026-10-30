package info.tracking;

import bwapi.UnitType;
import bwapi.UpgradeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeedRunDetectorTest {

    private static final UpgradeType BOOST = UpgradeType.Metabolic_Boost;
    private static final UnitType LING = UnitType.Zerg_Zergling;
    private static final int W = SpeedRunDetector.WINDOW_FRAMES;
    private static final int BOUND = 3000;

    private static SpeedRunDetector detector() {
        return new SpeedRunDetector(upgrade -> BOUND);
    }

    private static double upgraded() {
        return LING.topSpeed() * 1.5;
    }

    @Test
    void anUnupgradedLingAtTopSpeedIsNotFast() {
        assertFalse(SpeedRunDetector.exceedsBaseSpeed(LING, LING.topSpeed()));
        assertTrue(SpeedRunDetector.exceedsBaseSpeed(LING, upgraded()));
        assertFalse(SpeedRunDetector.exceedsBaseSpeed(UnitType.Zerg_Mutalisk, 100));
    }

    @Test
    void anUnupgradedLingWithAFlickeringMovingFlagAndVelocityIsNeverSeen() {
        SpeedRunDetector detector = detector();
        int start = BOUND + 100;
        for (int i = 0; i < 20 * W; i++) {
            double velocity = i % 2 == 0 ? 0 : 3 * LING.topSpeed();
            detector.observe(start + i, 7, LING, 100 + (int) Math.round(i * LING.topSpeed()), 100, velocity,
                    i % 3 != 0);
            detector.onFrame(start + i);
        }

        assertNull(detector.seen(BOOST));
        assertNull(detector.earlyRun(BOOST));
        SpeedRunDetector.Diagnostic diagnostic = detector.diagnostic(LING);
        assertEquals(0, diagnostic.getLongestRun());
        assertTrue(diagnostic.getVelocityFrames() > 0);
        assertTrue(diagnostic.getNotMovingFrames() > 0);
        assertTrue(diagnostic.getWindowSpeed() < SpeedRunDetector.SPEED_MARGIN);
        assertTrue(diagnostic.getWindowSpeed() > 0.9);
    }

    @Test
    void anUpgradedLingIsSeenAWindowAfterItStartsAndAgainstItsSpan() {
        SpeedRunDetector detector = detector();
        int start = BOUND;
        for (int i = 0; i <= W; i++) {
            detector.observe(start + i, 7, LING, 100 + (int) Math.round(i * upgraded()), 100, 0, false);
        }

        SpeedRunDetector.Sighting seen = detector.seen(BOOST);
        assertNotNull(seen);
        assertEquals(start + W, seen.getFrame());
        assertEquals(W, seen.getRunFrames());
        assertEquals(upgraded(), seen.getMeasuredSpeed(), 0.1);
        assertEquals(BOUND, seen.getBoundFrame());
    }

    @Test
    void aSingleMissedFrameInsideTheWindowIsMeasuredOverTheFramesItSpans() {
        SpeedRunDetector detector = detector();
        int start = BOUND;
        for (int i = 0; i <= W + 1; i++) {
            if (i == 1) {
                continue;
            }
            detector.observe(start + i, 7, LING, 100 + (int) Math.round(i * upgraded()), 100, 0, true);
        }

        SpeedRunDetector.Sighting seen = detector.seen(BOOST);
        assertNotNull(seen);
        assertEquals(start + W, seen.getFrame());
        assertEquals(W, seen.getRunFrames());

        SpeedRunDetector missedAtStart = detector();
        for (int i = 0; i <= W + 1; i++) {
            if (i == 1 || i == W) {
                continue;
            }
            missedAtStart.observe(start + i, 7, LING, 100 + (int) Math.round(i * upgraded()), 100, 0, true);
        }
        assertEquals(start + W + 1, missedAtStart.seen(BOOST).getFrame());
    }

    @Test
    void aCurvingUpgradedLingIsStillSeen() {
        SpeedRunDetector detector = detector();
        double radius = 200;
        for (int i = 0; i <= W; i++) {
            double angle = i * upgraded() / radius;
            detector.observe(BOUND + i, 7, LING, 1000 + (int) Math.round(radius * Math.cos(angle)),
                    1000 + (int) Math.round(radius * Math.sin(angle)), 0, true);
        }

        assertNotNull(detector.seen(BOOST));
    }

    @Test
    void aTransportUnloadJumpIsNotAWindow() {
        SpeedRunDetector detector = detector();
        int start = BOUND + 100;
        for (int i = 0; i < 3 * W; i++) {
            int jump = i >= W ? 400 : 0;
            detector.observe(start + i, 7, LING, 100 + jump + (int) Math.round(i * LING.topSpeed()), 100, 0, true);
            detector.onFrame(start + i);
        }

        assertTrue(SpeedRunDetector.impossibleStep(LING, 400, 1));
        assertFalse(SpeedRunDetector.impossibleStep(LING, upgraded() * 2, 2));
        assertNull(detector.seen(BOOST));
        assertTrue(detector.diagnostic(LING).getWindowSpeed() < SpeedRunDetector.SPEED_MARGIN);
    }

    @Test
    void anUnupgradedUnitAtTopSpeedWithUpToTwoStepsAtTheCutAndWorstRoundingNeverPasses() {
        for (UnitType type : new UnitType[] {LING, UnitType.Zerg_Hydralisk}) {
            double v = type.topSpeed();
            double rounding = SpeedRunDetector.POSITION_ROUNDING;
            double cut = v * SpeedRunDetector.UPGRADED_SPEED_FACTOR + rounding;
            for (int steps = 1; steps <= 2; steps++) {
                double distance = (W - steps) * v + steps * cut + rounding;
                assertFalse(SpeedRunDetector.passes(type, distance, cut - v, W));
            }
            assertFalse(SpeedRunDetector.passes(type, (W - 1) * v + cut + rounding, 0, W));
            assertFalse(SpeedRunDetector.impossibleStep(type, cut, 1));
            assertTrue(SpeedRunDetector.impossibleStep(type, cut + 0.01, 1));
        }
    }

    @Test
    void anUpgradedUnitWithAStepAtTheCutAndWorstRoundingStillPasses() {
        for (UnitType type : new UnitType[] {LING, UnitType.Zerg_Hydralisk}) {
            double upgraded = type.topSpeed() * SpeedRunDetector.UPGRADED_SPEED_FACTOR;
            double rounding = SpeedRunDetector.POSITION_ROUNDING;
            double longest = upgraded + rounding;

            assertFalse(SpeedRunDetector.impossibleStep(type, longest, 1));
            assertTrue(SpeedRunDetector.passes(type, W * upgraded - rounding, longest - type.topSpeed(), W));
        }
    }

    @Test
    void anUnupgradedLingAndHydraWithTwoLongStepsInEveryWindowAreNeverSeen() {
        for (UnitType type : new UnitType[] {LING, UnitType.Zerg_Hydralisk}) {
            SpeedRunDetector detector = detector();
            double v = type.topSpeed();
            double longStep = v * SpeedRunDetector.UPGRADED_SPEED_FACTOR + SpeedRunDetector.POSITION_ROUNDING - 1.2;
            double x = 100;
            for (int i = 0; i < 20 * W; i++) {
                int phase = i % W;
                x += phase == 5 || phase == 9 ? longStep : v;
                detector.observe(BOUND + i, 7, type, (int) Math.round(x), 100, 0, true);
                detector.onFrame(BOUND + i);
            }

            UpgradeType upgrade = EnemySpeedTracker.upgradeFor(type);
            assertNull(detector.seen(upgrade));
            assertTrue(detector.diagnostic(type).getWindows() > 10 * W);
        }
    }

    @Test
    void aWindowThatPassesBeforeTheBoundIsAnEarlyRunAndOneStraddlingItIsNeither() {
        SpeedRunDetector detector = detector();
        int start = BOUND - W - 1;
        for (int i = 0; i <= W; i++) {
            detector.observe(start + i, 7, LING, 100 + (int) Math.round(i * upgraded()), 100, 0, true);
        }
        assertEquals(BOUND - 1, detector.earlyRun(BOOST).getFrame());

        for (int i = W + 1; i < 2 * W + 1; i++) {
            detector.observe(start + i, 7, LING, 100 + (int) Math.round(i * upgraded()), 100, 0, true);
        }
        assertNull(detector.seen(BOOST));
        detector.observe(start + 2 * W + 1, 7, LING, 100 + (int) Math.round((2 * W + 1) * upgraded()), 100, 0, true);
        assertEquals(BOUND + W, detector.seen(BOOST).getFrame());
    }

    @Test
    void theDiagnosticsTallyTheWindowsAndStartAfreshOnAReset() {
        SpeedRunDetector detector = detector();
        int frames = 2 * W;
        for (int i = 0; i < frames; i++) {
            detector.observe(BOUND + i, 7, LING, 100 + (int) Math.round(i * upgraded()), 100, upgraded(), true);
        }

        SpeedRunDetector.Diagnostic diagnostic = detector.diagnostic(LING);
        assertEquals(frames - W, diagnostic.getWindows());
        assertEquals(frames - W, diagnostic.getLongestRun());
        assertEquals(frames, diagnostic.getVelocityFrames());
        assertEquals(0, diagnostic.getNotMovingFrames());
        assertEquals(1.5, diagnostic.getShortWindowSpeed(), 0.05);
        assertEquals(1.5, diagnostic.getWindowSpeed(), 0.05);
        assertEquals(1.5, diagnostic.getLongWindowSpeed(), 0.05);

        detector.resetDiagnostic(LING, BOUND + frames);
        SpeedRunDetector.Diagnostic fresh = detector.diagnostic(LING);
        assertEquals(BOUND + frames, fresh.getFromFrame());
        assertEquals(0, fresh.getWindows());
        assertEquals(0, detector.diagnostic(UnitType.Zerg_Hydralisk).getWindows());
    }
}
