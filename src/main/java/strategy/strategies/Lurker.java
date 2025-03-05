package strategy.strategies;

import bwapi.UnitType;
import strategy.openers.Opener;

public class Lurker implements Strategy {

    @Override
    public UnitWeights getUnitWeights() {
        UnitWeights weights = new UnitWeights();
        weights.setWeight(UnitType.Zerg_Lurker, 0.2);
        weights.setWeight(UnitType.Zerg_Hydralisk, 0.8);
        weights.setWeight(UnitType.Zerg_Zergling, 0.2);
        return weights;
    }

    @Override
    public boolean playsOpener(Opener opener) {
        switch(opener.getName()) {
            case FOUR_POOL:
            case FIVE_POOL:
                return false;
            default:
                return true;
        }
    }

    @Override
    public StrategyName getType() {
        return StrategyName.LURKER;
    }
}