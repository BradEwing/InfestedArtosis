package info.tracking;

import bwapi.Unit;

import java.util.HashMap;

public class ObservedUnitTracker {
    private final HashMap<Unit, ObservedUnit> observedUnits = new HashMap<>();

    public ObservedUnitTracker() {

    }

    public void onUnitShow(Unit unit, int currentFrame) {
        if (!observedUnits.containsKey(unit)) {
            observedUnits.put(unit, new ObservedUnit(unit, currentFrame));
        } else {
            ObservedUnit u = observedUnits.get(unit);
            u.setLastObservedFrame(currentFrame);
            u.setLastKnownLocation(unit.getTilePosition());
        }
    }

    public void onUnitHide(Unit unit, int currentFrame) {
        if (observedUnits.containsKey(unit)) {
            ObservedUnit u = observedUnits.get(unit);
            // Update last seen info before unit goes out of vision
            u.setLastObservedFrame(currentFrame);
            u.setLastKnownLocation(unit.getTilePosition());
        }
    }

    public void onUnitDestroy(Unit unit, int currentFrame) {
        if (observedUnits.containsKey(unit)) {
            ObservedUnit u = observedUnits.get(unit);
            u.setDestroyedFrame(currentFrame);
            u.setLastKnownLocation(null);
        }
    }
}
