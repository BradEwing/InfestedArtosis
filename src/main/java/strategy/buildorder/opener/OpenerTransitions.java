package strategy.buildorder.opener;

import bwapi.Race;
import strategy.buildorder.BuildOrder;
import strategy.buildorder.SpeedlingAllIn;
import strategy.buildorder.protoss.ThreeHatchMuta;
import strategy.buildorder.terran.CrazyZerg;
import strategy.buildorder.terran.ThreeHatchLurker;
import strategy.buildorder.zerg.OneHatchSpire;

import java.util.HashSet;
import java.util.Set;

/**
 * Terminal build orders an opener may transition into, by opponent race. 3HatchHydra and
 * 2HatchMuta are retired: their classes stay registered so learning rows that name them still
 * resolve, but no opener offers them.
 */
final class OpenerTransitions {
    private OpenerTransitions() {
    }

    static Set<BuildOrder> forRace(Race opponentRace) {
        Set<BuildOrder> next = new HashSet<>();
        switch (opponentRace) {
            case Protoss:
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
                next.add(new SpeedlingAllIn());
                break;
            default:
                break;
        }
        return next;
    }
}
