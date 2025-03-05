package strategy.strategies;

import bwapi.UnitType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class UnitWeights {
    private Map<UnitType, Double> weightMap = new HashMap<>();

    private HashSet<UnitType> enabledUnitTypes = new HashSet<>();

    public Double getWeight(UnitType unitType) {
        return weightMap.get(unitType);
    }

    public void setWeight(UnitType unitType, Double weight) {
        weightMap.put(unitType, weight);
    }

    public void removeUnit(UnitType unitType) {
        weightMap.remove(unitType);
    }

    public boolean hasUnit(UnitType unitType) {
        return weightMap.containsKey(unitType);
    }

    public void enableUnit(UnitType unitType) {
        enabledUnitTypes.add(unitType);
    }

    public boolean isEnabled(UnitType unitType) {
        return enabledUnitTypes.contains(unitType);
    }

    public void disableUnit(UnitType unitType) {
        enabledUnitTypes.remove(unitType);
    }

    // getRandom returns a UnitType based on weight
    public UnitType getRandom() {
        UnitType unitType = UnitType.Unknown;
        double totalWeight = 0;

        for (Map.Entry<UnitType, Double> e: weightMap.entrySet()) {
            if (!enabledUnitTypes.contains(e.getKey())) {
                continue;
            }
            totalWeight += e.getValue();
        }

        double randomNumber = Math.random() * totalWeight;
        for (Map.Entry<UnitType, Double> e: weightMap.entrySet()) {
            if (!enabledUnitTypes.contains(e.getKey())) {
                continue;
            }
            randomNumber -= e.getValue();
            if (randomNumber <= 0.0) {
                unitType = e.getKey();
                break;
            }
        }

        return unitType;
    }
}
