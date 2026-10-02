package telemetry;

import bwapi.TilePosition;
import bwapi.UnitType;
import unit.scout.BaseCheckScheduler;

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
                               int dispatchFrame, int endFrame, BaseCheckScheduler.Release outcome,
                               boolean occupied) {
        BaseCheckSink current = sink;
        if (current == null) {
            return;
        }
        current.onBaseChecked(unitId, unitType, base, ageAtDispatch, dispatchFrame, endFrame, outcome, occupied);
    }
}
