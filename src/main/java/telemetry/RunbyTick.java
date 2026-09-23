package telemetry;

import bwapi.Position;
import lombok.Builder;
import lombok.Getter;
import unit.squad.RunbyEvaluator;
import unit.squad.RunbyState;

/**
 * One row of telemetry_runby.csv: a runby squad's decision tick, or a containing squad's runby entry check.
 *
 * <p>Counts and flags left at -1 were not evaluated for the row's event.
 */
@Getter
@Builder
public final class RunbyTick {

    /**
     * What the row describes.
     */
    public enum Event {
        TICK,
        ENTRY_CHECK
    }

    private final int frame;
    private final String squadId;
    private final Event event;
    private final RunbyEvaluator.EntryVerdict verdict;
    private final RunbyState.Phase phase;
    private final RunbyState.GoalType goalType;
    private final Position seekPoint;
    private final Position anchor;
    @Builder.Default
    private final int abortWindowOpen = -1;
    @Builder.Default
    private final double enemyTally = -1;
    @Builder.Default
    private final double ourTally = -1;
    @Builder.Default
    private final double pathTally = -1;
    @Builder.Default
    private final int inBaseArea = -1;
    private final int lings;
    @Builder.Default
    private final int workersVisible = -1;
    @Builder.Default
    private final int exposedLings = -1;
    @Builder.Default
    private final int hpLost = -1;
    @Builder.Default
    private final int hpLostNonWorkerInReach = -1;
    @Builder.Default
    private final int winnable = -1;
    @Builder.Default
    private final int basesUnderAttack = -1;
    @Builder.Default
    private final int workersKilled = -1;
    @Builder.Default
    private final int buildingsKilled = -1;
}
