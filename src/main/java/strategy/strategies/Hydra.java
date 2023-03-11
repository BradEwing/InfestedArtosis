package strategy.strategies;

import bwapi.UnitType;
import strategy.openers.Opener;

/**
 * Hydralisk heavy.
 *
 * Includes lings at low probability to ensure fighting units before den is complete.
 */
public class Hydra implements Strategy {
    public UnitWeights getUnitWeights() {
        UnitWeights weights = new UnitWeights();
        weights.setWeight(UnitType.Zerg_Zergling, 0.1);
        weights.setWeight(UnitType.Zerg_Hydralisk_Den, 0.9);
        return weights;
    }

    @Override
    public boolean playsOpener(Opener opener) {
        switch(opener.getName()) {
            case TWELVE_HATCH:
            case OVER_POOL:
            case NINE_POOL_SPEED:
                return true;
            default:
                return false;
        }
    }
}
