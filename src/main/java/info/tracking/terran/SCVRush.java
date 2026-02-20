package info.tracking.terran;

import bwapi.UnitType;
import info.tracking.ObservedUnitTracker;
import info.tracking.StrategyDetectionContext;
import util.Time;

public class SCVRush extends TerranBaseStrategy {

    public SCVRush() {
        super("SCVRush");
    }

    @Override
    public boolean isDetected(StrategyDetectionContext context) {
        ObservedUnitTracker tracker = context.getTracker();
        Time time = context.getTime();
        final int scvCount = tracker.getCountOfLivingUnits(UnitType.Terran_SCV);
        final Time fortyFiveSeconds = new Time(0, 45);
        final Time oneMinute = new Time(1, 0);
        final Time oneMinuteThirty = new Time(1, 30);

        if (scvCount > 1 && time.lessThanOrEqual(fortyFiveSeconds)) {
            return true;
        }
        if (scvCount > 2 && time.lessThanOrEqual(oneMinute)) {
            return true;
        }
        if (scvCount > 3 && time.lessThanOrEqual(oneMinuteThirty)) {
            return true;
        }
        return false;
    }
}
