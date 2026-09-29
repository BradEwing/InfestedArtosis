package info.tracking;

import bwapi.UnitType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The garrison a set of enemy Bunkers is believed to hold, from the garrison actually seen.
 *
 * <p>A Bunker whose garrison was estimated from its fire, or proven empty, within {@link #TRUST_FRAMES} holds that
 * estimate, see {@link BunkerGarrisonEstimator}. Every other Bunker holds a share of the Bunker occupants known to be
 * alive that the trusted Bunkers have not already claimed, up to a full Bunker each, filled in the order given. The
 * garrisons together therefore never exceed the infantry known to exist, so two Bunkers sharing four Marines are
 * never read as eight.
 */
public final class BunkerGarrison {

    public static final int MAX_GARRISON = 4;

    /**
     * Frames a garrison estimate stays trusted after the Bunker was last checked: 120, the point at which the combat
     * sim's garrison modifier has decayed an unobserved estimate fully back to a full Bunker.
     */
    public static final int TRUST_FRAMES = 120;

    public static final Set<UnitType> OCCUPANTS = Collections.unmodifiableSet(EnumSet.of(UnitType.Terran_Marine,
            UnitType.Terran_Firebat, UnitType.Terran_Ghost, UnitType.Terran_Medic));

    private BunkerGarrison() {
    }

    /**
     * The estimate a Bunker's garrison is read at, or -1 when there is none to trust.
     *
     * @param loadedCount the Bunker's last garrison estimate, -1 while unknown
     * @param lastCheckFrame frame the Bunker was last seen with its estimate current
     * @param currentFrame current frame
     * @return the estimate while it is within {@link #TRUST_FRAMES} of its last check, else -1
     */
    public static int trustedEstimate(int loadedCount, int lastCheckFrame, int currentFrame) {
        if (loadedCount < 0 || lastCheckFrame < 0) {
            return -1;
        }
        return currentFrame - lastCheckFrame <= TRUST_FRAMES ? loadedCount : -1;
    }

    /**
     * Garrison believed to sit in each Bunker.
     *
     * @param trustedEstimates one entry per Bunker, its trusted estimate or -1 when unknown
     * @param livingOccupants Bunker occupants known to be alive
     * @return the believed garrison of each Bunker, in the same order
     */
    public static int[] believed(List<Integer> trustedEstimates, int livingOccupants) {
        int[] garrisons = new int[trustedEstimates.size()];
        int remaining = Math.max(livingOccupants, 0);
        for (int i = 0; i < garrisons.length; i++) {
            int estimate = trustedEstimates.get(i);
            if (estimate < 0) {
                continue;
            }
            garrisons[i] = Math.min(Math.min(estimate, MAX_GARRISON), remaining);
            remaining -= garrisons[i];
        }
        for (int i = 0; i < garrisons.length; i++) {
            if (trustedEstimates.get(i) >= 0) {
                continue;
            }
            garrisons[i] = Math.min(MAX_GARRISON, remaining);
            remaining -= garrisons[i];
        }
        return garrisons;
    }

    /**
     * Total garrison believed to sit in a set of Bunkers, see {@link #believed}.
     *
     * @param trustedEstimates one entry per Bunker, its trusted estimate or -1 when unknown
     * @param livingOccupants Bunker occupants known to be alive
     * @return the sum of the believed garrisons
     */
    public static int believedTotal(List<Integer> trustedEstimates, int livingOccupants) {
        int total = 0;
        for (int garrison : believed(trustedEstimates, livingOccupants)) {
            total += garrison;
        }
        return total;
    }
}
