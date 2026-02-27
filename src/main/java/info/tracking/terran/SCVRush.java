package info.tracking.terran;

import bwapi.TilePosition;
import bwapi.UnitType;
import info.tracking.ObservedUnitTracker;
import info.tracking.StrategyDetectionContext;
import util.Time;

import java.util.Set;

public class SCVRush extends TerranBaseStrategy {

    public SCVRush() {
        super("SCVRush");
    }

    @Override
    public boolean isDetected(StrategyDetectionContext context) {
        Set<TilePosition> mainBaseTiles = context.getGameMap().getMainBaseTiles();
        if (mainBaseTiles == null || mainBaseTiles.isEmpty()) {
            return false;
        }

        ObservedUnitTracker tracker = context.getTracker();
        Time time = context.getTime();
        final Time threeMinuteThirty = new Time(3, 30);

        if (time.greaterThan(threeMinuteThirty)) {
            return false;
        }

        int scvsOnBase = tracker.getCountOfLivingUnitsOnTiles(UnitType.Terran_SCV, mainBaseTiles);
        return scvsOnBase >= 3;
    }

}
