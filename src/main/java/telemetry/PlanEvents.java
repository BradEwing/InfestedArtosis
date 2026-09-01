package telemetry;

import bwapi.UnitType;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.PlanState;

import java.util.List;

/**
 * Static dispatch point for plan lifecycle events.
 *
 * <p>Plans are created and mutated from managers that have no telemetry dependency, so the hooks
 * reach the logger through this holder rather than through a constructor argument. With no sink
 * registered every entry point is a single null check: no allocation, no BWAPI call, no file
 * handle.
 */
public final class PlanEvents {

    private static PlanEventSink sink;

    private PlanEvents() {
    }

    public static void register(PlanEventSink planEventSink) {
        sink = planEventSink;
    }

    public static void clear() {
        sink = null;
    }

    public static void enqueued(Plan plan) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onEnqueue(plan);
    }

    public static void enqueued(List<Plan> plans) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onEnqueueAll(plans);
    }

    public static void stateChanged(Plan plan, PlanState from, PlanState to) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onStateChange(plan, from, to);
    }

    public static void blocked(Plan plan, PlanBlocker blocker) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onBlocked(plan, blocker);
    }

    public static void buildAheadHold(Plan holder, int heldFrames, int starvedBehind) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onBuildAheadHold(holder, heldFrames, starvedBehind);
    }

    public static void buildAheadEvicted(Plan holder, int heldFrames, int starvedBehind) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onBuildAheadEvict(holder, heldFrames, starvedBehind);
    }

    public static void withheld(UnitType unitType, PlanBlocker blocker) {
        PlanEventSink current = sink;
        if (current == null) {
            return;
        }
        current.onWithheld(unitType, blocker);
    }
}
