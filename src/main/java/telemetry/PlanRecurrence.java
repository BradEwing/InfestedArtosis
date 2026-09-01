package telemetry;

/**
 * Counts repeated cancellations of one plan item inside a rolling window.
 *
 * <p>An enqueue/cancel disagreement used to recur silently for thousands of frames (IA-286):
 * a build order queued a hatchery and a canceller removed it every frame, so the bot never
 * started its unit production. The plan event log now surfaces that shape: when the same item
 * is cancelled by the same source often enough inside a short window, the logger emits a
 * RECURRING_CANCEL row alongside the ordinary transition. Each counted cancellation stands for
 * one enqueue/cancel cycle, because every cancelled plan was queued first. No new persisted
 * column is needed; the existing event row carries the item, the cancel source and the age of
 * the plan at cancellation.
 */
public final class PlanRecurrence {

    static final int WINDOW_FRAMES = 480;

    static final int THRESHOLD = 6;

    private int count;

    private int windowStartFrame = -1;

    /**
     * Records one cancellation and reports whether the window filled up.
     *
     * @param frame the frame the cancellation happened on
     * @return true when this cancellation is the THRESHOLD-th inside the current window; the
     *     counter resets on reporting, so a sustained loop re-reports at every multiple of
     *     THRESHOLD instead of every cancellation
     */
    public boolean record(int frame) {
        if (windowStartFrame < 0 || frame - windowStartFrame >= WINDOW_FRAMES) {
            windowStartFrame = frame;
            count = 0;
        }
        count++;
        if (count < THRESHOLD) {
            return false;
        }
        count = 0;
        return true;
    }
}
