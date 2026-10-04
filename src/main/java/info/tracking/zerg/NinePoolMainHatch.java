package info.tracking.zerg;

import info.tracking.StrategyDetectionContext;
import util.Time;

/**
 * A second Hatchery inside the main and no natural, the shape of 9PoolGasHatchSpeed8D: a second depot stands in the
 * enemy main's area, and no natural depot and no Lair tech have been seen. It does not read the Pool's timing, so it
 * fires on any one-base two-hatch opening, whatever the opener. It reads the same two-hatch-on-one-base ling build as
 * {@link TwoHatchLing} from the in-main Hatchery onward, without waiting for the Zerglings to mass.
 */
public class NinePoolMainHatch extends ZergBaseStrategy {

    static final Time DETECTION_CUTOFF = TwoHatchLing.DETECTION_CUTOFF;

    private boolean naturalDepotSeen = false;

    public NinePoolMainHatch() {
        super("9PoolMainHatch");
    }

    @Override
    public boolean isDetected(StrategyDetectionContext context) {
        Time time = context.getTime();
        if (time.greaterThan(DETECTION_CUTOFF)) {
            return false;
        }
        boolean naturalTaken = observeNaturalDepot(context.enemyNaturalHasDepot());
        return matches(context.enemyHasExtraDepotInMainArea(),
                naturalTaken,
                context.getTracker().hasObservedAnyBeforeTime(time, TwoHatchLing.LAIR_TECH));
    }

    /**
     * Latches a sighting of a living depot at the enemy natural. The tracker forgets a destroyed depot's position,
     * so the latch is what keeps a natural that later died counting as taken.
     *
     * @param naturalHasDepotNow a living enemy depot stands at the enemy natural this frame
     * @return whether an enemy natural depot has been seen on any frame so far
     */
    boolean observeNaturalDepot(boolean naturalHasDepotNow) {
        naturalDepotSeen = naturalDepotSeen || naturalHasDepotNow;
        return naturalDepotSeen;
    }

    /**
     * The detection rule over the scouted evidence.
     *
     * @param extraDepotInMain a living enemy depot stands in the enemy main's area besides the main depot
     * @param naturalDepotSeen an enemy depot has been seen at the enemy natural
     * @param lairTechSeen an enemy Lair, Hive, Spire, Mutalisk or Hydralisk Den was ever observed
     */
    static boolean matches(boolean extraDepotInMain, boolean naturalDepotSeen, boolean lairTechSeen) {
        return extraDepotInMain
                && !naturalDepotSeen
                && !lairTechSeen;
    }
}
