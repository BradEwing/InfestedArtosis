package macro.plan;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * The plans that lost their builder and have not yet dispatched a new one, each with the reason and
 * the reading of the builder it lost, so the dispatch that replaces the builder can report both
 * executors. A plan that settles without a new builder, cancelled or complete, is forgotten.
 */
public class LostBuilders {
    private final Map<Plan, Loss> losses = new HashMap<>();

    /**
     * Records the builder a plan lost, replacing any earlier loss the plan has not yet replaced.
     *
     * @param plan the plan that lost its builder
     * @param reason why the builder was lost
     * @param builder the lost builder as last read
     */
    public void lost(Plan plan, BuilderLossReason reason, BuilderReading builder) {
        losses.put(plan, new Loss(reason, builder));
    }

    /**
     * The loss a newly dispatched builder replaces, or null when the plan lost no builder. The loss
     * is consumed, so one loss pairs with one re-dispatch.
     *
     * @param plan the plan a builder was just dispatched for
     */
    public Loss takeOver(Plan plan) {
        return losses.remove(plan);
    }

    /** Forgets every plan that was cancelled or completed without dispatching a new builder. */
    public void forgetSettled() {
        losses.keySet().removeIf(plan -> plan.getState() == PlanState.CANCELLED
                || plan.getState() == PlanState.COMPLETE);
    }

    public int size() {
        return losses.size();
    }

    /** Why a plan lost its builder, and that builder as last read. */
    @Getter
    public static final class Loss {
        private final BuilderLossReason reason;
        private final BuilderReading builder;

        private Loss(BuilderLossReason reason, BuilderReading builder) {
            this.reason = reason;
            this.builder = builder;
        }
    }
}
