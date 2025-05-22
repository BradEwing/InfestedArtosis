package info.tracking;

import bwapi.Unit;
import bwapi.UnitType;
import util.Time;

import java.util.HashMap;

public class ObservedUnitTracker {
    private final HashMap<Unit, ObservedUnit> observedUnits = new HashMap<>();

    public ObservedUnitTracker() {

    }

    public void onUnitShow(Unit unit, int currentFrame) {
        Time t = new Time(currentFrame);
        if (!observedUnits.containsKey(unit)) {
            observedUnits.put(unit, new ObservedUnit(unit, t));
        } else {
            ObservedUnit u = observedUnits.get(unit);
            u.setLastObservedFrame(t);
            u.setLastKnownLocation(unit.getTilePosition());
        }
    }

    public void onUnitHide(Unit unit, int currentFrame) {
        Time t = new Time(currentFrame);
        if (observedUnits.containsKey(unit)) {
            ObservedUnit u = observedUnits.get(unit);
            // Update last seen info before unit goes out of vision
            u.setLastObservedFrame(t);
            u.setLastKnownLocation(unit.getTilePosition());
        }
    }

    public void onUnitDestroy(Unit unit, int currentFrame) {
        Time t = new Time(currentFrame);
        if (observedUnits.containsKey(unit)) {
            ObservedUnit u = observedUnits.get(unit);
            u.setDestroyedFrame(t);
            u.setLastKnownLocation(null);
        }
    }

    public int getUnitTypeCountBeforeTime(UnitType type, Time t) {
        return (int) observedUnits.values()
                .stream()
                .filter(ou -> ou.getUnit().getType() == type)
                .filter(ou -> ou.getFirstObservedFrame().lessThanOrEqual(t))
                .count();
    }

    public int size() {
        return observedUnits.size();
    }

    public int getCountOfLivingUnits(UnitType unitType) {
        return (int) observedUnits.values()
                .stream()
                .filter(ou -> ou.getUnit().getType() == unitType)
                .filter(ou -> ou.getDestroyedFrame() == null)
                .count();
    }
}
