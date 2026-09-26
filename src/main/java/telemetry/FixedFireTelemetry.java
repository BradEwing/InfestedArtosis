package telemetry;

import bwapi.Position;
import bwapi.UnitType;
import util.StaticDefenseZone;

/**
 * Static dispatch point for fixed fire cooldown and Lurker hold telemetry. With no sink registered, every method is
 * a no-op.
 */
public final class FixedFireTelemetry {

    private static FixedFireSink sink;

    private FixedFireTelemetry() {
    }

    public static void register(FixedFireSink fixedFireSink) {
        sink = fixedFireSink;
    }

    public static void clear() {
        sink = null;
    }

    public static void cooldownStarted(int frame, int unitId, UnitType unitType, Position position,
                                       StaticDefenseZone zone) {
        FixedFireSink current = sink;
        if (current == null) {
            return;
        }
        current.onCooldownStarted(frame, unitId, unitType, position, zone);
    }

    public static void targetSkipped(int frame, int attackerId, UnitType attackerType, Position attackerPosition,
                                     int targetId, UnitType targetType, Position targetPosition,
                                     StaticDefenseZone zone) {
        FixedFireSink current = sink;
        if (current == null) {
            return;
        }
        current.onTargetSkipped(frame, attackerId, attackerType, attackerPosition, targetId, targetType,
                targetPosition, zone);
    }

    public static void lurkerHold(int frame, int lurkerId, Position position, Position holdPoint,
                                  StaticDefenseZone zone, String reason) {
        FixedFireSink current = sink;
        if (current == null) {
            return;
        }
        current.onLurkerHold(frame, lurkerId, position, holdPoint, zone, reason);
    }

    public static void lurkerHoldReleased(int frame, int lurkerId, Position position, Position holdPoint,
                                          String reason) {
        FixedFireSink current = sink;
        if (current == null) {
            return;
        }
        current.onLurkerHoldReleased(frame, lurkerId, position, holdPoint, reason);
    }
}
