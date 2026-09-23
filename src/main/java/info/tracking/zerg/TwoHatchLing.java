package info.tracking.zerg;

import bwapi.UnitType;
import info.tracking.ObservedUnitTracker;
import info.tracking.StrategyDetectionContext;
import util.Time;

/**
 * Two hatcheries on one base flooding zerglings: a second depot inside the enemy main's area, no natural,
 * and a large zergling count with no Lair tech. The in-main depot separates this build from one-hatch ling
 * floods, and the absence of Lair tech separates it from two-hatch Mutalisk builds.
 */
public class TwoHatchLing extends ZergBaseStrategy {

    static final Time DETECTION_CUTOFF = new Time(6, 0);
    static final int ZERGLING_THRESHOLD = 16;

    private static final UnitType[] LAIR_TECH = {
        UnitType.Zerg_Lair,
        UnitType.Zerg_Hive,
        UnitType.Zerg_Spire,
        UnitType.Zerg_Mutalisk,
        UnitType.Zerg_Hydralisk_Den
    };

    public TwoHatchLing() {
        super("2HatchLing");
    }

    @Override
    public boolean isDetected(StrategyDetectionContext context) {
        Time time = context.getTime();
        if (time.greaterThan(DETECTION_CUTOFF)) {
            return false;
        }
        ObservedUnitTracker tracker = context.getTracker();
        return matches(time,
                context.enemyHasExtraDepotInMainArea(),
                tracker.getUnitTypeCountBeforeTime(UnitType.Zerg_Zergling, time),
                context.enemyNaturalHasDepot(),
                tracker.hasObservedAnyBeforeTime(time, LAIR_TECH));
    }

    /**
     * The detection rule over the scouted evidence.
     *
     * @param time current game time
     * @param extraDepotInMain a living enemy depot stands in the enemy main's area besides the main depot
     * @param zerglingsSeen enemy zerglings ever observed
     * @param naturalHasDepot a living enemy depot stands at the enemy natural
     * @param lairTechSeen an enemy Lair, Hive, Spire, Mutalisk or Hydralisk Den was ever observed
     */
    static boolean matches(Time time, boolean extraDepotInMain, int zerglingsSeen, boolean naturalHasDepot,
                           boolean lairTechSeen) {
        return !time.greaterThan(DETECTION_CUTOFF)
                && extraDepotInMain
                && zerglingsSeen >= ZERGLING_THRESHOLD
                && !naturalHasDepot
                && !lairTechSeen;
    }
}
