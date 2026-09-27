package telemetry;

/**
 * Receives air reinforcement rows. Implementations are registered with {@link AirReinforcementTelemetry} and must
 * never throw: they run inside the per frame squad loop, where an escaped exception kills the JVM.
 */
public interface AirReinforcementSink {

    /**
     * An air reinforcement event.
     *
     * @param row the row to record
     */
    void onRow(AirReinforcementRow row);
}
