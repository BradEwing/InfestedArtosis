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

    /**
     * Reports the condition that stopped a plan being scheduled this frame. Called every frame the
     * plan is passed over, so implementations are responsible for collapsing the repeats.
     */
    void onBlocked(Plan plan, PlanBlocker blocker);

    default void onEnqueueAll(List<Plan> plans) {
        for (Plan plan : plans) {
            onEnqueue(plan);
        }
    }
}
