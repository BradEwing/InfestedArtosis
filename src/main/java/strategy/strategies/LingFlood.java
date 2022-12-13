package strategy.strategies;

import bwapi.UnitType;

import java.util.HashMap;
import java.util.Map;

public class LingFlood implements Strategy {
    public Map<UnitType, Double> getUnitMix() {
        HashMap<UnitType, Double> map = new HashMap<>();
        map.put(UnitType.Zerg_Zergling, 1.00);
        return map;
    }
}
