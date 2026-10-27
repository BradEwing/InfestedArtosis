package info.tracking;

import bwapi.UnitType;
import bwapi.UpgradeType;
import info.tracking.EnemySpeedTracker.Evidence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnemySpeedTrackerTest {

    private static double fastLing() {
        return UnitType.Zerg_Zergling.topSpeed() * EnemySpeedTracker.SEEN_SPEED_MARGIN + 0.5;
    }

    private static double fastHydra() {
        return UnitType.Zerg_Hydralisk.topSpeed() * EnemySpeedTracker.SEEN_SPEED_MARGIN + 0.5;
    }

    @Test
    void anUnupgradedLingAtTopSpeedIsNotFast() {
        assertFalse(EnemySpeedTracker.exceedsBaseSpeed(UnitType.Zerg_Zergling, UnitType.Zerg_Zergling.topSpeed()));
    }

    @Test
    void aLingAboveTheMarginIsFast() {
        assertTrue(EnemySpeedTracker.exceedsBaseSpeed(UnitType.Zerg_Zergling, fastLing()));
    }

    @Test
    void aTypeNotFollowedIsNeverFast() {
        assertFalse(EnemySpeedTracker.exceedsBaseSpeed(UnitType.Zerg_Mutalisk, 100));
    }

    @Test
    void fastLingsAreSeenOnlyAfterEnoughFrames() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();

        for (int frame = 100; frame < 100 + EnemySpeedTracker.SEEN_CONFIRMATIONS - 1; frame++) {
            tracker.observe(frame, UnitType.Zerg_Zergling, fastLing());
        }
        assertEquals(Evidence.NONE, tracker.evidence(UpgradeType.Metabolic_Boost, 500));

        tracker.observe(200, UnitType.Zerg_Zergling, fastLing());
        assertEquals(Evidence.SEEN, tracker.evidence(UpgradeType.Metabolic_Boost, 200));
        assertEquals(200, tracker.seenFrame(UpgradeType.Metabolic_Boost));
    }

    @Test
    void manyFastLingsOnOneFrameCountOnce() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();

        for (int i = 0; i < 20; i++) {
            tracker.observe(100, UnitType.Zerg_Zergling, fastLing());
        }

        assertEquals(Evidence.NONE, tracker.evidence(UpgradeType.Metabolic_Boost, 100));
    }

    @Test
    void slowLingsAreNeverSeenWithTheUpgrade() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();

        for (int frame = 0; frame < 1000; frame++) {
            tracker.observe(frame, UnitType.Zerg_Zergling, UnitType.Zerg_Zergling.topSpeed());
        }

        assertEquals(Evidence.NONE, tracker.evidence(UpgradeType.Metabolic_Boost, 1000));
    }

    @Test
    void hydrasSeenFastGiveMuscularAugmentsAndLeaveMetabolicBoostAlone() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();

        for (int frame = 0; frame < EnemySpeedTracker.SEEN_CONFIRMATIONS; frame++) {
            tracker.observe(frame, UnitType.Zerg_Hydralisk, fastHydra());
        }

        assertEquals(Evidence.SEEN, tracker.evidence(UpgradeType.Muscular_Augments, 10));
        assertEquals(Evidence.NONE, tracker.evidence(UpgradeType.Metabolic_Boost, 10));
    }

    @Test
    void seenIsPermanent() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        for (int frame = 0; frame < EnemySpeedTracker.SEEN_CONFIRMATIONS; frame++) {
            tracker.observe(frame, UnitType.Zerg_Zergling, fastLing());
        }

        assertEquals(Evidence.SEEN, tracker.evidence(UpgradeType.Metabolic_Boost, 100000));
        assertTrue(tracker.hasOrPlausiblyHas(UpgradeType.Metabolic_Boost, 100000));
    }

    @Test
    void theEarliestFinishAddsTheGasTheResearchTimeAndWaitsForTheResearcher() {
        int gasFrames = EnemySpeedTracker.gasFrames(UpgradeType.Metabolic_Boost);
        int research = UpgradeType.Metabolic_Boost.upgradeTime();

        assertEquals(1000 + gasFrames + research,
                EnemySpeedTracker.earliestFinishFrame(UpgradeType.Metabolic_Boost, 1000, 0));
        assertEquals(5000 + research,
                EnemySpeedTracker.earliestFinishFrame(UpgradeType.Metabolic_Boost, 1000, 5000));
    }

    @Test
    void theGasFramesComeFromThreeWorkersMiningTheUpgradeCost() {
        int gasFrames = EnemySpeedTracker.gasFrames(UpgradeType.Metabolic_Boost);

        assertTrue(gasFrames * info.ResourceCount.GAS_PER_FRAME_PER_WORKER * EnemySpeedTracker.GAS_WORKERS
                >= UpgradeType.Metabolic_Boost.gasPrice());
        assertTrue((gasFrames - 1) * info.ResourceCount.GAS_PER_FRAME_PER_WORKER * EnemySpeedTracker.GAS_WORKERS
                < UpgradeType.Metabolic_Boost.gasPrice());
    }

    @Test
    void noGasBuildingMeansNoTimingEstimate() {
        assertEquals(-1, EnemySpeedTracker.earliestFinishFrame(UpgradeType.Metabolic_Boost, -1, 0));
        assertEquals(-1, EnemySpeedTracker.earliestFinishFrame(UpgradeType.Muscular_Augments, 0, -1));
    }

    @Test
    void theUpgradeIsPlausibleOnceTheTimingAllowsItAndNotBefore() {
        int finish = EnemySpeedTracker.earliestFinishFrame(UpgradeType.Metabolic_Boost, 1000, 0);

        assertEquals(Evidence.NONE, EnemySpeedTracker.classify(-1, finish, finish - 1));
        assertEquals(Evidence.PLAUSIBLE, EnemySpeedTracker.classify(-1, finish, finish));
        assertEquals(Evidence.NONE, EnemySpeedTracker.classify(-1, -1, 100000));
    }

    @Test
    void seenOutranksPlausible() {
        assertEquals(Evidence.SEEN, EnemySpeedTracker.classify(50, 40, 60));
    }

    @Test
    void anUpgradeNotFollowedReadsNone() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();

        assertEquals(Evidence.NONE, tracker.evidence(UpgradeType.Adrenal_Glands, 100000));
    }

    @Test
    void announceReportsEachRiseOnce() {
        EnemySpeedTracker tracker = new EnemySpeedTracker();
        assertNull(tracker.announce(10, UpgradeType.Metabolic_Boost));

        for (int frame = 0; frame < EnemySpeedTracker.SEEN_CONFIRMATIONS; frame++) {
            tracker.observe(frame + 20, UnitType.Zerg_Zergling, fastLing());
        }

        assertEquals(Evidence.SEEN, tracker.announce(30, UpgradeType.Metabolic_Boost));
        assertNull(tracker.announce(31, UpgradeType.Metabolic_Boost));
    }

    @Test
    void theUpgradeAndUnitTypesPairUp() {
        assertEquals(UpgradeType.Metabolic_Boost, EnemySpeedTracker.upgradeFor(UnitType.Zerg_Zergling));
        assertEquals(UpgradeType.Muscular_Augments, EnemySpeedTracker.upgradeFor(UnitType.Zerg_Hydralisk));
        assertEquals(UnitType.Zerg_Zergling, EnemySpeedTracker.unitFor(UpgradeType.Metabolic_Boost));
        assertEquals(UnitType.Zerg_Hydralisk, EnemySpeedTracker.unitFor(UpgradeType.Muscular_Augments));
        assertNull(EnemySpeedTracker.upgradeFor(UnitType.Zerg_Drone));
    }
}
