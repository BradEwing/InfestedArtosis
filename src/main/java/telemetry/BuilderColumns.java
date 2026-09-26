package telemetry;

import macro.plan.BuilderDispatchDecision;
import macro.plan.BuilderLossReason;
import macro.plan.BuilderReading;

import java.util.Arrays;
import java.util.List;

/**
 * The executor a plan row reports, what the row says happened to it, and the builder columns written
 * about it: builder_dispatch_decision, builder_role, builder_order, builder_in_range and
 * previous_executor_unit_id.
 *
 * <p>A row written about the plan's current executor reads NONE in builder_role when the plan has
 * none, and UNMANAGED when its executor is a unit no managed unit wraps, so a blank role means the
 * row shape does not report the builder at all. A BUILDER_LOST row reports the builder it lost, and
 * a BUILDER_REDISPATCH row reports the new builder with the lost one in previous_executor_unit_id;
 * both carry the loss in builder_dispatch_decision.
 */
final class BuilderColumns {
    static final String NO_EXECUTOR = "NONE";
    static final String UNMANAGED = "UNMANAGED";

    static final BuilderColumns BLANK = new BuilderColumns(false, null, null, null);

    private final boolean reported;
    private final BuilderReading executor;
    private final BuilderReading previous;
    private final BuilderDispatchDecision decision;

    private BuilderColumns(boolean reported, BuilderReading executor, BuilderReading previous,
                           BuilderDispatchDecision decision) {
        this.reported = reported;
        this.executor = executor;
        this.previous = previous;
        this.decision = decision;
    }

    /**
     * The builder columns of a row about the plan's current executor.
     *
     * @param executor the executor as read on the row's frame, or null when the plan has none
     */
    static BuilderColumns current(BuilderReading executor) {
        return new BuilderColumns(true, executor, null, null);
    }

    /**
     * The builder columns of a BUILDER_DISPATCH_DECISION row.
     *
     * @param executor the executor as read on the row's frame, or null when the plan has none
     * @param decision what the dispatch gate did
     */
    static BuilderColumns gateDecision(BuilderReading executor, BuilderDispatchDecision decision) {
        return new BuilderColumns(true, executor, null, decision);
    }

    /**
     * The builder columns of a BUILDER_LOST row.
     *
     * @param reason why the builder was lost
     * @param lost the lost builder as last read
     */
    static BuilderColumns lost(BuilderLossReason reason, BuilderReading lost) {
        return new BuilderColumns(true, lost, null, reason.decision());
    }

    /**
     * The builder columns of a BUILDER_REDISPATCH row.
     *
     * @param reason why the previous builder was lost
     * @param lost the previous builder as last read
     * @param taker the new builder, read on its dispatch frame
     */
    static BuilderColumns redispatch(BuilderLossReason reason, BuilderReading lost, BuilderReading taker) {
        return new BuilderColumns(true, taker, lost, reason.decision());
    }

    /** The executor the row's executor_unit_id and builder_distance_px report, or null for none. */
    BuilderReading executor() {
        return executor;
    }

    /** builder_dispatch_decision: the gate's decision, or the loss a builder row reports, or null. */
    BuilderDispatchDecision decision() {
        return decision;
    }

    String executorUnitId() {
        return executor == null ? "" : String.valueOf(executor.getUnitId());
    }

    String distance() {
        return executor == null || executor.getDistancePx() == null ? "" : String.valueOf(executor.getDistancePx());
    }

    /** builder_role, builder_order, builder_in_range and previous_executor_unit_id, in header order. */
    List<String> trailing() {
        return Arrays.asList(role(), order(), inRange(), previousExecutorUnitId());
    }

    private String role() {
        if (!reported) {
            return "";
        }
        if (executor == null) {
            return NO_EXECUTOR;
        }
        return executor.getRole() == null ? UNMANAGED : executor.getRole().toString();
    }

    private String order() {
        return executor == null || executor.getOrder() == null ? "" : Csv.sanitize(executor.getOrder());
    }

    private String inRange() {
        return executor == null || executor.getInRange() == null ? "" : String.valueOf(executor.getInRange());
    }

    private String previousExecutorUnitId() {
        return previous == null ? "" : String.valueOf(previous.getUnitId());
    }
}
