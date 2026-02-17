package info.tracking;

import bwapi.PlayerType;
import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import util.Filter;
import util.Time;

import java.util.HashMap;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class ObservedUnitTracker {
    private final HashMap<Unit, ObservedUnit> observedUnits = new HashMap<>();

    public ObservedUnitTracker() {

    }

    public void onFrame() {
        for (ObservedUnit ou : observedUnits.values()) {
            if (ou.getDestroyedFrame() != null) {
                continue;
            }
            if (!ou.isCompleted()) {
                Unit unit = ou.getUnit();
                if (unit.isVisible() && unit.isCompleted()) {
                    ou.setCompleted(true);
                }
            }
        }
    }

    public void onUnitShow(Unit unit, int currentFrame, boolean isProxied) {
        Time t = new Time(currentFrame);
        if (!observedUnits.containsKey(unit)) {
            ObservedUnit ou = new ObservedUnit(unit, t, isProxied);
            ou.setCompleted(unit.isCompleted());
            observedUnits.put(unit, ou);
        } else {
            ObservedUnit u = observedUnits.get(unit);
            u.setLastObservedFrame(t);
            u.setLastKnownLocation(unit.getPosition());
            u.setCompleted(unit.isCompleted());
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
                .filter(ou -> ou.getUnitType() == type)
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
                .filter(ou -> ou != null)
                .collect(Collectors.toSet());
    }

    public Set<Position> getLastKnownPositionsOfLivingUnits(UnitType... unitTypes) {
        final Set<UnitType> typeSet = Arrays.stream(unitTypes).collect(Collectors.toSet());
        return observedUnits.values()
                .stream()
                .filter(ou -> typeSet.contains(ou.getUnitType()))
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
                .filter(ou -> ou.getUnitType() == unitType)
                .filter(ObservedUnit::isProxied)
                .filter(ou -> ou.getFirstObservedFrame().lessThanOrEqual(detectedBy))
                .count();
    }

    public Set<Unit> getDetectedUnits() {
        return observedUnits.values()
                .stream()
                .filter(ou -> ou.getUnit().isDetected())
                .filter(ou -> ou.getDestroyedFrame() == null)
                .map(ou -> ou.getUnit())
                .collect(Collectors.toSet());
    }

    public Set<Unit> getBuilding() {
        return observedUnits.values()
                .stream()
                .filter(ou -> ou.getUnitType().isBuilding())
                .filter(ou -> ou.getDestroyedFrame() == null)
                .map(ou -> ou.getUnit())
                .collect(Collectors.toSet());
    }

    public Set<Unit> getCompletedBuildings() {
        return observedUnits.values()
                .stream()
                .filter(ou -> ou.getUnitType().isBuilding())
                .filter(ou -> ou.getDestroyedFrame() == null)
                .filter(ObservedUnit::isCompleted)
                .map(ou -> ou.getUnit())
                .collect(Collectors.toSet());
    }

    public Set<Position> getLastKnownPositionsOfBuildings() {
        return observedUnits.values()
                .stream()
                .filter(ou -> ou.getUnitType().isBuilding())
                .filter(ou -> ou.getDestroyedFrame() == null)
                .map(ou -> ou.getLastKnownLocation())
                .collect(Collectors.toSet());
    }

    public Set<Unit> getVisibleEnemyUnits() {
        return observedUnits.values()
                .stream()
                .filter(ou -> ou.getUnit().isVisible())
                .filter(ou -> ou.getUnit().getPlayer().getType() != PlayerType.None)
                .filter(ou -> ou.getUnit().getPlayer().getType() != PlayerType.Neutral)
                .filter(ou -> ou.getDestroyedFrame() == null)
                .map(ou -> ou.getUnit())
                .collect(Collectors.toSet());
    }

    public Set<Unit> getHostileToGroundBuildings() {
        return observedUnits.values()
                .stream()
                .filter(ou -> ou.getUnitType().isBuilding())
                .filter(ou -> Filter.isHostileBuildingToGround(ou.getUnitType()))
                .filter(ou -> ou.getDestroyedFrame() == null)
                .map(ou -> ou.getUnit())
                .collect(Collectors.toSet());
    }

    public int getCompletedBuildingCountNearPositions(UnitType type, Set<Position> positions, int distance) {
        return (int) observedUnits.values().stream()
                .filter(ou -> ou.getUnitType() == type)
                .filter(ou -> ou.getDestroyedFrame() == null)
                .filter(ObservedUnit::isCompleted)
                .filter(ou -> isNearAnyPosition(ou, positions, distance))
                .count();
    }

    public int getIncompleteBuildingCountNearPositions(Set<Position> positions, int distance) {
        return (int) observedUnits.values().stream()
                .filter(ou -> ou.getUnitType().isBuilding())
                .filter(ou -> ou.getDestroyedFrame() == null)
                .filter(ou -> !ou.isCompleted())
                .filter(ou -> isNearAnyPosition(ou, positions, distance))
                .count();
    }

    public int getLivingBuildingCountNearPositions(Set<Position> positions, int distance) {
        return (int) observedUnits.values().stream()
                .filter(ou -> ou.getUnitType().isBuilding())
                .filter(ou -> ou.getDestroyedFrame() == null)
                .filter(ou -> isNearAnyPosition(ou, positions, distance))
                .count();
    }

    private boolean isNearAnyPosition(ObservedUnit ou, Set<Position> positions, int distance) {
        Position unitPos = ou.getUnit().isVisible() ? ou.getUnit().getPosition() : ou.getLastKnownLocation();
        if (unitPos == null) { 
            return false;
        }
        for (Position pos : positions) {
            if (unitPos.getDistance(pos) <= distance) {
                return true;
            }
        }
        return false;
    }

    public Set<Unit> getProxiedBuildings() {
        return observedUnits.values()
                .stream()
                .filter(ou -> ou.getUnitType().isBuilding())
                .filter(ObservedUnit::isProxied)
                .filter(ou -> ou.getDestroyedFrame() == null)
                .map(ObservedUnit::getUnit)
                .collect(Collectors.toSet());
    }

    public Set<Unit> getWorkerUnitsNearPositions(Set<Position> positions, int distance) {
        return observedUnits.values()
                .stream()
                .filter(ou -> Filter.isWorkerType(ou.getUnitType()))
                .filter(ou -> ou.getDestroyedFrame() == null)
                .filter(ou -> isNearAnyPosition(ou, positions, distance))
                .map(ObservedUnit::getUnit)
                .collect(Collectors.toSet());
    }

    public Position getLastKnownPosition(Unit unit) {
        ObservedUnit ou = observedUnits.get(unit);
        if (ou == null) {
            return null;
        }
        if (ou.getUnit().isVisible()) {
            return ou.getUnit().getPosition();
        }
        return ou.getLastKnownLocation();
    }

    public void clearLastKnownLocationsAt(Set<Position> visibleLocations) {
        if (visibleLocations == null || visibleLocations.isEmpty()) {
            return;
        }

        observedUnits.values()
                .stream()
                .filter(ou -> ou.getDestroyedFrame() == null)
                .filter(ou -> ou.getLastKnownLocation() != null)
                .filter(ou -> visibleLocations.contains(ou.getLastKnownLocation()))
                .forEach(ou -> ou.setLastKnownLocation(null));
    }
}
