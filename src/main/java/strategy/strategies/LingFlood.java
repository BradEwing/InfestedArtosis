package strategy.strategies;

import bwapi.UnitType;
import strategy.openers.Opener;

public class LingFlood implements Strategy {

    @Override
    public UnitWeights getUnitWeights() {
        UnitWeights weights = new UnitWeights();
        weights.setWeight(UnitType.Zerg_Zergling, 1.0);
        return weights;
    }

    @Override
    public boolean playsOpener(Opener opener) {
        return true;
    }
}
