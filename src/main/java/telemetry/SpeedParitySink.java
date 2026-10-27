package telemetry;

import bwapi.UpgradeType;
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
     * The evidence for an enemy upgrade rose.
     *
     * @param frame current frame
     * @param upgrade Metabolic Boost or Muscular Augments
     * @param evidence the new level, PLAUSIBLE from timing or SEEN from observed movement
     */
    void onEnemySpeed(int frame, UpgradeType upgrade, EnemySpeedTracker.Evidence evidence);

    /**
     * Our own Metabolic Boost finished.
     *
     * @param frame the first frame the upgrade was seen finished
     */
    void onOwnSpeedFinished(int frame);
}
