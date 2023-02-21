package info;

import bwapi.UnitType;

import java.util.HashMap;

// UnitTypeCount tracks the number of units per type
public class UnitTypeCount {
    private HashMap<UnitType, Integer> unitTypeCount = new HashMap();
    private HashMap<UnitType, Integer> plannedUnitTypeCount = new HashMap();

    public int get(UnitType unitType) {
        return unitTypeCount.get(unitType) + plannedUnitTypeCount.get(unitType);
    }

    public void addUnit(UnitType unitType) {
        if (!unitTypeCount.containsKey(unitType)) {
            unitTypeCount.put(unitType, 0);
        }
        final int newCount = unitTypeCount.get(unitType) + 1;
        unitTypeCount.put(unitType, newCount);
    }

    public void removeUnit(UnitType unitType) {
        if (!unitTypeCount.containsKey(unitType)) {
            return;
        }
        final int newCount = unitTypeCount.get(unitType) - 1;
        unitTypeCount.put(unitType, newCount);
    }

    public void planUnit(UnitType unitType) {
        if (!plannedUnitTypeCount.containsKey(unitType)) {
            plannedUnitTypeCount.put(unitType, 0);
        }
        final int newCount = plannedUnitTypeCount.get(unitType) + 1;
        plannedUnitTypeCount.put(unitType, newCount);
    }

    public void unplanUnit(UnitType unitType) {
        if (!plannedUnitTypeCount.containsKey(unitType)) {
            return;
        }

        final int newCount = plannedUnitTypeCount.get(unitType) - 1;
        plannedUnitTypeCount.put(unitType, newCount);
    }

    public HashMap<UnitType, Integer> getCountLookup() {
        return unitTypeCount;
    }
}
