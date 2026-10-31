package info.tracking;

import bwapi.UnitType;
import bwapi.UpgradeType;
import info.tracking.EnemySpeedTracker.Anchor;
import info.tracking.EnemySpeedTracker.Event;
import info.tracking.EnemySpeedTracker.Evidence;
import info.tracking.EnemySpeedTracker.Report;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnemySpeedTrackerTest {

    private static final UpgradeType BOOST = UpgradeType.Metabolic_Boost;
    private static final UpgradeType AUGMENTS = UpgradeType.Muscular_Augments;
    private static final int EXTRACTOR_ID = 900;
    private static final int W = SpeedRunDetector.MIN_RUN_FRAMES;

    private static double fast(UnitType type) {
        return type.topSpeed() * SpeedRunDetector.speedMargin(type) + 0.5;
    }

    private static int gasFrames(int gas) {
        return (int) Math.ceil(gas / EnemySpeedTracker.GAS_PER_FRAME - 1e-9);
    }

    private static int boostFloor() {
        return Math.max(UnitType.Zerg_Spawning_Pool.buildTime(),
                UnitType.Zerg_Extractor.buildTime() + gasFrames(BOOST.gasPrice())) + BOOST.upgradeTime();
    }

    /**
     * Moves one unit in a straight line at a speed for a number of consecutive frames from a start frame; a run
     * passes from the frame {@link #W} after the start.
     */
    private static void run(EnemySpeedTracker tracker, int unitId, UnitType type, int start, int frames,
                            double speed) {
        for (int i = 0; i < frames; i++) {
            tracker.observe(start + i, unitId, type, 100 + (int) Math.round(i * speed), 100, speed, true);
            tracker.onFrame(start + i);
        }
    }

    private static Report only(List<Report> reports, Event event) {
        for (Report report : reports) {
            if (report.getEvent() == event) {
                return report;
            }
        }
        return null;
    }

    @Test
    void sightingsThatJumpBackEveryWindowNeverMakeTheUpgradeSeen() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        double speed = fast(UnitType.Zerg_Zergling);
        int frame = 10000;
        for (int unit = 0; unit < 200; unit++) {
            for (int i = 0; i < W; i++) {
                tracker.observe(frame, unit, UnitType.Zerg_Zergling, 100 + (int) (i * speed), 100, speed, true);
                tracker.observe(frame, unit + 1000, UnitType.Zerg_Zergling, 500, 500, speed, true);
                tracker.onFrame(frame);
                frame++;
            }
            tracker.observe(frame, unit, UnitType.Zerg_Zergling, 0, 0, 1.0, true);
            tracker.onFrame(frame);
            frame++;
        }

        assertEquals(Evidence.PLAUSIBLE, tracker.evidence(BOOST, frame));
        assertEquals(-1, tracker.seenFrame(BOOST));
    }

    @Test
    void aWindowAfterTheEarliestFinishMakesItSeenAndIsLogged() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        int start = boostFloor() + 100;

        run(tracker, 7, UnitType.Zerg_Zergling, start, W + 1, fast(UnitType.Zerg_Zergling));

        int last = start + W;
        assertEquals(last, tracker.seenFrame(BOOST));
        assertEquals(Evidence.SEEN, tracker.evidence(BOOST, last));
        Report seen = only(tracker.announce(last), Event.SEEN);
        assertEquals(W, seen.getRunFrames());
        assertEquals(UnitType.Zerg_Zergling.topSpeed(), seen.getTopSpeed(), 1e-9);
        assertTrue(seen.getMeasuredSpeed() > UnitType.Zerg_Zergling.topSpeed()
                * SpeedRunDetector.speedMargin(UnitType.Zerg_Zergling));
        assertEquals(boostFloor(), seen.getBoundFrame());
    }

    @Test
    void theEnemyTallyIsHandedOverOnceAtTheSoundEarliestFinishAndStartsAfresh() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        int bound = tracker.soundEarliestFinishFrame(BOOST);
        run(tracker, 7, UnitType.Zerg_Zergling, bound - 3 * W, 2 * W, UnitType.Zerg_Zergling.topSpeed());

        assertNull(tracker.soundBoundCrossed(BOOST, bound - 1));
        SpeedRunDetector.Diagnostic before = tracker.soundBoundCrossed(BOOST, bound);
        assertEquals(0, before.getFromFrame());
        assertTrue(before.getWindows() > 0);
        assertNull(tracker.soundBoundCrossed(BOOST, bound + 1));
        assertNull(tracker.soundBoundCrossed(UpgradeType.Adrenal_Glands, bound));
        assertEquals(bound, tracker.diagnostic(UnitType.Zerg_Zergling).getFromFrame());
        assertEquals(0, tracker.diagnostic(UnitType.Zerg_Zergling).getWindows());
        assertEquals(0, tracker.diagnostic(UnitType.Zerg_Hydralisk).getFromFrame());
    }

    @Test
    void aRunOneFrameShortOfAWindowIsNotSeen() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();

        run(tracker, 7, UnitType.Zerg_Zergling, boostFloor(), W, fast(UnitType.Zerg_Zergling));

        assertEquals(-1, tracker.seenFrame(BOOST));
    }

    @Test
    void aFastVelocityThatCoversNoGroundIsNotSeen() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        int start = boostFloor();
        for (int i = 0; i < W * 3; i++) {
            tracker.observe(start + i, 7, UnitType.Zerg_Zergling, 100 + i % 2, 100, fast(UnitType.Zerg_Zergling),
                    true);
            tracker.onFrame(start + i);
        }

        assertEquals(-1, tracker.seenFrame(BOOST));
    }

    @Test
    void aUnitUnseenForLongerThanTheGapStartsAfreshButItsMovingFlagDoesNotMatter() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        int start = boostFloor();
        double speed = fast(UnitType.Zerg_Zergling);
        int half = W / 2 + 1;
        int gap = SpeedRunDetector.MAX_SAMPLE_GAP + 1;

        run(tracker, 7, UnitType.Zerg_Zergling, start, half, speed);
        int resumed = start + half - 1 + gap;
        for (int i = 0; i < half; i++) {
            tracker.observe(resumed + i, 7, UnitType.Zerg_Zergling,
                    100 + (int) Math.round((resumed + i - start) * speed), 100, speed, true);
            tracker.onFrame(resumed + i);
        }
        assertEquals(-1, tracker.seenFrame(BOOST));

        int next = resumed + 2 * W;
        for (int i = 0; i <= W; i++) {
            tracker.observe(next + i, 8, UnitType.Zerg_Zergling, 100 + (int) Math.round(i * speed), 100, 0.0, false);
            tracker.onFrame(next + i);
        }
        assertEquals(next + W, tracker.seenFrame(BOOST));
    }

    @Test
    void aWindowBeforeTheEarliestFinishIsRefusedAndReportedOnceThenALaterWindowIsSeen() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        int early = boostFloor() - 500;

        run(tracker, 7, UnitType.Zerg_Zergling, early, W + 1, fast(UnitType.Zerg_Zergling));

        assertEquals(-1, tracker.seenFrame(BOOST));
        assertEquals(Evidence.NONE, tracker.evidence(BOOST, early + W));
        Report refused = only(tracker.announce(early + W), Event.EARLY_RUN);
        assertEquals(boostFloor(), refused.getBoundFrame());
        assertNull(only(tracker.announce(early + W + 1), Event.EARLY_RUN));

        run(tracker, 8, UnitType.Zerg_Zergling, boostFloor(), W + 1, fast(UnitType.Zerg_Zergling));

        assertEquals(boostFloor() + W, tracker.seenFrame(BOOST));
    }

    @Test
    void withNoExtractorSeenTheEarliestFinishIsTheGameStartFloor() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();

        assertEquals(boostFloor(), tracker.earliestFinishFrame(BOOST));
        assertEquals(Anchor.FLOOR, tracker.earliestFinishAnchor(BOOST));
        assertEquals(Evidence.NONE, tracker.evidence(BOOST, boostFloor() - 1));
        assertEquals(Evidence.PLAUSIBLE, tracker.evidence(BOOST, boostFloor()));
    }

    @Test
    void anExtractorSeenUnderConstructionAnchorsTheEarliestFinishOnItsCompletion() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();

        tracker.observeStructure(1500, EXTRACTOR_ID, UnitType.Zerg_Extractor, false, 300);

        int expected = 1800 + gasFrames(BOOST.gasPrice()) + BOOST.upgradeTime();
        assertEquals(expected, tracker.earliestFinishFrame(BOOST));
        assertEquals(Anchor.EXTRACTOR, tracker.earliestFinishAnchor(BOOST));
        Report plausible = only(tracker.announce(expected), Event.PLAUSIBLE);
        assertEquals(expected, plausible.getBoundFrame());
        assertEquals(Anchor.EXTRACTOR, plausible.getAnchor());
        assertTrue(tracker.announce(expected + 1).isEmpty());
    }

    @Test
    void anUnchangedTrustedGasReadingWritesAPlausibleRowOnlyEveryFewHundredFrames() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        assertEquals(Anchor.FLOOR, only(tracker.announce(boostFloor()), Event.PLAUSIBLE).getAnchor());
        int start = boostFloor() + 1;
        tracker.observeStructure(start, EXTRACTOR_ID, UnitType.Zerg_Extractor, true, 0);
        tracker.observeGasReading(start, EXTRACTOR_ID, 8);
        int rows = 0;
        int anchorChanges = 0;
        Anchor last = Anchor.FLOOR;
        for (int frame = start; frame < start + 1000; frame++) {
            tracker.observeGasReading(frame, EXTRACTOR_ID, frame == start ? 8 : 16);
            Report plausible = only(tracker.announce(frame), Event.PLAUSIBLE);
            if (plausible != null) {
                rows++;
                if (plausible.getAnchor() != last) {
                    anchorChanges++;
                    last = plausible.getAnchor();
                }
            }
        }

        assertEquals(Anchor.GAS_READING, tracker.earliestFinishAnchor(BOOST));
        assertEquals(Anchor.GAS_READING, last);
        assertTrue(anchorChanges >= 1);
        assertTrue(rows > anchorChanges);
        assertTrue(rows < 15);
    }

    @Test
    void aPlausibleRowIsReportedAgainWheneverTheBoundOrItsAnchorChanges() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        assertNull(only(tracker.announce(boostFloor() - 1), Event.PLAUSIBLE));
        assertEquals(Anchor.FLOOR, only(tracker.announce(boostFloor()), Event.PLAUSIBLE).getAnchor());
        assertNull(only(tracker.announce(boostFloor() + 1), Event.PLAUSIBLE));

        tracker.observeStructure(3000, EXTRACTOR_ID, UnitType.Zerg_Extractor, true, 0);
        Report moved = only(tracker.announce(3000), Event.PLAUSIBLE);

        assertEquals(Anchor.EXTRACTOR, moved.getAnchor());
        assertEquals(tracker.earliestFinishFrame(BOOST), moved.getBoundFrame());
        assertTrue(moved.getBoundFrame() > 3000);
        assertNull(only(tracker.announce(3001), Event.PLAUSIBLE));
    }

    @Test
    void anExtractorFirstSeenFinishedDelaysThePlausibleBoundButNotTheSoundBoundARunIsHeldTo() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();

        tracker.observeStructure(3000, EXTRACTOR_ID, UnitType.Zerg_Extractor, true, 0);
        tracker.observeStructure(3050, EXTRACTOR_ID, UnitType.Zerg_Extractor, true, 0);

        assertEquals(3000 + gasFrames(BOOST.gasPrice()) + BOOST.upgradeTime(), tracker.earliestFinishFrame(BOOST));
        assertEquals(boostFloor(), tracker.soundEarliestFinishFrame(BOOST));

        run(tracker, 7, UnitType.Zerg_Zergling, 2600 - W - 1, W + 1, fast(UnitType.Zerg_Zergling));
        assertEquals(-1, tracker.seenFrame(BOOST));
        run(tracker, 8, UnitType.Zerg_Zergling, 3100, W + 1, fast(UnitType.Zerg_Zergling));
        int last = 3100 + W;

        assertEquals(last, tracker.seenFrame(BOOST));
        List<Report> reports = tracker.announce(last);
        assertEquals(boostFloor(), only(reports, Event.SEEN).getBoundFrame());
        assertEquals(boostFloor(), only(reports, Event.EARLY_RUN).getBoundFrame());
    }

    @Test
    void aRunThatBeganBeforeTheBoundIsSeenOnlyAWholeWindowAfterIt() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        int bound = tracker.soundEarliestFinishFrame(BOOST);
        int start = bound - 200;

        run(tracker, 7, UnitType.Zerg_Zergling, start, bound + W - start, fast(UnitType.Zerg_Zergling));
        assertEquals(-1, tracker.seenFrame(BOOST));

        int last = bound + W;
        double speed = fast(UnitType.Zerg_Zergling);
        tracker.observe(last, 7, UnitType.Zerg_Zergling, 100 + (int) Math.round((last - start) * speed), 100, speed,
                true);

        assertEquals(last, tracker.seenFrame(BOOST));
        assertTrue(only(tracker.announce(last), Event.EARLY_RUN) != null);
    }

    @Test
    void aGasReadingRulesOutEveryFrameBeforeTheRestCanBeMined() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        tracker.observeStructure(2500, EXTRACTOR_ID, UnitType.Zerg_Extractor, true, 0);

        tracker.observeGasReading(2500, EXTRACTOR_ID, 16);
        tracker.observeGasReading(3000, EXTRACTOR_ID, 40);

        int expected = 3000 + gasFrames(BOOST.gasPrice() - 40) + BOOST.upgradeTime();
        assertEquals(expected, tracker.earliestFinishFrame(BOOST));
        assertEquals(Anchor.GAS_READING, tracker.earliestFinishAnchor(BOOST));
    }

    @Test
    void aTrustedGasReadingAboveTheCostLeavesTheFloorAndDropsTheSeenFrameAnchor() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        tracker.observeStructure(2500, EXTRACTOR_ID, UnitType.Zerg_Extractor, true, 0);

        tracker.observeGasReading(2500, EXTRACTOR_ID, 296);
        tracker.observeGasReading(2530, EXTRACTOR_ID, 304);

        assertEquals(boostFloor(), tracker.earliestFinishFrame(BOOST));
    }

    @Test
    void aGasReadingIsTrustedOnlyOnceTheMinedGasHasChanged() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        tracker.observeStructure(2500, EXTRACTOR_ID, UnitType.Zerg_Extractor, true, 0);
        int seenFrameBound = 2500 + gasFrames(BOOST.gasPrice()) + BOOST.upgradeTime();

        for (int frame = 2500; frame < 2600; frame++) {
            tracker.observeGasReading(frame, EXTRACTOR_ID, 0);
        }

        assertEquals(Anchor.EXTRACTOR, tracker.earliestFinishAnchor(BOOST));
        assertEquals(seenFrameBound, tracker.earliestFinishFrame(BOOST));

        tracker.observeGasReading(2600, EXTRACTOR_ID, 8);

        assertEquals(Anchor.GAS_READING, tracker.earliestFinishAnchor(BOOST));
        int expected = Math.max(2599 + gasFrames(BOOST.gasPrice()), 2600 + gasFrames(BOOST.gasPrice() - 8))
                + BOOST.upgradeTime();
        assertEquals(expected, tracker.earliestFinishFrame(BOOST));
    }

    @Test
    void anExtractorDestroyedBeforeItWasReadyNeverAnchors() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        tracker.observeStructure(1500, EXTRACTOR_ID, UnitType.Zerg_Extractor, false, 500);
        tracker.observeStructure(1510, EXTRACTOR_ID + 1, UnitType.Zerg_Extractor, false, 590);

        tracker.onUnitDestroy(EXTRACTOR_ID, 1520);

        assertEquals(2100 + gasFrames(BOOST.gasPrice()) + BOOST.upgradeTime(), tracker.earliestFinishFrame(BOOST));
        tracker.onUnitDestroy(EXTRACTOR_ID + 1, 2200);
        assertEquals(2100 + gasFrames(BOOST.gasPrice()) + BOOST.upgradeTime(), tracker.earliestFinishFrame(BOOST));
    }

    @Test
    void aLairBegunBeforeBothGasCostsCouldBeMinedPutsItsGasFirst() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        tracker.observeStructure(1500, EXTRACTOR_ID, UnitType.Zerg_Extractor, false, 300);
        int both = UnitType.Zerg_Lair.gasPrice() + BOOST.gasPrice();
        int bothReady = 1800 + gasFrames(both);

        tracker.observeStructure(bothReady - 10, 50, UnitType.Zerg_Lair, false,
                UnitType.Zerg_Lair.buildTime() - 20);

        assertEquals(bothReady + BOOST.upgradeTime(), tracker.earliestFinishFrame(BOOST));
    }

    @Test
    void aLairBegunAfterBothGasCostsCouldBeMinedChangesNothing() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        tracker.observeStructure(1500, EXTRACTOR_ID, UnitType.Zerg_Extractor, false, 300);
        int bothReady = 1800 + gasFrames(UnitType.Zerg_Lair.gasPrice() + BOOST.gasPrice());

        tracker.observeStructure(bothReady + UnitType.Zerg_Lair.buildTime() + 100, 50, UnitType.Zerg_Lair, true, 0);

        assertEquals(1800 + gasFrames(BOOST.gasPrice()) + BOOST.upgradeTime(), tracker.earliestFinishFrame(BOOST));
    }

    @Test
    void theLairGasRuleNeedsALairStartBeforeBothCouldBeMined() {
        assertTrue(EnemySpeedTracker.lairGasFirst(1000, 1001));
        assertFalse(EnemySpeedTracker.lairGasFirst(1001, 1001));
        assertFalse(EnemySpeedTracker.lairGasFirst(-1, 1001));
    }

    @Test
    void muscularAugmentsWaitsForTheDenAndItsGas() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        tracker.observeStructure(1500, EXTRACTOR_ID, UnitType.Zerg_Extractor, false, 300);
        int gas = UnitType.Zerg_Hydralisk_Den.gasPrice() + AUGMENTS.gasPrice();
        int denReady = Math.max(UnitType.Zerg_Spawning_Pool.buildTime(),
                1800 + gasFrames(UnitType.Zerg_Hydralisk_Den.gasPrice())) + UnitType.Zerg_Hydralisk_Den.buildTime();

        int expected = Math.max(1800 + gasFrames(gas), denReady) + AUGMENTS.upgradeTime();
        assertEquals(expected, tracker.earliestFinishFrame(AUGMENTS));
        tracker.observeStructure(4000, 50, UnitType.Zerg_Lair, true, 0);
        assertEquals(expected, tracker.earliestFinishFrame(AUGMENTS));

        tracker.observeStructure(5000, 60, UnitType.Zerg_Hydralisk_Den, false, 400);
        assertEquals(5400 + AUGMENTS.upgradeTime(), tracker.earliestFinishFrame(AUGMENTS));
        assertEquals(Anchor.RESEARCHER, tracker.earliestFinishAnchor(AUGMENTS));
    }

    @Test
    void muscularAugmentsSeenWaitsForItsOwnBoundAndLeavesMetabolicBoostAlone() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        int augmentsFloor = tracker.earliestFinishFrame(AUGMENTS);

        run(tracker, 3, UnitType.Zerg_Hydralisk, augmentsFloor - 200, W + 1, fast(UnitType.Zerg_Hydralisk));
        assertEquals(-1, tracker.seenFrame(AUGMENTS));

        run(tracker, 3, UnitType.Zerg_Hydralisk, augmentsFloor, W + 1, fast(UnitType.Zerg_Hydralisk));
        assertEquals(Evidence.SEEN, tracker.evidence(AUGMENTS, augmentsFloor + W));
        assertEquals(-1, tracker.seenFrame(BOOST));
    }

    @Test
    void twoExtractorsMineTogetherOnceBothAreReady() {
        int one = EnemySpeedTracker.earliestGasFrame(Collections.singletonList(1000), 100);
        int two = EnemySpeedTracker.earliestGasFrame(Arrays.asList(1000, 1000), 100);
        int late = EnemySpeedTracker.earliestGasFrame(Arrays.asList(1000, 100000), 100);

        assertEquals(1000 + gasFrames(100), one);
        assertEquals(1000 + gasFrames(50), two);
        assertEquals(one, late);
        assertEquals(-1, EnemySpeedTracker.earliestGasFrame(Collections.emptyList(), 100));
    }

    @Test
    void onlyReadingsBelowTheCostBoundTheGas() {
        NavigableMap<Integer, Integer> readings = new TreeMap<>();
        readings.put(8, 1900);
        readings.put(96, 2400);
        readings.put(104, 2450);

        assertEquals(2400 + gasFrames(4), EnemySpeedTracker.readingBound(readings, 100));
        assertEquals(-1, EnemySpeedTracker.readingBound(readings, 8));
    }

    @Test
    void seenOutranksPlausible() {
        assertEquals(Evidence.SEEN, EnemySpeedTracker.classify(50, 40, 60));
        assertEquals(Evidence.PLAUSIBLE, EnemySpeedTracker.classify(70, 40, 60));
        assertEquals(Evidence.NONE, EnemySpeedTracker.classify(-1, 61, 60));
    }

    @Test
    void anUpgradeNotFollowedReadsNone() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();

        assertEquals(Evidence.NONE, tracker.evidence(UpgradeType.Adrenal_Glands, 100000));
        assertEquals(-1, tracker.plausibleFrame(UpgradeType.Adrenal_Glands));
    }

    @Test
    void theUpgradeAndUnitTypesPairUp() {
        assertEquals(BOOST, EnemySpeedTracker.upgradeFor(UnitType.Zerg_Zergling));
        assertEquals(AUGMENTS, EnemySpeedTracker.upgradeFor(UnitType.Zerg_Hydralisk));
        assertEquals(UnitType.Zerg_Zergling, EnemySpeedTracker.unitFor(BOOST));
        assertEquals(UnitType.Zerg_Hydralisk, EnemySpeedTracker.unitFor(AUGMENTS));
        assertNull(EnemySpeedTracker.upgradeFor(UnitType.Zerg_Drone));
        assertTrue(EnemySpeedTracker.followsStructure(UnitType.Zerg_Extractor));
        assertFalse(EnemySpeedTracker.followsStructure(UnitType.Zerg_Hatchery));
    }
}
