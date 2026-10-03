package strategy.buildorder.opener;

import bwapi.Race;
import config.Config;
import info.GameState;
import info.tracking.StrategyTracker;
import strategy.buildorder.BuildOrder;
import strategy.buildorder.SpeedlingAllIn;
import strategy.buildorder.protoss.ThreeHatchHydra;
import strategy.buildorder.protoss.ThreeHatchMuta;
import strategy.buildorder.terran.CrazyZerg;
import strategy.buildorder.terran.TwoHatchHydraTerran;
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

    /**
     * The transitions for this game's opponent race, leaving SpeedlingAllIn out against a Terran whose wall was
     * detected this game or in the previous game, and offering TwoHatchHydraTerran only against a Terran whose
     * mech persists across recent games or when the strategy override names it.
     */
    static Set<BuildOrder> forGame(GameState gameState) {
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        boolean terranWall = strategyTracker != null && strategyTracker.isTerranWallDetected();
        boolean hydraBuild = strategyTracker != null && strategyTracker.isTerranMechPersistent()
                || isHydraBuildForced(gameState.getConfig());
        return forRace(gameState.getOpponentRace(), terranWall, hydraBuild);
    }

    /**
     * Whether the strategy override names {@link TwoHatchHydraTerran}.
     */
    static boolean isHydraBuildForced(Config config) {
        return config != null && TwoHatchHydraTerran.NAME.equals(config.strategyOverride);
    }

    static Set<BuildOrder> forRace(Race opponentRace) {
        return forRace(opponentRace, false, false);
    }

    static Set<BuildOrder> forRace(Race opponentRace, boolean terranWall) {
        return forRace(opponentRace, terranWall, false);
    }

    static Set<BuildOrder> forRace(Race opponentRace, boolean terranWall, boolean hydraBuild) {
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
                if (hydraBuild) {
                    next.add(new TwoHatchHydraTerran());
                }
                next.add(new ThreeHatchLurker());
                next.add(new TwoHatchMuta());
                if (!terranWall) {
                    next.add(new SpeedlingAllIn());
                }
                break;
            default:
                break;
        }
        return next;
    }
}
