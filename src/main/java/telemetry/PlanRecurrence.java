package telemetry;

/**
 * Counts repeated cancellations of one plan item inside a rolling window.
 */
public final class PlanRecurrence {

    static final int WINDOW_FRAMES = 480;

    static final int THRESHOLD = 6;

    private int count;

    private int windowStartFrame = -1;

    /**
     * Records one cancellation and reports whether the window filled up.
     *
     * @return true on the THRESHOLD-th cancellation inside the window; the counter then resets
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
