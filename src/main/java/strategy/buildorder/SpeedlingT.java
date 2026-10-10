package strategy.buildorder;

import bwapi.Race;
import info.GameState;
import info.TechProgression;
import info.tracking.StrategyTracker;
import info.tracking.terran.BunkerNatural;
import info.tracking.terran.TerranWallMain;
import info.tracking.terran.TerranWallNatural;
import strategy.buildorder.opener.OpenerTransitions;
import telemetry.PlanEvents;
import util.Time;

import java.util.Set;

/**
 * {@link Speedling} against Terran. Keeps a base advantage, and bails out of the ling build when a
 * wall is seen early.
 *
 * <p>{@link #shouldTransition} is true before {@link #BAIL_OUT_DEADLINE} while TerranWall is
 * detected this game and BunkerNatural is not, because a Bunker at the natural is the one defence
 * the ling build beats. A Bunker in the main alone does not bail out. The next build is picked from the Terran candidates without
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
        TERRAN_WALL
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
        bailOutTrigger = bailOutTrigger(gameState.getGameTime(), gameState.getOpponentRace(),
                gameState.getStrategyTracker());
        return bailOutTrigger != null;
    }

    /**
     * @param gameTime current game time
     * @param opponentRace the opponent's race
     * @param strategyTracker the detected strategies, possibly null
     * @return the trigger that holds for the detections, see {@link #bailOutTrigger(Time, boolean, boolean)}, or
     *     null against a race other than Terran or with no tracker
     */
    static BailOutTrigger bailOutTrigger(Time gameTime, Race opponentRace, StrategyTracker strategyTracker) {
        if (strategyTracker == null || opponentRace != Race.Terran) {
            return null;
        }
        return bailOutTrigger(gameTime,
                strategyTracker.isAnyDetectedStrategy(TerranWallNatural.NAME, TerranWallMain.NAME),
                strategyTracker.isDetectedStrategy(BunkerNatural.NAME));
    }

    /**
     * @param gameTime current game time
     * @param terranWall whether a Terran wall was detected this game
     * @param bunkerNatural whether BunkerNatural was detected
     * @return the wall trigger, or null after the deadline, with BunkerNatural detected, or with no wall
     */
    static BailOutTrigger bailOutTrigger(Time gameTime, boolean terranWall, boolean bunkerNatural) {
        if (!gameTime.lessThan(BAIL_OUT_DEADLINE) || bunkerNatural) {
            return null;
        }
        return terranWall ? BailOutTrigger.TERRAN_WALL : null;
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
     * The item of the BUILD_ORDER_TRANSITION row, as {@code SpeedlingT>3HatchLurker:TERRAN_WALL}.
     */
    static String label(String next, BailOutTrigger trigger) {
        return NAME + ">" + next + ":" + trigger;
    }
}
