package info.tracking;

import bwapi.TilePosition;
import bwapi.UnitType;
import util.Time;

import java.util.Collection;
import java.util.Set;

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

    /**
     * The count ObservedUnitTracker.countCompletedWhileObservedOnTiles() takes over its tracked units, taken
     * over the given units instead, since a tracker holds at most one unit built here.
     */
    public static int countCompletedWhileObservedOnTiles(Collection<ObservedUnit> units, UnitType unitType,
                                                         Set<TilePosition> tiles, Time t) {
        return ObservedUnitTracker.countCompletedWhileObservedOnTiles(units, unitType, tiles, t);
    }

    public static void changeType(ObservedUnit observedUnit, UnitType unitType) {
        ObservedUnitTracker.updateUnitTypeChange(observedUnit, unitType);
    }
}
