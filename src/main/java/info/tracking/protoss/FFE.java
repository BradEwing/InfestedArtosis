package info.tracking.protoss;

import bwapi.TilePosition;
import bwapi.UnitType;
import bwem.Base;
import info.BaseData;
import info.tracking.ObservedUnitTracker;
import info.tracking.StrategyDetectionContext;
import util.Time;

/**
 * https://liquipedia.net/starcraft/Forge_FE_(vs._Zerg)
 */
public class FFE extends ProtossBaseStrategy {

    private static final Time DETECTION_CUTOFF = new Time(4, 30);
    private static final int MANHATTAN_THRESHOLD = 8;

    public FFE() {
        super("FFE");
    }

    @Override
    public boolean isDetected(StrategyDetectionContext context) {
        if (context.getTime().greaterThan(DETECTION_CUTOFF)) {
            return false;
        }

        BaseData baseData = context.getBaseData();
        Base enemyNatural = baseData.getEnemyNaturalBase();
        if (enemyNatural == null) {
            return false;
        }

        TilePosition naturalTile = enemyNatural.getLocation();
        ObservedUnitTracker tracker = context.getTracker();

        return tracker.hasLivingUnitNearTile(UnitType.Protoss_Forge, naturalTile, MANHATTAN_THRESHOLD)
                || tracker.hasLivingUnitNearTile(UnitType.Protoss_Photon_Cannon, naturalTile, MANHATTAN_THRESHOLD);
    }
}
