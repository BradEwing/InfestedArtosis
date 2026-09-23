package telemetry;

/**
 * Receives runby decision ticks and entry checks. Implementations are registered with {@link RunbyTelemetry} and
 * must never throw: they run inside the per frame squad loop, where an escaped exception kills the JVM.
 */
public interface RunbySink {

    /**
     * A runby squad's decision tick, or a containing squad's entry check.
     *
     * @param tick the row to record
     */
    void onTick(RunbyTick tick);
}
