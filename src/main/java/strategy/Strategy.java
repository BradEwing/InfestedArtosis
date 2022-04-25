package strategy;

import lombok.Data;
import planner.PlannedItem;

import java.util.ArrayList;
import java.util.List;

@Data
public class Strategy {

    private String name;

    private int winsTotal;
    private int losesTotal;
    private int winsOpponent;
    private int losesOpponent;
    private int winsRace;
    private int losesRace;

    private boolean hasExtractor;

    private List<PlannedItem> buildOrder = new ArrayList<>();
}
