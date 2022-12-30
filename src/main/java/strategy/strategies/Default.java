package strategy.strategies;

import bwapi.UnitType;

import java.util.HashMap;
import java.util.Map;

/**
 * Default strategy, reflecting the original hardcoded unit mix
 */
public class Default implements Strategy {

    // TODO: Handle mix when units are still gated behind tech
    public Map<UnitType, Integer> getUnitWeights() {
        HashMap<UnitType, Integer> map = new HashMap<>();
        map.put(UnitType.Zerg_Zergling, 5);
        map.put(UnitType.Zerg_Hydralisk, 5);
        return map;
    }
}
