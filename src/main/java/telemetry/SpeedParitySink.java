package telemetry;

import info.tracking.EnemySpeedTracker;
import info.tracking.SpeedRunDetector;

/**
 * Receives the speed parity gate's holds, the enemy speed inferences and the speed detector's calibration on our own
 * units. Implementations are registered with {@link SpeedParityTelemetry} and must never throw: they run inside the
 * per frame squad loop, where an escaped exception kills the JVM.
 */
public interface SpeedParitySink {

    /** Whose units a speed detector watched. */
    enum Side {
        /** The enemy's units. */
        ENEMY,
        /** Our own units. */
        SELF
    }

    /**
     * What the speed parity gate read for a ground squad this frame. A row is written when the term changes from the
     * last frame; a NONE term writes nothing and ends the squad's episode.
     *
     * @param frame current frame
     * @param gate what the gate read
     */
    void onGateRead(int frame, SpeedParityGate gate);

    /**
     * An enemy upgrade's report: the frame reached its earliest finish or the earliest finish changed since, a window
     * made it SEEN, or a window was refused before the earliest finish.
     *
     * @param frame current frame
     * @param report what the tracker reported
     */
    void onEnemySpeed(int frame, EnemySpeedTracker.Report report);

    /**
     * The speed detector on our own units passed a window before our upgrade finished, or after it.
     *
     * @param frame current frame
     * @param report the window
     */
    void onSelfSpeed(int frame, EnemySpeedTracker.Report report);

    /**
     * What a speed detector measured on one unit type.
     *
     * @param frame current frame
     * @param side whose units it watched
     * @param diagnostic the measures
     */
    void onSpeedDiagnostic(int frame, Side side, SpeedRunDetector.Diagnostic diagnostic);

    /**
     * Our own Metabolic Boost finished.
     *
     * @param frame the first frame the upgrade was seen finished
     */
    void onOwnSpeedFinished(int frame);
}
