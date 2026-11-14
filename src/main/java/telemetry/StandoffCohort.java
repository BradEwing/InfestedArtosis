package telemetry;

import lombok.Value;

/**
 * The standoff launch cohort one of our units belongs to: the standoff episode it stood in at STANDOFF_ENTER or
 * joined afterwards, -1 for none, and whether it joined after STANDOFF_ENTER.
 */
@Value
public class StandoffCohort {
    public static final StandoffCohort NONE = new StandoffCohort(-1, false);

    int episodeId;
    boolean joiner;
}
