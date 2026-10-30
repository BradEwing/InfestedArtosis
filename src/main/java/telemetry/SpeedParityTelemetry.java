package telemetry;

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

    /**
     * @return true when a sink is registered, so a caller can skip building what it would report
     */
    public static boolean isRegistered() {
        return sink != null;
    }

    public static void gateRead(int frame, SpeedParityGate gate) {
        SpeedParitySink current = sink;
        if (current == null) {
            return;
        }
        current.onGateRead(frame, gate);
    }

    public static void enemySpeed(int frame, EnemySpeedTracker.Report report) {
        SpeedParitySink current = sink;
        if (current == null) {
            return;
        }
        current.onEnemySpeed(frame, report);
    }

    public static void ownSpeedFinished(int frame) {
        SpeedParitySink current = sink;
        if (current == null) {
            return;
        }
        current.onOwnSpeedFinished(frame);
    }
}
