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
     * @param reason NONE for a new episode, MERGED for an episode carried on by a merged squad
     * @param row what the squad read when it started
     */
    void onStandoffEnter(int frame, String reason, StagingRow row);

    /**
     * A ground squad stopped gathering.
     *
     * @param frame current frame
     * @param reason why the episode ended, see {@link StagingLogger}
     * @param row what the squad read when it stopped
     */
    void onStandoffExit(int frame, String reason, StagingRow row);

    /**
     * A reinforcement squad was sent to the rear of a friendly army.
     *
     * @param frame current frame
     * @param row what the squad read; the squad id, its supply, its members, the army it joins and applied apply
     */
    void onReinforcement(int frame, StagingRow row);

    /**
     * A reinforcement squad stopped being sent to an army's rear.
     *
     * @param frame current frame
     * @param outcome JOINED, DIED or ABANDONED
     * @param row the squad and the army it was sent to
     */
    void onReinforcementOutcome(int frame, String outcome, StagingRow row);

    /**
     * A ground squad left the rally point on its own, not routed to an army.
     *
     * @param frame current frame
     * @param reason NONE, BELOW_MIN_MASS or EXEMPT, see {@link StagingLogger}
     * @param row the squad, its members and the minimum mass in force
     */
    void onLaunch(int frame, String reason, StagingRow row);

    /**
     * A ground squad cleared to launch was kept from launching alone because it was below the minimum mass and had no
     * army's rear to join.
     *
     * @param frame current frame
     * @param reason HOME when it was kept at the rally point, CONTAIN when it was sent to a contain's arc instead
     * @param row the squad, its members and the minimum mass in force
     */
    void onLaunchHeld(int frame, String reason, StagingRow row);
}
