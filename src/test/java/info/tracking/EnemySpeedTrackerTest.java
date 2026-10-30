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

    private static double fast(UnitType type) {
        return type.topSpeed() * EnemySpeedTracker.SEEN_SPEED_MARGIN + 0.5;
    }

    private static int gasFrames(int gas) {
        return (int) Math.ceil(gas / EnemySpeedTracker.GAS_PER_FRAME - 1e-9);
    }

    private static int boostFloor() {
        return Math.max(UnitType.Zerg_Spawning_Pool.buildTime(),
                UnitType.Zerg_Extractor.buildTime() + gasFrames(BOOST.gasPrice())) + BOOST.upgradeTime();
    }

    /**
     * Moves one unit in a straight line at a speed for a number of consecutive frames from a start frame.
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
    void anUnupgradedLingAtTopSpeedIsNotFast() {
        assertFalse(EnemySpeedTracker.exceedsBaseSpeed(UnitType.Zerg_Zergling, UnitType.Zerg_Zergling.topSpeed()));
        assertTrue(EnemySpeedTracker.exceedsBaseSpeed(UnitType.Zerg_Zergling, fast(UnitType.Zerg_Zergling)));
        assertFalse(EnemySpeedTracker.exceedsBaseSpeed(UnitType.Zerg_Mutalisk, 100));
    }

    @Test
    void spikeFramesOnManyUnitsNeverMakeTheUpgradeSeen() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        double speed = fast(UnitType.Zerg_Zergling);
        int frame = 10000;
        for (int unit = 0; unit < 200; unit++) {
            for (int i = 0; i < EnemySpeedTracker.SEEN_RUN_FRAMES - 1; i++) {
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
    void aSustainedRunOnOneUnitAfterTheEarliestFinishMakesItSeenAndIsLogged() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        int start = boostFloor() + 100;

        run(tracker, 7, UnitType.Zerg_Zergling, start, EnemySpeedTracker.SEEN_RUN_FRAMES,
                fast(UnitType.Zerg_Zergling));

        int last = start + EnemySpeedTracker.SEEN_RUN_FRAMES - 1;
        assertEquals(last, tracker.seenFrame(BOOST));
        assertEquals(Evidence.SEEN, tracker.evidence(BOOST, last));
        Report seen = only(tracker.announce(last), Event.SEEN);
        assertEquals(EnemySpeedTracker.SEEN_RUN_FRAMES, seen.getRunFrames());
        assertEquals(UnitType.Zerg_Zergling.topSpeed(), seen.getTopSpeed(), 1e-9);
        assertTrue(seen.getMeasuredSpeed() > UnitType.Zerg_Zergling.topSpeed() * EnemySpeedTracker.SEEN_SPEED_MARGIN);
        assertEquals(boostFloor(), seen.getBoundFrame());
    }

    @Test
    void aRunOneFrameShortIsNotSeen() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();

        run(tracker, 7, UnitType.Zerg_Zergling, boostFloor(), EnemySpeedTracker.SEEN_RUN_FRAMES - 1,
                fast(UnitType.Zerg_Zergling));

        assertEquals(-1, tracker.seenFrame(BOOST));
    }

    @Test
    void aFastVelocityThatCoversNoGroundIsNotSeen() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        int start = boostFloor();
        for (int i = 0; i < EnemySpeedTracker.SEEN_RUN_FRAMES * 3; i++) {
            tracker.observe(start + i, 7, UnitType.Zerg_Zergling, 100 + i % 2, 100, fast(UnitType.Zerg_Zergling),
                    true);
            tracker.onFrame(start + i);
        }

        assertEquals(-1, tracker.seenFrame(BOOST));
    }

    @Test
    void aFrameMissedOrNotUnderItsOwnPowerEndsTheRun() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        int start = boostFloor();
        double speed = fast(UnitType.Zerg_Zergling);
        int half = EnemySpeedTracker.SEEN_RUN_FRAMES / 2 + 1;

        run(tracker, 7, UnitType.Zerg_Zergling, start, half, speed);
        tracker.onFrame(start + half);
        run(tracker, 7, UnitType.Zerg_Zergling, start + half + 1, half, speed);
        int next = start + 2 * half + 1;
        tracker.observe(next, 7, UnitType.Zerg_Zergling, 0, 0, speed, false);
        tracker.onFrame(next);
        run(tracker, 7, UnitType.Zerg_Zergling, next + 1, half, speed);

        assertEquals(-1, tracker.seenFrame(BOOST));
    }

    @Test
    void aRunBeforeTheEarliestFinishIsRefusedAndReportedOnceThenALaterRunIsSeen() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        int early = boostFloor() - 500;

        run(tracker, 7, UnitType.Zerg_Zergling, early, EnemySpeedTracker.SEEN_RUN_FRAMES,
                fast(UnitType.Zerg_Zergling));

        assertEquals(-1, tracker.seenFrame(BOOST));
        assertEquals(Evidence.NONE, tracker.evidence(BOOST, early + EnemySpeedTracker.SEEN_RUN_FRAMES));
        Report refused = only(tracker.announce(early + EnemySpeedTracker.SEEN_RUN_FRAMES), Event.EARLY_RUN);
        assertEquals(boostFloor(), refused.getBoundFrame());
        assertNull(only(tracker.announce(early + EnemySpeedTracker.SEEN_RUN_FRAMES + 1), Event.EARLY_RUN));

        run(tracker, 8, UnitType.Zerg_Zergling, boostFloor(), EnemySpeedTracker.SEEN_RUN_FRAMES,
                fast(UnitType.Zerg_Zergling));

        assertEquals(boostFloor() + EnemySpeedTracker.SEEN_RUN_FRAMES - 1, tracker.seenFrame(BOOST));
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
    void anExtractorFirstSeenFinishedIsReadyTheFrameItWasSeen() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();

        tracker.observeStructure(2500, EXTRACTOR_ID, UnitType.Zerg_Extractor, true, 0);
        tracker.observeStructure(2600, EXTRACTOR_ID, UnitType.Zerg_Extractor, true, 0);

        assertEquals(2500 + gasFrames(BOOST.gasPrice()) + BOOST.upgradeTime(), tracker.earliestFinishFrame(BOOST));
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
    void aGasReadingAboveTheCostLeavesTheFloorAndDropsTheSeenFrameAnchor() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        tracker.observeStructure(2500, EXTRACTOR_ID, UnitType.Zerg_Extractor, true, 0);

        tracker.observeGasReading(2500, EXTRACTOR_ID, 300);

        assertEquals(boostFloor(), tracker.earliestFinishFrame(BOOST));
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
    void muscularAugmentsWaitsForTheLairTheDenAndAllTheirGas() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        tracker.observeStructure(1500, EXTRACTOR_ID, UnitType.Zerg_Extractor, false, 300);
        int gas = UnitType.Zerg_Lair.gasPrice() + UnitType.Zerg_Hydralisk_Den.gasPrice() + AUGMENTS.gasPrice();
        int lairReady = Math.max(UnitType.Zerg_Spawning_Pool.buildTime(),
                1800 + gasFrames(UnitType.Zerg_Lair.gasPrice())) + UnitType.Zerg_Lair.buildTime();

        int expected = Math.max(1800 + gasFrames(gas), lairReady) + AUGMENTS.upgradeTime();
        assertEquals(expected, tracker.earliestFinishFrame(AUGMENTS));

        tracker.observeStructure(5000, 60, UnitType.Zerg_Hydralisk_Den, false, 400);
        assertEquals(5400 + AUGMENTS.upgradeTime(), tracker.earliestFinishFrame(AUGMENTS));
        assertEquals(Anchor.RESEARCHER, tracker.earliestFinishAnchor(AUGMENTS));
    }

    @Test
    void muscularAugmentsSeenWaitsForItsOwnBoundAndLeavesMetabolicBoostAlone() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        int augmentsFloor = tracker.earliestFinishFrame(AUGMENTS);

        run(tracker, 3, UnitType.Zerg_Hydralisk, augmentsFloor - 200, EnemySpeedTracker.SEEN_RUN_FRAMES,
                fast(UnitType.Zerg_Hydralisk));
        assertEquals(-1, tracker.seenFrame(AUGMENTS));

        run(tracker, 3, UnitType.Zerg_Hydralisk, augmentsFloor, EnemySpeedTracker.SEEN_RUN_FRAMES,
                fast(UnitType.Zerg_Hydralisk));
        assertEquals(Evidence.SEEN, tracker.evidence(AUGMENTS, augmentsFloor + EnemySpeedTracker.SEEN_RUN_FRAMES));
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
