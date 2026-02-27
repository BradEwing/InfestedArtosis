package info.tracking.terran;

import bwapi.TilePosition;
import bwapi.UnitType;
import info.tracking.StrategyDetectionContext;
import util.Time;

import java.util.Set;

public class SCVRush extends TerranBaseStrategy {

    private static final Time THREE_MINUTES_THIRTY = new Time(3, 30);

    public SCVRush() {
        super("SCVRush");
    }

    @Override
    public boolean isDetected(StrategyDetectionContext context) {
        Set<TilePosition> mainBaseTiles = context.getGameMap().getMainBaseTiles();
        if (mainBaseTiles.isEmpty()) {
            return false;
        }

        if (context.getTime().greaterThan(THREE_MINUTES_THIRTY)) {
            return false;
        }

        return context.getTracker().getCountOfLivingUnitsOnTiles(UnitType.Terran_SCV, mainBaseTiles) >= 3;
    }

}
