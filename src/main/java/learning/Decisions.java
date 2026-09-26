package learning;

import lombok.Data;
import strategy.buildorder.BuildOrder;

@Data
public class Decisions {
    private BuildOrder opener;

    /**
     * The strategies the learning file recorded as detected in the previous game against this opponent, joined by
     * ';'. Empty when there was no previous game.
     */
    private String lastGameDetectedStrategies = "";
}
