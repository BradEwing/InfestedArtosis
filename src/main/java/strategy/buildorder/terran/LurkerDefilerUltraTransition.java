package strategy.buildorder.terran;

import bwapi.Race;
import bwapi.UnitType;
import info.GameState;
import strategy.buildorder.BuildOrder;
import telemetry.PlanEvents;
import util.Time;

import java.util.HashSet;
import java.util.Set;

/**
 * When a ZvT build hands over to {@link LurkerDefilerUltra}.
 *
 * <p>Each transitioning build names its own trigger, and every trigger waits on the same economy
 * gate as well: {@value #ECONOMY_DRONES} living Drones and {@value #ECONOMY_BASES} bases. The
 * terminal build asks for Hive tech, four gases and Defilers; entered on a smaller economy it
 * would spend the bank the economy still needs.
 *
 * <p>Only 2HatchMuta and 3HatchLurker transition. CrazyZerg is terminal and reaches Hive on its
 * own.
 */
public final class LurkerDefilerUltraTransition {

    /** Living Drones every transition waits on. Owner decision. */
    static final int ECONOMY_DRONES = 21;

    /** Bases every transition waits on. Owner decision. */
    static final int ECONOMY_BASES = 3;

    /**
     * Living enemy Goliaths that end 2HatchMuta. Against SiegeExpand every 2HatchMuta win had at
     * most one Goliath at 16 minutes, and every loss had five or more.
     */
    static final int GOLIATH_TRIGGER = 6;

    /**
     * The game time that ends 2HatchMuta whatever the enemy shows. Every 2HatchMuta win against
     * GrimHammer's SiegeExpand had ended by 14.3 minutes, so a Mutalisk build still running past
     * this is one the Mutalisks did not close out.
     */
    static final Time CLOCK_TRIGGER = new Time(13, 0);

    /** Lurkers 3HatchLurker has morphed before it hands over. Owner decision. */
    static final int LURKER_TRIGGER = 4;

    /** What opened a build's transition, written to the BUILD_ORDER_TRANSITION row. */
    public enum Trigger {
        /** 2HatchMuta saw {@value #GOLIATH_TRIGGER} living Goliaths. */
        GOLIATHS,
        /** 2HatchMuta reached {@link #CLOCK_TRIGGER}. */
        CLOCK,
        /** 3HatchLurker morphed {@value #LURKER_TRIGGER} Lurkers. */
        LURKERS
    }

    private LurkerDefilerUltraTransition() {
    }

    /**
     * The 2HatchMuta trigger: enough Goliaths to shut the Mutalisks out, or the clock.
     *
     * @param enemyGoliaths living enemy Goliaths we have observed
     * @param gameTime current game time
     * @return the trigger that holds, Goliaths first, or null while neither does
     */
    static Trigger twoHatchMutaTrigger(int enemyGoliaths, Time gameTime) {
        if (enemyGoliaths >= GOLIATH_TRIGGER) {
            return Trigger.GOLIATHS;
        }
        if (gameTime.getFrames() >= CLOCK_TRIGGER.getFrames()) {
            return Trigger.CLOCK;
        }
        return null;
    }

    /**
     * The 3HatchLurker trigger. Lurkers morphed rather than living, so a build that keeps losing
     * its Lurkers still hands over once it has made them.
     *
     * @param lurkersMorphed Lurkers this game has produced
     * @return {@link Trigger#LURKERS} once enough have been morphed, else null
     */
    static Trigger threeHatchLurkerTrigger(int lurkersMorphed) {
        return lurkersMorphed >= LURKER_TRIGGER ? Trigger.LURKERS : null;
    }

    /**
     * The economy gate every transition waits on.
     *
     * @param livingDrones Drones alive, not counting eggs or plans
     * @param bases bases with a hatchery of ours
     * @return true once both terms are met
     */
    static boolean economyReady(int livingDrones, int bases) {
        return livingDrones >= ECONOMY_DRONES && bases >= ECONOMY_BASES;
    }

    /**
     * Whether a build with this trigger hands over now.
     *
     * <p>The race term is the one {@link BuildOrder#shouldTransition} applies by default, kept so
     * no handover happens while the opponent's race is unresolved.
     *
     * @param trigger the build's own trigger, or null while it does not hold
     * @param raceKnown whether the opponent's race is resolved
     * @param livingDrones Drones alive
     * @param bases bases with a hatchery of ours
     * @return true when the race is known, the trigger holds and the economy gate is met
     */
    static boolean shouldEnter(Trigger trigger, boolean raceKnown, int livingDrones, int bases) {
        return raceKnown && trigger != null && economyReady(livingDrones, bases);
    }

    /**
     * Whether the named build hands over this frame, writing a BUILD_ORDER_TRANSITION row when it
     * does.
     *
     * <p>InformationManager swaps the active build on the frame this returns true, so the row is
     * written once per transition.
     *
     * @param gameState current game state
     * @param from the name of the build handing over
     * @param trigger the build's own trigger, or null while it does not hold
     * @return true when the build hands over now
     */
    static boolean shouldEnter(GameState gameState, String from, Trigger trigger) {
        boolean enter = shouldEnter(trigger, gameState.getOpponentRace() != Race.Unknown,
                gameState.ourLivingUnitCount(UnitType.Zerg_Drone),
                gameState.getBaseData().currentBaseCount());
        if (enter) {
            PlanEvents.buildOrderTransition(label(from, trigger));
        }
        return enter;
    }

    /**
     * The item the BUILD_ORDER_TRANSITION row carries: the build handing over, the build taking
     * over, and the trigger, as {@code 2HatchMuta>LurkerDefilerUltra:GOLIATHS}.
     */
    static String label(String from, Trigger trigger) {
        return from + ">" + LurkerDefilerUltra.NAME + ":" + trigger;
    }

    /**
     * @return the single candidate every transitioning build offers
     */
    static Set<BuildOrder> candidates() {
        Set<BuildOrder> candidates = new HashSet<>();
        candidates.add(new LurkerDefilerUltra());
        return candidates;
    }
}
