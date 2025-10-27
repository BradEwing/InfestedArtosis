package info;

import bwapi.UnitType;

import java.util.HashMap;

// UnitTypeCount tracks the number of units per type
public class UnitTypeCount {
    private HashMap<UnitType, Integer> unitTypeCount = new HashMap<>();
    private HashMap<UnitType, Integer> plannedUnitTypeCount = new HashMap<>();

    public int get(UnitType unitType) {
        ensureUnitType(unitType);

        return unitTypeCount.get(unitType) + plannedUnitTypeCount.get(unitType);
    }

    private int safeGet(UnitType unitType) {
        ensureUnitType(unitType);
        return unitTypeCount.get(unitType);
    }

    private int safeLivingGet(UnitType unitType) {
        ensureUnitType(unitType);
        return unitTypeCount.get(unitType);
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
        int newCount = plannedUnitTypeCount.get(unitType) + 1;
        if (unitType == UnitType.Zerg_Zergling || unitType == UnitType.Zerg_Scourge) {
            newCount += 1;
        }

        plannedUnitTypeCount.put(unitType, newCount);
    }

    public void unplanUnit(UnitType unitType) {
        if (!plannedUnitTypeCount.containsKey(unitType)) {
            return;
        }

        final int newCount = plannedUnitTypeCount.get(unitType) - 1;
        if (newCount >= 0) {
            plannedUnitTypeCount.put(unitType, newCount);
        }
    }

    public HashMap<UnitType, Integer> getCountLookup() {
        return unitTypeCount;
    }

    private void ensureUnitType(UnitType unitType) {
        if (!unitTypeCount.containsKey(unitType)) {
            unitTypeCount.put(unitType, 0);
        }

        if (!plannedUnitTypeCount.containsKey(unitType)) {
            plannedUnitTypeCount.put(unitType, 0);
        }
    }

    public int meleeCount() {
        return safeGet(UnitType.Zerg_Zergling) + safeGet(UnitType.Zerg_Ultralisk);
    }

    public int rangedCount() {
        return safeGet(UnitType.Zerg_Hydralisk) + safeGet(UnitType.Zerg_Lurker);
    }

    public int groundCount() {
        return meleeCount() + rangedCount() + safeGet(UnitType.Zerg_Defiler);
    }

    public int airCount() {
        return safeGet(UnitType.Zerg_Mutalisk) + safeGet(UnitType.Zerg_Guardian) + safeGet(UnitType.Zerg_Devourer);
    }

    public int livingCount(UnitType unitType) {
        return safeLivingGet(unitType);
    }
}
