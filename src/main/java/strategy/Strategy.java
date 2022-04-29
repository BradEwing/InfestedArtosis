package strategy;

import planner.PlannedItem;

import java.util.List;

public interface Strategy {

    String getName();

    List<PlannedItem> getBuildOrder();

    boolean playsFourPlayerMap();
}
