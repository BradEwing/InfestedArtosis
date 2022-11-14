package strategy;

import planner.PlannedItem;

import java.util.List;

public interface Strategy {
    default String getName() {
        return this.getClass().getSimpleName();
    }
}
