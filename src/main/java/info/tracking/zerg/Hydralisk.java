package info.tracking.zerg;

import bwapi.UnitType;
import info.tracking.ObservedUnitTracker;
import info.tracking.StrategyDetectionContext;

public class Hydralisk extends ZergBaseStrategy {

    public Hydralisk() {
        super("Hydralisk");
    }

    @Override
    public boolean isDetected(StrategyDetectionContext context) {
        ObservedUnitTracker tracker = context.getTracker();
        return tracker.getCountOfLivingUnits(UnitType.Zerg_Hydralisk_Den, UnitType.Zerg_Hydralisk, UnitType.Zerg_Lurker) > 0;
    }
}
