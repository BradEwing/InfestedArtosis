package info.tracking.protoss;

import bwapi.UnitType;
import info.map.BaseArea;
import info.tracking.StrategyDetectionContext;
import util.Time;

/**
 * https://liquipedia.net/starcraft/Forge_FE_(vs._Zerg)
 */
public class FFE extends ProtossBaseStrategy {

    private static final Time DETECTION_CUTOFF = new Time(4, 30);
    private static final int MANHATTAN_RADIUS = 8;

    public FFE() {
        super("FFE");
    }

    @Override
    public boolean isDetected(StrategyDetectionContext context) {
        if (context.getTime().greaterThan(DETECTION_CUTOFF)) {
            return false;
        }

        BaseArea enemyNatural = context.enemyNaturalArea(MANHATTAN_RADIUS);
        if (enemyNatural == null) {
            return false;
        }

        return context.getTracker().hasLivingUnitAt(FFE::isWallBuilding, enemyNatural::contains);
    }

    private static boolean isWallBuilding(UnitType unitType) {
        return unitType == UnitType.Protoss_Forge || unitType == UnitType.Protoss_Photon_Cannon;
    }
}
