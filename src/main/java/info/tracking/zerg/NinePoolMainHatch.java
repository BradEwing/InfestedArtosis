package info.tracking.zerg;

import info.tracking.StrategyDetectionContext;
import util.Time;

/**
 * A 9 pool followed by a second Hatchery inside the main and no natural, the shape of 9PoolGasHatchSpeed8D: the
 * {@link ZergOpenerReading} reads 9Pool, a second depot stands in the enemy main's area, and no natural depot and
 * no Lair tech have been seen. It reads the same two-hatch-on-one-base ling build as {@link TwoHatchLing} from the
 * in-main Hatchery onward, without waiting for the Zerglings to mass.
 */
public class NinePoolMainHatch extends ZergBaseStrategy {

    static final Time DETECTION_CUTOFF = TwoHatchLing.DETECTION_CUTOFF;

    private final ZergOpenerReading reading;

    public NinePoolMainHatch(ZergOpenerReading reading) {
        super("9PoolMainHatch");
        this.reading = reading;
    }

    @Override
    public boolean isDetected(StrategyDetectionContext context) {
        Time time = context.getTime();
        if (time.greaterThan(DETECTION_CUTOFF)) {
            return false;
        }
        ZergOpener opener = reading.read(context);
        return matches(opener,
                context.enemyHasExtraDepotInMainArea(),
                reading.naturalDepotSeen(),
                context.getTracker().hasObservedAnyBeforeTime(time, TwoHatchLing.LAIR_TECH));
    }

    /**
     * The detection rule over the scouted evidence.
     *
     * @param opener the opener read so far, or null
     * @param extraDepotInMain a living enemy depot stands in the enemy main's area besides the main depot
     * @param naturalDepotSeen an enemy depot has been seen at the enemy natural
     * @param lairTechSeen an enemy Lair, Hive, Spire, Mutalisk or Hydralisk Den was ever observed
     */
    static boolean matches(ZergOpener opener, boolean extraDepotInMain, boolean naturalDepotSeen,
                           boolean lairTechSeen) {
        return opener == ZergOpener.NINE_POOL
                && extraDepotInMain
                && !naturalDepotSeen
                && !lairTechSeen;
    }
}
