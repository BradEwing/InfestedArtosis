package telemetry;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.UnitType;

/**
 * Static dispatch point for finished base checks. With no sink registered, every method is a no-op.
 */
public final class BaseChecks {

    private static BaseCheckSink sink;

    private BaseChecks() {
    }

    public static void register(BaseCheckSink baseCheckSink) {
        sink = baseCheckSink;
    }

    public static void clear() {
        sink = null;
    }

    public static void checked(int unitId, UnitType unitType, TilePosition base, int ageAtDispatch,
                               int dispatchFrame, BaseCheckEnd end) {
        BaseCheckSink current = sink;
        if (current == null) {
            return;
        }
        current.onBaseChecked(unitId, unitType, base, ageAtDispatch, dispatchFrame, end);
    }

    public static void skipped(int frame, TilePosition base, BaseCheckSkip reason, Position site) {
        BaseCheckSink current = sink;
        if (current == null) {
            return;
        }
        current.onBaseCheckSkipped(frame, base, reason, site);
    }
}
