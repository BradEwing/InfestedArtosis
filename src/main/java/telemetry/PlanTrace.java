package telemetry;

import macro.plan.PlanBlocker;

/**
 * Per-plan timing and blocker state for the plan event log.
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
