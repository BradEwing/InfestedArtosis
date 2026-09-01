package info;

import bwapi.UnitType;

import java.util.HashMap;

// UnitTypeCount tracks the number of units per type
public class UnitTypeCount {
    private HashMap<UnitType, Integer> unitTypeCount = new HashMap<>();
    private HashMap<UnitType, Integer> plannedUnitTypeCount = new HashMap<>();
    private HashMap<UnitType, Integer> totalProduced = new HashMap<>();

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
        totalProduced.merge(unitType, 1, Integer::sum);
    }

    public int getTotalProduced(UnitType unitType) {
        return totalProduced.getOrDefault(unitType, 0);
    }

    public void removeUnit(UnitType unitType) {
        if (!unitTypeCount.containsKey(unitType)) {
            return;
        }
        final int newCount = unitTypeCount.get(unitType) - 1;
        unitTypeCount.put(unitType, newCount);
    }

    /**
     * Structures that morph in place from a finished structure of another type.
     *
     * @param unitType the structure the morph produces
     * @return the structure the morph upgrades, or null when the morph does not upgrade one
     */
    public static UnitType morphPredecessor(UnitType unitType) {
        switch (unitType) {
            case Zerg_Lair:
                return UnitType.Zerg_Hatchery;
            case Zerg_Hive:
                return UnitType.Zerg_Lair;
            case Zerg_Greater_Spire:
                return UnitType.Zerg_Spire;
            default:
                return null;
        }
    }

    /**
     * Applies the cost of a building morph when the morph is assigned.
     * <p>
     * A Creep Colony or a Drone is consumed the moment the morph starts. An in-place structure
     * upgrade is not: a morphing Lair, Hive or Greater Spire still occupies the map and still
     * counts as a resource depot or tech building, so its predecessor stays counted until
     * {@link #completeMorph} runs.
     *
     * @param plannedUnit the structure the morph produces
     */
    public void startBuildingMorph(UnitType plannedUnit) {
        if (plannedUnit == UnitType.Zerg_Sunken_Colony || plannedUnit == UnitType.Zerg_Spore_Colony) {
            removeUnit(UnitType.Zerg_Creep_Colony);
            return;
        }

        if (morphPredecessor(plannedUnit) == null) {
            removeUnit(UnitType.Zerg_Drone);
        }
    }

    /**
     * Drops the predecessor of a finished in-place structure upgrade.
     *
     * @param unitType the structure that completed
     */
    public void completeMorph(UnitType unitType) {
        UnitType predecessor = morphPredecessor(unitType);
        if (predecessor != null) {
            removeUnit(predecessor);
        }
    }

    /**
     * Removes a destroyed unit. An in-place structure upgrade that dies before it completes was
     * never added under its own type, so its predecessor is what must be removed.
     *
     * @param unitType the destroyed unit
     * @param isCompleted whether the destroyed unit had finished morphing
     */
    public void removeDestroyedUnit(UnitType unitType, boolean isCompleted) {
        UnitType predecessor = morphPredecessor(unitType);
        if (!isCompleted && predecessor != null) {
            removeUnit(predecessor);
            return;
        }

        removeUnit(unitType);
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
