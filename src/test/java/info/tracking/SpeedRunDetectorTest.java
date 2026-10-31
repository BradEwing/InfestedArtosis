package info.tracking;

import bwapi.UnitType;
import bwapi.UpgradeType;
import org.junit.jupiter.api.Test;

import java.util.function.IntToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeedRunDetectorTest {

    private static final UpgradeType BOOST = UpgradeType.Metabolic_Boost;
    private static final UnitType LING = UnitType.Zerg_Zergling;
    private static final UnitType HYDRA = UnitType.Zerg_Hydralisk;
    private static final UnitType[] FOLLOWED = {LING, HYDRA};
    private static final int W = SpeedRunDetector.WINDOW_FRAMES;
    private static final int R = SpeedRunDetector.MIN_RUN_FRAMES;
    private static final int BOUND = 3000;
    private static final double LING_CEILING = 1.172;
    private static final double HYDRA_CEILING = 1.203;

    private static SpeedRunDetector detector() {
        return new SpeedRunDetector(upgrade -> BOUND);
    }

    private static double upgraded() {
        return upgraded(LING);
    }

    private static double upgraded(UnitType type) {
        return type.topSpeed() * SpeedRunDetector.UPGRADED_SPEED_FACTOR;
    }

    /**
     * Moves one unit along x from a start frame, one sighting a frame, by the step each frame gives; returns the last
     * frame.
     */
    private static int walk(SpeedRunDetector detector, UnitType type, int start, int frames, IntToDoubleFunction step) {
        double x = 100;
        for (int i = 0; i < frames; i++) {
            x += i == 0 ? 0 : step.applyAsDouble(i);
            detector.observe(start + i, 7, type, (int) Math.round(x), 100, 0, true);
            detector.onFrame(start + i);
        }
        return start + frames - 1;
    }

    /** Steps of nothing and of twice a speed in turn, so every window of an even length covers the speed. */
    private static IntToDoubleFunction uneven(double speed) {
        return i -> i % 2 == 0 ? 2 * speed : 0;
    }

    @Test
    void eachFollowedTypeIsHeldToItsOwnMargin() {
        assertEquals(1.25, SpeedRunDetector.speedMargin(LING), 1e-9);
        assertEquals(1.30, SpeedRunDetector.speedMargin(HYDRA), 1e-9);
        assertFalse(SpeedRunDetector.exceedsBaseSpeed(LING, LING.topSpeed() * 1.24));
        assertTrue(SpeedRunDetector.exceedsBaseSpeed(LING, LING.topSpeed() * 1.26));
        assertFalse(SpeedRunDetector.exceedsBaseSpeed(HYDRA, HYDRA.topSpeed() * 1.29));
        assertTrue(SpeedRunDetector.exceedsBaseSpeed(HYDRA, HYDRA.topSpeed() * 1.31));
        assertFalse(SpeedRunDetector.exceedsBaseSpeed(UnitType.Zerg_Mutalisk, 100));
    }

    @Test
    void anUnupgradedLingWithAFlickeringMovingFlagAndVelocityIsNeverSeen() {
        SpeedRunDetector detector = detector();
        int start = BOUND + 100;
        for (int i = 0; i < 20 * R; i++) {
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
        assertTrue(diagnostic.getWindowSpeed() < SpeedRunDetector.speedMargin(LING));
        assertTrue(diagnostic.getWindowSpeed() > 0.9);
    }

    @Test
    void anUpgradedLingIsSeenTwoWindowsAfterItStartsAndAgainstTheRunsSpan() {
        SpeedRunDetector detector = detector();
        int start = BOUND;
        for (int i = 0; i <= R; i++) {
            detector.observe(start + i, 7, LING, 100 + (int) Math.round(i * upgraded()), 100, 0, false);
            if (i < R) {
                assertNull(detector.seen(BOOST));
            }
        }

        SpeedRunDetector.Sighting seen = detector.seen(BOOST);
        assertNotNull(seen);
        assertEquals(start + R, seen.getFrame());
        assertEquals(R, seen.getRunFrames());
        assertEquals(upgraded(), seen.getMeasuredSpeed(), 0.1);
        assertEquals(BOUND, seen.getBoundFrame());
    }

    @Test
    void oneFastWindowIsNotARunButTwoWithinTheSpanAre() {
        double v = LING.topSpeed() * 0.95;
        int near = SpeedRunDetector.PAIR_SPAN_FRAMES - W;
        int far = SpeedRunDetector.PAIR_SPAN_FRAMES + 2 * W;

        SpeedRunDetector single = detector();
        walk(single, LING, BOUND, 200, i -> i <= W ? upgraded() : v);
        assertNull(single.seen(BOOST));
        assertTrue(single.diagnostic(LING).getWindowSpeed() > SpeedRunDetector.speedMargin(LING));
        assertTrue(single.diagnostic(LING).getRunSpeed() < SpeedRunDetector.speedMargin(LING));

        SpeedRunDetector within = detector();
        walk(within, LING, BOUND, 200, i -> i <= W || i > near && i <= near + W ? upgraded() : v);
        assertNotNull(within.seen(BOOST));

        SpeedRunDetector apart = detector();
        walk(apart, LING, BOUND, 200, i -> i <= W || i > far && i <= far + W ? upgraded() : v);
        assertNull(apart.seen(BOOST));
    }

    @Test
    void unevenUpgradedStepsNeverClearTheUnitAndAreSeen() {
        for (UnitType type : FOLLOWED) {
            UpgradeType upgrade = EnemySpeedTracker.upgradeFor(type);
            double u = upgraded(type);
            SpeedRunDetector stillThenDouble = detector();
            walk(stillThenDouble, type, BOUND, 20 * R, uneven(u));
            SpeedRunDetector twoStillThenTriple = detector();
            walk(twoStillThenTriple, type, BOUND, 20 * R, i -> i % 3 == 0 ? 3 * u : 0);

            for (SpeedRunDetector detector : new SpeedRunDetector[] {stillThenDouble, twoStillThenTriple}) {
                SpeedRunDetector.Diagnostic diagnostic = detector.diagnostic(type);
                assertEquals(0, diagnostic.getJumpClears());
                assertEquals(0, diagnostic.getGapClears());
                assertNotNull(detector.seen(upgrade));
                assertTrue(detector.seen(upgrade).getFrame() <= BOUND + R + 2);
                assertTrue(diagnostic.getRunSpeed() > SpeedRunDetector.speedMargin(type));
            }
            assertTrue(stillThenDouble.diagnostic(type).getSteps()[0] > 0);
            assertTrue(stillThenDouble.diagnostic(type).getSteps()[5] > 0);
        }
    }

    @Test
    void anUnupgradedLingAndHydraAtTheirMeasuredCeilingWithUnevenStepsAreNeverSeen() {
        double[] ceilings = {LING_CEILING, HYDRA_CEILING};
        for (int t = 0; t < FOLLOWED.length; t++) {
            UnitType type = FOLLOWED[t];
            double ceiling = ceilings[t];
            UpgradeType upgrade = EnemySpeedTracker.upgradeFor(type);
            SpeedRunDetector detector = new SpeedRunDetector(u -> BOUND);
            walk(detector, type, BOUND, 40 * R, uneven(type.topSpeed() * ceiling));
            SpeedRunDetector early = new SpeedRunDetector(u -> Integer.MAX_VALUE);
            walk(early, type, BOUND, 40 * R, uneven(type.topSpeed() * ceiling));

            assertNull(detector.seen(upgrade));
            assertNull(early.earlyRun(upgrade));
            SpeedRunDetector.Diagnostic diagnostic = detector.diagnostic(type);
            assertEquals(ceiling, diagnostic.getWindowSpeed(), 0.05);
            assertTrue(diagnostic.getRunSpeed() < SpeedRunDetector.speedMargin(type));
            assertEquals(0, diagnostic.getLongestRun());
            assertEquals(0, diagnostic.getJumpClears());
        }
    }

    @Test
    void aSingleMissedFrameInsideTheRunIsMeasuredOverTheFramesItSpans() {
        SpeedRunDetector detector = detector();
        int start = BOUND;
        for (int i = 0; i <= R + 1; i++) {
            if (i == 1) {
                continue;
            }
            detector.observe(start + i, 7, LING, 100 + (int) Math.round(i * upgraded()), 100, 0, true);
        }

        SpeedRunDetector.Sighting seen = detector.seen(BOOST);
        assertNotNull(seen);
        assertEquals(start + R, seen.getFrame());
        assertEquals(R, seen.getRunFrames());

        SpeedRunDetector missedAtStart = detector();
        for (int i = 0; i <= R + 1; i++) {
            if (i == 1 || i == W) {
                continue;
            }
            missedAtStart.observe(start + i, 7, LING, 100 + (int) Math.round(i * upgraded()), 100, 0, true);
        }
        assertEquals(start + R + 1, missedAtStart.seen(BOOST).getFrame());
    }

    @Test
    void aCurvingUpgradedLingIsStillSeen() {
        SpeedRunDetector detector = detector();
        double radius = 200;
        for (int i = 0; i <= R; i++) {
            double angle = i * upgraded() / radius;
            detector.observe(BOUND + i, 7, LING, 1000 + (int) Math.round(radius * Math.cos(angle)),
                    1000 + (int) Math.round(radius * Math.sin(angle)), 0, true);
        }

        assertNotNull(detector.seen(BOOST));
    }

    @Test
    void aTransportUnloadStartsTheUnitAfreshAndIsNotARun() {
        for (UnitType type : FOLLOWED) {
            UpgradeType upgrade = EnemySpeedTracker.upgradeFor(type);
            double v = type.topSpeed();
            SpeedRunDetector carried = detector();
            int start = BOUND + 100;
            for (int i = 0; i < 6 * R; i++) {
                boolean loaded = i >= 2 * R && i < 3 * R;
                if (!loaded) {
                    int jump = i >= 3 * R ? 600 : 0;
                    carried.observe(start + i, 7, type, 100 + jump + (int) Math.round(i * v), 100, 0, true);
                }
                carried.onFrame(start + i);
            }
            assertNull(carried.seen(upgrade));
            assertEquals(1, carried.diagnostic(type).getGapClears());
            assertTrue(carried.diagnostic(type).getRunSpeed() < SpeedRunDetector.speedMargin(type));

            SpeedRunDetector jumped = detector();
            walk(jumped, type, start, 6 * R, i -> i == 2 * R ? 400 : v);
            assertNull(jumped.seen(upgrade));
            assertEquals(1, jumped.diagnostic(type).getJumpClears());
            assertTrue(jumped.diagnostic(type).getRunSpeed() < SpeedRunDetector.speedMargin(type));

            assertTrue(SpeedRunDetector.impossibleStep(type, 400, 1));
            assertFalse(SpeedRunDetector.impossibleStep(type, 4 * upgraded(type), 1));
            assertFalse(SpeedRunDetector.impossibleStep(type, 6 * upgraded(type), 2));
        }
    }

    @Test
    void theStepHistogramAndTheClearsCountEachStepAndRestart() {
        SpeedRunDetector detector = detector();
        double v = LING.topSpeed();
        double[] steps = {0, 0.5 * v, 1.1 * v, 1.4 * v, 1.8 * v, 3 * v, 400};
        double x = 100;
        int frame = BOUND;
        detector.observe(frame, 7, LING, (int) Math.round(x), 100, 0, true);
        for (double step : steps) {
            x += step;
            frame++;
            detector.observe(frame, 7, LING, (int) Math.round(x), 100, 0, true);
            detector.onFrame(frame);
        }
        for (int i = 0; i <= SpeedRunDetector.MAX_SAMPLE_GAP; i++) {
            frame++;
            detector.onFrame(frame);
        }
        detector.observe(frame, 7, LING, 100, 100, 0, true);

        SpeedRunDetector.Diagnostic diagnostic = detector.diagnostic(LING);
        assertArrayEquals(new int[] {1, 1, 1, 1, 1, 2}, diagnostic.getSteps());
        assertEquals(1, diagnostic.getJumpClears());
        assertEquals(1, diagnostic.getGapClears());
    }

    @Test
    void aRunThatEndsBeforeTheBoundIsAnEarlyRunAndOneStraddlingItIsNeither() {
        SpeedRunDetector detector = detector();
        int start = BOUND - R - 1;
        for (int i = 0; i <= R; i++) {
            detector.observe(start + i, 7, LING, 100 + (int) Math.round(i * upgraded()), 100, 0, true);
        }
        assertEquals(BOUND - 1, detector.earlyRun(BOOST).getFrame());

        for (int i = R + 1; i < 2 * R + 1; i++) {
            detector.observe(start + i, 7, LING, 100 + (int) Math.round(i * upgraded()), 100, 0, true);
        }
        assertNull(detector.seen(BOOST));
        detector.observe(start + 2 * R + 1, 7, LING, 100 + (int) Math.round((2 * R + 1) * upgraded()), 100, 0, true);
        assertEquals(BOUND + R, detector.seen(BOOST).getFrame());
    }

    @Test
    void theDiagnosticsTallyTheWindowsAndStartAfreshOnAReset() {
        SpeedRunDetector detector = detector();
        int frames = 2 * SpeedRunDetector.LONG_WINDOW_FRAMES;
        for (int i = 0; i < frames; i++) {
            detector.observe(BOUND + i, 7, LING, 100 + (int) Math.round(i * upgraded()), 100, upgraded(), true);
        }

        SpeedRunDetector.Diagnostic diagnostic = detector.diagnostic(LING);
        assertEquals(frames - W, diagnostic.getWindows());
        assertEquals(frames - W, diagnostic.getLongestRun());
        assertEquals(frames, diagnostic.getVelocityFrames());
        assertEquals(0, diagnostic.getNotMovingFrames());
        assertEquals(1.5, diagnostic.getWindowSpeed(), 0.05);
        assertEquals(1.5, diagnostic.getMidWindowSpeed(), 0.05);
        assertEquals(1.5, diagnostic.getLongWindowSpeed(), 0.05);
        assertEquals(1.5, diagnostic.getRunSpeed(), 0.05);
        assertEquals(1.5 - 0.5 / W, diagnostic.getDiscountedWindowSpeed(), 0.05);
        assertEquals(frames - 1, diagnostic.getSteps()[3] + diagnostic.getSteps()[4]);

        detector.resetDiagnostic(LING, BOUND + frames);
        SpeedRunDetector.Diagnostic fresh = detector.diagnostic(LING);
        assertEquals(BOUND + frames, fresh.getFromFrame());
        assertEquals(0, fresh.getWindows());
        assertArrayEquals(new int[SpeedRunDetector.STEP_BINS.length + 1], fresh.getSteps());
        assertEquals(0, detector.diagnostic(HYDRA).getWindows());
    }
}
