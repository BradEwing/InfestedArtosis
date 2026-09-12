package strategy.buildorder.opener;

import bwapi.Race;
import strategy.buildorder.BuildOrder;
import strategy.buildorder.SpeedlingAllIn;
import strategy.buildorder.protoss.ThreeHatchHydra;
import strategy.buildorder.protoss.ThreeHatchMuta;
import strategy.buildorder.terran.CrazyZerg;
import strategy.buildorder.terran.ThreeHatchLurker;
import strategy.buildorder.terran.TwoHatchMuta;
import strategy.buildorder.zerg.OneHatchSpire;

import java.util.HashSet;
import java.util.Set;

/**
 * Terminal build orders an opener may transition into, by opponent race.
 */
final class OpenerTransitions {
    private OpenerTransitions() {
    }

    static Set<BuildOrder> forRace(Race opponentRace) {
        Set<BuildOrder> next = new HashSet<>();
        switch (opponentRace) {
            case Protoss:
                next.add(new ThreeHatchHydra());
                next.add(new ThreeHatchMuta());
                next.add(new SpeedlingAllIn());
                break;
            case Zerg:
                next.add(new OneHatchSpire());
                next.add(new SpeedlingAllIn());
                break;
            case Terran:
                next.add(new CrazyZerg());
                next.add(new ThreeHatchLurker());
                next.add(new TwoHatchMuta());
                next.add(new SpeedlingAllIn());
                break;
            default:
                break;
        }
        return next;
    }
}
