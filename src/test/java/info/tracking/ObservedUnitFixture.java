package info.tracking;

import bwapi.UnitType;
import util.Time;

/**
 * Builds ObservedUnit and ObservedUnitTracker state without a live bwapi Unit, which cannot be instantiated
 * outside its own package. Units built here wrap a null Unit, so a tracker holds at most one of them.
 */
public final class ObservedUnitFixture {

    private ObservedUnitFixture() {
    }

    public static ObservedUnit observedUnit(UnitType unitType, Time firstObservedFrame) {
        return new ObservedUnit(null, unitType, null, firstObservedFrame, false);
    }

    public static ObservedUnitTracker trackerHolding(ObservedUnit observedUnit) {
        ObservedUnitTracker tracker = new ObservedUnitTracker();
        tracker.track(observedUnit);
        return tracker;
    }

    public static void changeType(ObservedUnit observedUnit, UnitType unitType) {
        ObservedUnitTracker.updateUnitTypeChange(observedUnit, unitType);
    }
}
