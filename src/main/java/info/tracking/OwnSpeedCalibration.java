package info.tracking;

import bwapi.UnitType;
import bwapi.UpgradeType;
import info.tracking.EnemySpeedTracker.Event;
import info.tracking.EnemySpeedTracker.Report;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs the enemy speed detector on our own Zerglings and Hydralisks, whose upgrades we know the finish of, to score
 * it: a run that ends before our upgrade finished is a false positive, and the first one starting at or after the
 * finish is a hit. Nothing decides on it.
 *
 * <p>An upgrade's bound is the frame it finished for us, and no frame before we have it. The diagnostics of the
 * upgraded type are handed over and start afresh when the upgrade finishes, so one tally covers the frames before it
 * and the next the frames after.
 */
public class OwnSpeedCalibration {
    private static final UpgradeType[] FOLLOWED = {UpgradeType.Metabolic_Boost, UpgradeType.Muscular_Augments};

    private final Map<UpgradeType, Integer> finished = new HashMap<>();
    private final SpeedRunDetector detector =
            new SpeedRunDetector(upgrade -> finished.getOrDefault(upgrade, Integer.MAX_VALUE));
    private final Map<UpgradeType, Set<Event>> reported = new HashMap<>();

    /**
     * Records one sighting of one of our units, see {@link SpeedRunDetector#observe}.
     *
     * @param frame current frame
     * @param unitId the unit's ID
     * @param type the unit's type
     * @param x the unit's position in pixels
     * @param y the unit's position in pixels
     * @param velocity the magnitude of the unit's velocity, pixels per frame
     * @param moving the unit's moving flag
     */
    public void observe(int frame, int unitId, UnitType type, int x, int y, double velocity, boolean moving) {
        detector.observe(frame, unitId, type, x, y, velocity, moving);
    }

    /**
     * Drops the units not seen recently, see {@link SpeedRunDetector#onFrame}.
     *
     * @param frame current frame
     */
    public void onFrame(int frame) {
        detector.onFrame(frame);
    }

    /**
     * Records our upgrade finishing, once.
     *
     * @param upgrade Metabolic Boost or Muscular Augments
     * @param frame the first frame we have it
     * @return the diagnostics of the upgraded type up to the finish, or null when the finish was recorded already
     */
    public SpeedRunDetector.Diagnostic upgradeFinished(UpgradeType upgrade, int frame) {
        UnitType type = EnemySpeedTracker.unitFor(upgrade);
        if (type == null || finished.putIfAbsent(upgrade, frame) != null) {
            return null;
        }
        SpeedRunDetector.Diagnostic before = detector.diagnostic(type);
        detector.resetDiagnostic(type, frame);
        return before;
    }

    /**
     * Reports, once each, the first run that ended before our upgrade finished and the first that started after.
     *
     * @param frame current frame
     * @return EARLY_RUN and SEEN reports, without an anchor; an early run's bound is -1
     */
    public List<Report> announce(int frame) {
        List<Report> reports = new ArrayList<>();
        for (UpgradeType upgrade : FOLLOWED) {
            Set<Event> done = reported.computeIfAbsent(upgrade, u -> EnumSet.noneOf(Event.class));
            SpeedRunDetector.Sighting sighting = detector.seen(upgrade);
            if (sighting != null && done.add(Event.SEEN)) {
                reports.add(EnemySpeedTracker.runReport(Event.SEEN, upgrade, sighting, null));
            }
            SpeedRunDetector.Sighting early = detector.earlyRun(upgrade);
            if (early != null && done.add(Event.EARLY_RUN)) {
                reports.add(new Report(Event.EARLY_RUN, upgrade, early.getMeasuredSpeed(), early.getTopSpeed(),
                        early.getRunFrames(), -1, null));
            }
        }
        return reports;
    }

    /**
     * @param type Zergling or Hydralisk
     * @return what the detector measured on our units of the type since its tally began
     */
    public SpeedRunDetector.Diagnostic diagnostic(UnitType type) {
        return detector.diagnostic(type);
    }
}
