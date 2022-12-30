package strategy.strategies;

import bwapi.UnitType;

import java.util.HashMap;
import java.util.Map;

public class LingFlood implements Strategy {
    public Map<UnitType, Integer> getUnitWeights() {
        HashMap<UnitType, Integer> map = new HashMap<>();
        map.put(UnitType.Zerg_Zergling, 10);
        return map;
    }
}
