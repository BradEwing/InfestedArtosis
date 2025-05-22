package macro.buildorder;

import bwapi.UnitType;

import java.util.HashMap;
import java.util.Map;

public class BuildOrder {

    private final Map<UnitType, Integer> desiredUnitCounts = new HashMap<>();

    public void setUnitCount(UnitType unitType, int count) {
        desiredUnitCounts.put(unitType, count);
    }

    public void addUnitCount(UnitType unitType, int count) {
        desiredUnitCounts.put(unitType, desiredUnitCounts.getOrDefault(unitType, 0) + count);
    }
}
