package telemetry;

/**
 * Per-plan bookkeeping for the plan event log: the short id used in place of the plan uuid, the
 * frame the plan first reached the production queue, and the frame of its last state change.
 */
class PlanTrace {

    private final int id;
    private final int enqueueFrame;
    private int lastStateFrame;

    PlanTrace(int id, int frame) {
        this.id = id;
        this.enqueueFrame = frame;
        this.lastStateFrame = frame;
    }

    int getId() {
        return id;
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
}
