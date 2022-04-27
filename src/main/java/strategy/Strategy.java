package strategy;

import lombok.Data;
import planner.PlannedItem;

import java.util.ArrayList;
import java.util.List;

public interface Strategy {

    String getName();

    int getWins();

    int getLosses();

    List<PlannedItem> getBuildOrder();
}
