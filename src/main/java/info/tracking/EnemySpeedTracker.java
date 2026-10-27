package info.tracking;

import bwapi.UnitType;
import bwapi.UpgradeType;
import info.ResourceCount;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks whether the enemy has the movement upgrade of a unit type: Metabolic Boost for Zerglings and Muscular
 * Augments for Hydralisks.
 *
 * <p>An upgrade is {@link Evidence#SEEN} once enough frames saw one of its units move faster than the type's
 * unupgraded top speed by {@link #SEEN_SPEED_MARGIN}. It is {@link Evidence#PLAUSIBLE} when no unit has been seen
 * fast but the timing allows it: the enemy's gas building, 100 gas mined by three workers, the structure that
 * researches it and the research time have all elapsed. Otherwise it is {@link Evidence#NONE}. SEEN is permanent.
 * PLAUSIBLE reads the frame the timing allows, so it holds from then on.
 *
 * <p>The timing is anchored on what we observed, so an enemy that took gas before we first saw it reads late, and one
 * whose gas building we never saw reads NONE until a unit is seen moving fast.
 */
public class EnemySpeedTracker {
    /** How far above the unupgraded top speed a unit must move to count as upgraded. */
    public static final double SEEN_SPEED_MARGIN = 1.15;

    /** Frames on which a faster than base unit must be seen before the upgrade counts as seen. */
    public static final int SEEN_CONFIRMATIONS = 3;

    /** Gas workers an Extractor holds, the most that can mine toward the upgrade. */
    public static final int GAS_WORKERS = 3;

    private static final int TIMING_REFRESH_FRAMES = 24;

    /** What is known about an enemy upgrade. */
    public enum Evidence {
        /** Neither seen nor plausible by timing. */
        NONE,
        /** Not seen, but the gas and research timing allows it. */
        PLAUSIBLE,
        /** A unit was seen moving faster than the unupgraded top speed. */
        SEEN
    }

    private final Map<UpgradeType, Integer> seenFrames = new HashMap<>();
    private final Map<UpgradeType, Integer> plausibleFrames = new HashMap<>();
    private final Map<UpgradeType, Integer> fastFrameCounts = new HashMap<>();
    private final Map<UpgradeType, Integer> lastFastFrame = new HashMap<>();
    private final Map<UpgradeType, Evidence> announced = new HashMap<>();
    private int lastRefreshFrame = -TIMING_REFRESH_FRAMES;

    /**
     * The upgrade that speeds up a unit type, among those this tracker follows.
     *
     * @param type the unit type
     * @return Metabolic Boost for Zerglings, Muscular Augments for Hydralisks, else null
     */
    public static UpgradeType upgradeFor(UnitType type) {
        if (type == UnitType.Zerg_Zergling) {
            return UpgradeType.Metabolic_Boost;
        }
        if (type == UnitType.Zerg_Hydralisk) {
            return UpgradeType.Muscular_Augments;
        }
        return null;
    }

    /**
     * The unit type an upgrade speeds up, among those this tracker follows.
     *
     * @param upgrade the upgrade
     * @return the unit type, or null for an upgrade not followed
     */
    public static UnitType unitFor(UpgradeType upgrade) {
        if (upgrade == UpgradeType.Metabolic_Boost) {
            return UnitType.Zerg_Zergling;
        }
        if (upgrade == UpgradeType.Muscular_Augments) {
            return UnitType.Zerg_Hydralisk;
        }
        return null;
    }

    /**
     * Whether a speed is above the unupgraded top speed of a type by the margin.
     *
     * @param type the unit type
     * @param speed pixels per frame, from the unit's velocity
     * @return true when the unit moves faster than an unupgraded one can
     */
    public static boolean exceedsBaseSpeed(UnitType type, double speed) {
        return upgradeFor(type) != null && speed > type.topSpeed() * SEEN_SPEED_MARGIN;
    }

    /**
     * The frames three gas workers need to mine the gas an upgrade costs.
     *
     * @param upgrade the upgrade
     * @return frames, rounded up
     */
    public static int gasFrames(UpgradeType upgrade) {
        return (int) Math.ceil(upgrade.gasPrice() / (ResourceCount.GAS_PER_FRAME_PER_WORKER * GAS_WORKERS));
    }

    /**
     * The earliest frame an upgrade can finish: its research starts once the gas is mined and the researching
     * structure is up, and takes the upgrade's research time.
     *
     * @param upgrade the upgrade
     * @param gasBuildingReadyFrame frame the enemy's first gas building was usable, or -1 when none was seen
     * @param researcherReadyFrame frame the structure that researches it was usable
     * @return the frame, or -1 when there is no gas building
     */
    public static int earliestFinishFrame(UpgradeType upgrade, int gasBuildingReadyFrame, int researcherReadyFrame) {
        if (gasBuildingReadyFrame < 0 || researcherReadyFrame < 0) {
            return -1;
        }
        int start = Math.max(gasBuildingReadyFrame + gasFrames(upgrade), researcherReadyFrame);
        return start + upgrade.upgradeTime();
    }

    /**
     * What the frames recorded say about an upgrade at a frame.
     *
     * @param seenFrame frame the upgrade was first seen, or -1
     * @param plausibleFrame earliest frame the timing allows it, or -1
     * @param frame the frame asked about
     * @return SEEN once seen, PLAUSIBLE once the timing allows it, else NONE
     */
    public static Evidence classify(int seenFrame, int plausibleFrame, int frame) {
        if (seenFrame >= 0 && frame >= seenFrame) {
            return Evidence.SEEN;
        }
        if (plausibleFrame >= 0 && frame >= plausibleFrame) {
            return Evidence.PLAUSIBLE;
        }
        return Evidence.NONE;
    }

    /**
     * Records one visible enemy unit's speed. A frame counts once however many units are fast on it.
     *
     * @param frame current frame
     * @param type the unit's type
     * @param speed pixels per frame, from the unit's velocity
     */
    public void observe(int frame, UnitType type, double speed) {
        UpgradeType upgrade = upgradeFor(type);
        if (upgrade == null || seenFrames.containsKey(upgrade) || !exceedsBaseSpeed(type, speed)) {
            return;
        }
        if (lastFastFrame.getOrDefault(upgrade, -1) == frame) {
            return;
        }
        lastFastFrame.put(upgrade, frame);
        int count = fastFrameCounts.merge(upgrade, 1, Integer::sum);
        if (count >= SEEN_CONFIRMATIONS) {
            seenFrames.put(upgrade, frame);
        }
    }

    /**
     * Reports the evidence of an upgrade the first time it reaches a level above what was last reported.
     *
     * @param frame current frame
     * @param upgrade Metabolic Boost or Muscular Augments
     * @return the new level, or null when nothing rose since the last call
     */
    public Evidence announce(int frame, UpgradeType upgrade) {
        Evidence now = evidence(upgrade, frame);
        Evidence reported = announced.getOrDefault(upgrade, Evidence.NONE);
        if (now.ordinal() <= reported.ordinal()) {
            return null;
        }
        announced.put(upgrade, now);
        return now;
    }

    /**
     * Refreshes the timing estimate from what the observed unit tracker holds. It reads the tracker every
     * {@link #TIMING_REFRESH_FRAMES} frames, until the estimate has been reached.
     *
     * @param frame current frame
     * @param tracker the enemy units observed
     */
    public void refreshTiming(int frame, ObservedUnitTracker tracker) {
        if (frame - lastRefreshFrame < TIMING_REFRESH_FRAMES) {
            return;
        }
        lastRefreshFrame = frame;
        int gasReady = tracker.earliestReadyFrame(UnitType.Zerg_Extractor);
        for (UpgradeType upgrade : new UpgradeType[] {UpgradeType.Metabolic_Boost, UpgradeType.Muscular_Augments}) {
            int before = plausibleFrames.getOrDefault(upgrade, -1);
            if (before >= 0 && frame >= before) {
                continue;
            }
            int researcherReady = researcherReadyFrame(upgrade, tracker);
            int finish = earliestFinishFrame(upgrade, gasReady, researcherReady);
            if (finish < 0) {
                continue;
            }
            plausibleFrames.put(upgrade, finish);
        }
    }

    private static int researcherReadyFrame(UpgradeType upgrade, ObservedUnitTracker tracker) {
        UnitType produced = unitFor(upgrade);
        int fallback = tracker.firstObservedFrame(produced);
        int ready = readyOrFallback(tracker, upgrade.whatUpgrades(), fallback);
        UnitType required = upgrade.whatsRequired();
        if (required != UnitType.None) {
            ready = Math.max(ready, readyOrFallback(tracker, required, fallback));
        }
        return ready;
    }

    private static int readyOrFallback(ObservedUnitTracker tracker, UnitType structure, int fallback) {
        int ready = tracker.earliestReadyFrame(structure);
        if (ready < 0) {
            return fallback;
        }
        return fallback < 0 ? ready : Math.min(ready, fallback);
    }

    /**
     * What is known about an enemy upgrade at a frame.
     *
     * @param upgrade Metabolic Boost or Muscular Augments; any other upgrade reads NONE
     * @param frame the frame asked about
     * @return the evidence
     */
    public Evidence evidence(UpgradeType upgrade, int frame) {
        return classify(seenFrame(upgrade), plausibleFrame(upgrade), frame);
    }

    /**
     * Whether the enemy has an upgrade, or plausibly has it.
     *
     * @param upgrade Metabolic Boost or Muscular Augments
     * @param frame the frame asked about
     * @return true when the evidence is SEEN or PLAUSIBLE
     */
    public boolean hasOrPlausiblyHas(UpgradeType upgrade, int frame) {
        return evidence(upgrade, frame) != Evidence.NONE;
    }

    /**
     * @param upgrade the upgrade
     * @return the frame a unit was first confirmed moving faster than base, or -1
     */
    public int seenFrame(UpgradeType upgrade) {
        return seenFrames.getOrDefault(upgrade, -1);
    }

    /**
     * @param upgrade the upgrade
     * @return the earliest frame the timing allows the enemy to have it, or -1 when it cannot be placed
     */
    public int plausibleFrame(UpgradeType upgrade) {
        return plausibleFrames.getOrDefault(upgrade, -1);
    }
}
