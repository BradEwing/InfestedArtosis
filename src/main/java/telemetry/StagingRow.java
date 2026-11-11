package telemetry;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;

/**
 * What a ground squad read at a standoff, reinforcement or launch decision. A column a row has no reading for keeps
 * its default, -1 or NONE.
 */
@Value
@Builder
public class StagingRow {
    String squadId;
    boolean applied;
    int supply;
    int members;
    @Default
    double gatheredShare = -1;
    @Default
    int enemyDistance = -1;
    @Default
    int standoffDistance = -1;
    @Default
    int framesStaged = -1;
    @Default
    String enemyType = "NONE";
    @Default
    int enemyReach = -1;
    @Default
    int episodeId = -1;
    @Default
    String memberUnitIds = "NONE";
    @Default
    String targetSquadId = "NONE";
    @Default
    int minMass = -1;
}
