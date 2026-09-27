package telemetry;

/**
 * Receives air harass rows. Implementations are registered with {@link HarassTelemetry} and must never throw: they
 * run inside the per frame squad loop, where an escaped exception kills the JVM.
 */
public interface HarassSink {

    /**
     * An air harass event.
     *
     * @param row the row to record
     */
    void onRow(HarassRow row);
}
