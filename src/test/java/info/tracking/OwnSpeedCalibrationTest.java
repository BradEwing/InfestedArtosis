package info.tracking;

import bwapi.UnitType;
import bwapi.UpgradeType;
import info.tracking.EnemySpeedTracker.Event;
import info.tracking.EnemySpeedTracker.Report;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnSpeedCalibrationTest {

    private static final UnitType LING = UnitType.Zerg_Zergling;
    private static final int W = SpeedRunDetector.MIN_RUN_FRAMES;

    private static void run(OwnSpeedCalibration calibration, int unitId, int start, double speed) {
        for (int i = 0; i <= W; i++) {
            calibration.observe(start + i, unitId, LING, 100 + (int) Math.round(i * speed), 100, speed, true);
            calibration.onFrame(start + i);
        }
    }

    @Test
    void aRunBeforeOurUpgradeIsAnEarlyRunAndTheFirstAfterItIsSeenAgainstOurFinish() {
        OwnSpeedCalibration calibration = new OwnSpeedCalibration();
        double fast = LING.topSpeed() * SpeedRunDetector.speedMargin(LING) + 0.5;

        run(calibration, 7, 2000, fast);
        List<Report> early = calibration.announce(2000 + W);
        assertEquals(1, early.size());
        assertEquals(Event.EARLY_RUN, early.get(0).getEvent());
        assertEquals(-1, early.get(0).getBoundFrame());
        assertNull(early.get(0).getAnchor());

        SpeedRunDetector.Diagnostic before = calibration.upgradeFinished(UpgradeType.Metabolic_Boost, 4000);
        assertEquals(0, before.getFromFrame());
        assertEquals(W - SpeedRunDetector.WINDOW_FRAMES + 1, before.getWindows());
        assertNull(calibration.upgradeFinished(UpgradeType.Metabolic_Boost, 4100));
        assertEquals(4000, calibration.diagnostic(LING).getFromFrame());
        assertEquals(0, calibration.diagnostic(LING).getWindows());

        run(calibration, 8, 4000, LING.topSpeed() * 1.5);
        List<Report> seen = calibration.announce(4000 + W);
        assertEquals(1, seen.size());
        assertEquals(Event.SEEN, seen.get(0).getEvent());
        assertEquals(4000, seen.get(0).getBoundFrame());
        assertTrue(calibration.announce(4001 + W).isEmpty());
    }

    @Test
    void anUnupgradedLingNeverFiresAndAnUpgradeNotFollowedIsIgnored() {
        OwnSpeedCalibration calibration = new OwnSpeedCalibration();

        run(calibration, 7, 2000, LING.topSpeed());

        assertTrue(calibration.announce(2000 + W).isEmpty());
        assertNull(calibration.upgradeFinished(UpgradeType.Adrenal_Glands, 3000));
    }
}
