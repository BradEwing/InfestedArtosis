package telemetry;

/**
 * Receives every burrow and unburrow command a Lurker issues. Implementations are registered with
 * {@link BurrowTelemetry} and must never throw: they run inside the per frame unit update, where an escaped exception
 * kills the JVM.
 */
public interface BurrowSink {

    /**
     * A Lurker was ordered to burrow or unburrow.
     *
     * @param command the command
     */
    void onBurrowCommand(BurrowCommand command);
}
