package telemetry;

/**
 * Receives the Bunker telemetry events: Bunker stance changes.
 */
public interface BunkerSink {

    /**
     * A Bunker stance, or a Drone round it opened, started or ended.
     *
     * @param event the change
     */
    void onStance(BunkerStanceEvent event);
}
