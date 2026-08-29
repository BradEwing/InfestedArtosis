package telemetry;

import macro.plan.PlanBlocker;

/**
 * Per-plan bookkeeping for the plan event log: the frame the plan first reached the production
 * queue, the frame of its last state change, and the blocker it is currently waiting on together
 * with the frame that wait began.
 *
 * <p>The plan's identity is not held here. It lives on the plan itself so it is the same value at
 * every point in the lifecycle, including events that arrive before the plan is first seen.
 */
class PlanTrace {

    private final int enqueueFrame;
    private int lastStateFrame;
    private PlanBlocker blocker = PlanBlocker.NONE;
    private int blockerSinceFrame;

    PlanTrace(int frame) {
        this.enqueueFrame = frame;
        this.lastStateFrame = frame;
        this.blockerSinceFrame = frame;
    }

    int getEnqueueFrame() {
        return enqueueFrame;
    }

    int getLastStateFrame() {
        return lastStateFrame;
    }

    void setLastStateFrame(int frame) {
        this.lastStateFrame = frame;
    }

    PlanBlocker getBlocker() {
        return blocker;
    }

    int getBlockerSinceFrame() {
        return blockerSinceFrame;
    }

    void startBlocker(PlanBlocker planBlocker, int frame) {
        this.blocker = planBlocker;
        this.blockerSinceFrame = frame;
    }

    void clearBlocker(int frame) {
        this.blocker = PlanBlocker.NONE;
        this.blockerSinceFrame = frame;
    }
}
