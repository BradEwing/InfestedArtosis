package telemetry;

import bwapi.UpgradeType;
import info.tracking.EnemySpeedTracker;

/**
 * Static dispatch point for speed parity gate and enemy speed telemetry. With no sink registered, every method is a
 * no-op.
 */
public final class SpeedParityTelemetry {

    private static SpeedParitySink sink;

    private SpeedParityTelemetry() {
    }

    public static void register(SpeedParitySink speedParitySink) {
        sink = speedParitySink;
    }

    public static void clear() {
        sink = null;
    }

    public static void gateRead(int frame, SpeedParityGate gate) {
        SpeedParitySink current = sink;
        if (current == null) {
            return;
        }
        current.onGateRead(frame, gate);
    }

    public static void enemySpeed(int frame, UpgradeType upgrade, EnemySpeedTracker.Evidence evidence) {
        SpeedParitySink current = sink;
        if (current == null) {
            return;
        }
        current.onEnemySpeed(frame, upgrade, evidence);
    }

    public static void ownSpeedFinished(int frame) {
        SpeedParitySink current = sink;
        if (current == null) {
            return;
        }
        current.onOwnSpeedFinished(frame);
    }
}
