package strategy.strategies;

import bwapi.UnitType;
import strategy.openers.Opener;

public class Mutalisk implements Strategy {

    public UnitWeights getUnitWeights() {
        UnitWeights weights = new UnitWeights();
        weights.setWeight(UnitType.Zerg_Zergling, 0.3);
        weights.setWeight(UnitType.Zerg_Mutalisk, 0.7);
        weights.setWeight(UnitType.Zerg_Scourge, 0.0);
        return weights;
    }

    @Override
    public boolean playsOpener(Opener opener) {
        switch(opener.getName()) {
            case FOUR_POOL:
                return false;
            default:
                return true;
        }
    }
}
