package telemetry;

import info.tracking.EnemySpeedTracker;

/**
 * Receives the speed parity gate's holds and the enemy speed inferences. Implementations are registered with
 * {@link SpeedParityTelemetry} and must never throw: they run inside the per frame squad loop, where an escaped
 * exception kills the JVM.
 */
public interface SpeedParitySink {

    /**
     * What the speed parity gate read for a ground squad this frame. A row is written when the term changes from the
     * last frame; a NONE term writes nothing and ends the squad's episode.
     *
     * @param frame current frame
     * @param gate what the gate read
     */
    void onGateRead(int frame, SpeedParityGate gate);

    /**
     * A first of the game for an enemy upgrade: the frame reached its earliest finish, a run made it SEEN, or a run
     * was refused before the earliest finish.
     *
     * @param frame current frame
     * @param report what the tracker reported
     */
    void onEnemySpeed(int frame, EnemySpeedTracker.Report report);

    /**
     * Our own Metabolic Boost finished.
     *
     * @param frame the first frame the upgrade was seen finished
     */
    void onOwnSpeedFinished(int frame);
}
