package telemetry;

import lombok.Value;

/**
 * What a ground squad read at a standoff or reinforcement decision.
 */
@Value
public class StagingRow {
    String squadId;
    boolean applied;
    int supply;
    int members;
    double gatheredShare;
    int enemyDistance;
    int standoffDistance;
    int framesStaged;
}
