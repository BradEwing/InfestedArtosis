package strategy.strategies;

import bwapi.UnitType;

import java.util.HashMap;
import java.util.Map;

/**
 * Default strategy, reflecting the original hardcoded fighter unit mix
 */
public class Default implements Strategy {

    public Map<UnitType, Double> getUnitMix() {
        HashMap<UnitType, Double> map = new HashMap<>();
        map.put(UnitType.Zerg_Zergling, 0.50);
        map.put(UnitType.Zerg_Hydralisk, 0.50);
        return map;
    }
}
