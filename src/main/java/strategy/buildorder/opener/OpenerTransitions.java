package strategy.buildorder.opener;

import bwapi.Race;
import config.Config;
import info.GameState;
import info.tracking.StrategyTracker;
import strategy.buildorder.BuildOrder;
import info.tracking.terran.BunkerMain;
import strategy.buildorder.SpeedlingP;
import strategy.buildorder.SpeedlingR;
import strategy.buildorder.SpeedlingT;
import strategy.buildorder.SpeedlingZ;
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
public final class OpenerTransitions {
    private OpenerTransitions() {
    }

    /**
     * The transitions for this game's opponent race, leaving SpeedlingT out against a Terran whose wall was
     * detected this game or in the previous game or whose main Bunker is detected, and offering
     * TwoHatchHydraTerran only against a Terran whose mech persists across recent games or when the strategy
     * override names it.
     */
    static Set<BuildOrder> forGame(GameState gameState) {
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        return forRace(gameState.getOpponentRace(), barsSpeedlingT(strategyTracker),
                offersHydraBuild(strategyTracker, gameState.getConfig()));
    }

    /**
     * Whether SpeedlingT is left out: a Terran wall was detected this game or persists, or BunkerMain is detected.
     */
    static boolean barsSpeedlingT(StrategyTracker strategyTracker) {
        return strategyTracker != null
                && (strategyTracker.isTerranWallDetected() || strategyTracker.isDetectedStrategy(BunkerMain.NAME));
    }

    /**
     * The Terran transitions without SpeedlingT, for the build that bails out of it.
     */
    public static Set<BuildOrder> terranWithoutSpeedling(GameState gameState) {
        return forRace(Race.Terran, true, offersHydraBuild(gameState.getStrategyTracker(), gameState.getConfig()));
    }

    /**
     * Whether TwoHatchHydraTerran is offered: the persisted TerranMech prior holds, or the strategy override
     * names it.
     */
    static boolean offersHydraBuild(StrategyTracker strategyTracker, Config config) {
        return strategyTracker != null && strategyTracker.isTerranMechPersistent() || isHydraBuildForced(config);
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
                next.add(new SpeedlingP());
                break;
            case Zerg:
                next.add(new OneHatchSpire());
                next.add(new SpeedlingZ());
                break;
            case Terran:
                next.add(new CrazyZerg());
                if (hydraBuild) {
                    next.add(new TwoHatchHydraTerran());
                }
                next.add(new ThreeHatchLurker());
                next.add(new TwoHatchMuta());
                if (!terranWall) {
                    next.add(new SpeedlingT());
                }
                break;
            default:
                next.add(new SpeedlingR());
                break;
        }
        return next;
    }
}
