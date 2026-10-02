package telemetry;

/**
 * Receives air flock rows. Implementations are registered with {@link FlockTelemetry} and must never throw: they
 * run inside the per frame squad loop, where an escaped exception kills the JVM.
 */
public interface FlockSink {

    /**
     * An air flock sample or loss.
     *
     * @param row the row to record
     */
    void onRow(FlockRow row);
}
