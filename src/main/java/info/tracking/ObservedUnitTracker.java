package info.tracking;

import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import util.Time;

import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

public class ObservedUnitTracker {
    private final HashMap<Unit, ObservedUnit> observedUnits = new HashMap<>();

    public ObservedUnitTracker() {

    }

    public void onUnitShow(Unit unit, int currentFrame, boolean isProxied) {
        Time t = new Time(currentFrame);
        if (!observedUnits.containsKey(unit)) {
            observedUnits.put(unit, new ObservedUnit(unit, t, isProxied));
        } else {
            ObservedUnit u = observedUnits.get(unit);
            u.setLastObservedFrame(t);
            u.setLastKnownLocation(unit.getPosition());
            updateUnitTypeChange(unit);
        }
    }

    public void onUnitHide(Unit unit, int currentFrame) {
        Time t = new Time(currentFrame);
        if (observedUnits.containsKey(unit)) {
            ObservedUnit u = observedUnits.get(unit);
            // Update last seen info before unit goes out of vision
            u.setLastObservedFrame(t);
            u.setLastKnownLocation(unit.getPosition());
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
                .filter(ou -> ou.getUnitType() == unitType)
                .filter(ou -> ou.getDestroyedFrame() == null)
                .count();
    }

    public void updateUnitTypeChange(Unit unit) {
        ObservedUnit observedUnit = observedUnits.get(unit);
        UnitType trackedType = observedUnit.getUnitType();
        UnitType unitType = unit.getType();
        if (unitType != trackedType) {
            observedUnit.setUnitType(unit.getType());
        }
    }

    public Set<Position> getLastKnownPositionsOfLivingUnits(UnitType unitType) {
        return observedUnits.values()
                .stream()
                .filter(ou -> ou.getUnitType()  == unitType)
                .filter(ou -> ou.getDestroyedFrame() == null)
                .map(ou -> {
                    if (ou.getUnit().isVisible()) {
                        return ou.getUnit().getPosition();
                    } else {
                        return ou.getLastKnownLocation();
                    }
                })
                .collect(Collectors.toSet());
    }

    public int getProxiedCountByTypeBeforeTime(UnitType unitType, Time detectedBy) {
        return (int) observedUnits.values()
                .stream()
                .filter(ou -> ou.getUnit().getType() == unitType)
                .filter(ObservedUnit::isProxied)
                .filter(ou -> ou.getFirstObservedFrame().lessThanOrEqual(detectedBy))
                .count();
    }

    public Set<Unit> getVisibleUnits() {
        return observedUnits.values()
                .stream()
                .filter(ou -> ou.getUnit().isVisible())
                .filter(ou -> ou.getDestroyedFrame() == null)
                .map(ou -> ou.getUnit())
                .collect(Collectors.toSet());
    }

    public Set<Unit> getBuilding() {
        return observedUnits.values()
                .stream()
                .filter(ou -> ou.getUnit().getType().isBuilding())
                .filter(ou -> ou.getDestroyedFrame() == null)
                .map(ou -> ou.getUnit())
                .collect(Collectors.toSet());
    }
}
