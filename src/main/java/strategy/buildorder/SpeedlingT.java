package strategy.buildorder;

import bwapi.Race;
import info.GameState;
import info.TechProgression;
import info.tracking.StrategyTracker;
import info.tracking.terran.BunkerMain;
import info.tracking.terran.BunkerNatural;
import info.tracking.terran.TerranWallMain;
import info.tracking.terran.TerranWallNatural;
import strategy.buildorder.opener.OpenerTransitions;
import telemetry.PlanEvents;
import util.Time;

import java.util.Set;

/**
 * {@link Speedling} against Terran. Keeps a base advantage, and bails out of the ling build when a
 * wall or a main Bunker is seen early.
 *
 * <p>{@link #shouldTransition} is true before {@link #BAIL_OUT_DEADLINE} while TerranWall or
 * BunkerMain is detected this game and BunkerNatural is not, because a Bunker at the natural is the
 * one defence the ling build beats. The next build is picked from the Terran candidates without
 * a Speedling, and a BUILD_ORDER_TRANSITION row {@code SpeedlingT>NextBuild:TRIGGER} is written on
 * the frame the pick is made.
 */
public class SpeedlingT extends Speedling {

    public static final String NAME = "SpeedlingT";

    /** The game time from which the build no longer bails out. */
    static final Time BAIL_OUT_DEADLINE = new Time(4, 30);

    /** What made the build bail out, written to the BUILD_ORDER_TRANSITION row. */
    public enum BailOutTrigger {
        /** TerranWallNatural or TerranWallMain was detected. */
        TERRAN_WALL,
        /** BunkerMain was detected. */
        BUNKER_MAIN
    }

    private BailOutTrigger bailOutTrigger;

    public SpeedlingT() {
        super(NAME);
    }

    @Override
    public boolean playsRace(Race race) {
        return race == Race.Terran;
    }

    /**
     * False. The build has no tech unit to be larva bound on: every larva goes to a Zergling the
     * Spawning Pool already allows, and its own hatchery request at the mineral bar is the one producer.
     */
    @Override
    protected boolean macroHatcheryTechReady(TechProgression techProgression) {
        return false;
    }

    @Override
    protected boolean wantsBaseAdvantage() {
        return true;
    }

    @Override
    public boolean shouldTransition(GameState gameState) {
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        if (strategyTracker == null || gameState.getOpponentRace() != Race.Terran) {
            return false;
        }
        bailOutTrigger = bailOutTrigger(gameState.getGameTime(),
                strategyTracker.isAnyDetectedStrategy(TerranWallNatural.NAME, TerranWallMain.NAME),
                strategyTracker.isDetectedStrategy(BunkerMain.NAME),
                strategyTracker.isDetectedStrategy(BunkerNatural.NAME));
        return bailOutTrigger != null;
    }

    /**
     * @param gameTime current game time
     * @param terranWall whether a Terran wall was detected this game
     * @param bunkerMain whether BunkerMain was detected
     * @param bunkerNatural whether BunkerNatural was detected
     * @return the trigger that holds, the wall first, or null after the deadline, with BunkerNatural
     *     detected, or with neither a wall nor BunkerMain
     */
    static BailOutTrigger bailOutTrigger(Time gameTime, boolean terranWall, boolean bunkerMain,
                                         boolean bunkerNatural) {
        if (!gameTime.lessThan(BAIL_OUT_DEADLINE) || bunkerNatural) {
            return null;
        }
        if (terranWall) {
            return BailOutTrigger.TERRAN_WALL;
        }
        return bunkerMain ? BailOutTrigger.BUNKER_MAIN : null;
    }

    @Override
    public Set<BuildOrder> transition(GameState gameState) {
        return OpenerTransitions.terranWithoutSpeedling(gameState);
    }

    @Override
    public void onTransitioned(BuildOrder next) {
        if (bailOutTrigger != null) {
            PlanEvents.buildOrderTransition(label(next.getName(), bailOutTrigger));
        }
    }

    /**
     * The item of the BUILD_ORDER_TRANSITION row, as {@code SpeedlingT>3HatchLurker:BUNKER_MAIN}.
     */
    static String label(String next, BailOutTrigger trigger) {
        return NAME + ">" + next + ":" + trigger;
    }
}
