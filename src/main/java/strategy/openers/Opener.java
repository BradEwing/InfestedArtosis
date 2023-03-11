package strategy.openers;

import planner.PlannedItem;

import java.util.List;

public interface Opener {

    default String getNameString() {
        return this.getClass().getSimpleName();
    }

    OpenerName getName();

    List<PlannedItem> getBuildOrder();

    boolean playsFourPlayerMap();

    boolean isAllIn();
}
