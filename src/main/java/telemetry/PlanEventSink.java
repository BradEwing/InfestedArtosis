package telemetry;

import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.PlanState;

import java.util.List;

/**
 * Receives plan lifecycle events. Implementations are registered with {@link PlanEvents} and must
 * never throw: they run inside the production hot path, where an escaped exception kills the JVM.
 */
public interface PlanEventSink {

    void onEnqueue(Plan plan);

    void onStateChange(Plan plan, PlanState from, PlanState to);

    void onBlocked(Plan plan, PlanBlocker blocker);

    default void onEnqueueAll(List<Plan> plans) {
        for (Plan plan : plans) {
            onEnqueue(plan);
        }
    }

    /** A building plan still holds the build-ahead slot, starving the plans queued behind it. */
    default void onBuildAheadHold(Plan holder, int heldFrames, int starvedBehind) {
    }

    /** A building plan lost the build-ahead slot without placing its building. */
    default void onBuildAheadEvict(Plan holder, int heldFrames, int starvedBehind) {
    }
}
