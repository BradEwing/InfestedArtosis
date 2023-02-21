package strategy.strategies;

import bwapi.UnitType;

import java.util.HashMap;
import java.util.Map;

/**
 * Default strategy, reflecting the original hardcoded unit mix
 */
public class Default implements Strategy {

    // TODO: Handle mix when units are still gated behind tech
    public UnitWeights getUnitWeights() {
        UnitWeights weights = new UnitWeights();
        weights.setWeight(UnitType.Zerg_Zergling, 0.5);
        weights.setWeight(UnitType.Zerg_Hydralisk, 0.5);
        return weights;
    }
}
