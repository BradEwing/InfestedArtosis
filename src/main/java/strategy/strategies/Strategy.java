package strategy.strategies;

import bwapi.UnitType;
import planner.PlannedItem;

import java.util.List;
import java.util.Map;

// Represent desired unit types and mix for strategy
//
// Represent tech options as a tree, if root can be made, add to queue w/ high priority
public interface Strategy {
    default String getName() {
        return this.getClass().getSimpleName();
    }

    Map<UnitType, Double> getUnitMix();
}
