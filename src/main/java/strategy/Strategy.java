package strategy;

import planner.PlannedItem;

import java.util.List;

public interface Strategy {

    default String getName() {
        return this.getClass().getSimpleName();
    }

    List<PlannedItem> getBuildOrder();

    boolean playsFourPlayerMap();

    boolean isAllIn();
}
