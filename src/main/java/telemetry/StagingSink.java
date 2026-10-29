package telemetry;

/**
 * Receives the standoff staging episodes of ground squads. Implementations are registered with
 * {@link StagingTelemetry} and must never throw: they run inside the per frame squad loop, where an escaped
 * exception kills the JVM.
 */
public interface StagingSink {

    /**
     * A ground squad started gathering at a standoff point.
     *
     * @param frame current frame
     * @param row what the squad read when it started
     */
    void onStandoffEnter(int frame, StagingRow row);

    /**
     * A ground squad stopped gathering.
     *
     * @param frame current frame
     * @param reason GATHERED, TIMEOUT, ENEMY_ARRIVED or ABANDONED
     * @param row what the squad read when it stopped
     */
    void onStandoffExit(int frame, String reason, StagingRow row);

    /**
     * A reinforcement squad was sent to the rear of a friendly army.
     *
     * @param frame current frame
     * @param row what the squad read; only the squad id, its supply and applied apply
     */
    void onReinforcement(int frame, StagingRow row);
}
