package strategy.strategies;

import bwapi.UnitType;

import java.util.HashMap;
import java.util.Map;

public class LingFlood implements Strategy {
    public UnitWeights getUnitWeights() {
        UnitWeights weights = new UnitWeights();
        weights.setWeight(UnitType.Zerg_Zergling, 1.0);
        return weights;
    }
}
