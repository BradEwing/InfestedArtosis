package unit.squad;

import bwapi.Race;
import info.tracking.ObservedUnitTracker;
import info.tracking.StrategyTracker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BunkerSwitchTest {

    private static StrategyTracker holding(boolean natural, boolean main) {
        StrategyTracker tracker = new StrategyTracker(null, Race.Terran, new ObservedUnitTracker(), null, null, null,
                null);
        tracker.applyBunkerHolds(natural, main, 5000);
        return tracker;
    }

    @Test
    void aHeldBunkerCountsAgainstASquadsMakeupWhileTheGatesAreOn() {
        assertTrue(ContainmentEvaluator.bunkerHeld(true, holding(true, false)));
        assertTrue(ContainmentEvaluator.bunkerHeld(true, holding(false, true)));
    }

    @Test
    void withTheBunkerGatesSwitchedOffNoHeldBunkerCounts() {
        assertFalse(ContainmentEvaluator.bunkerHeld(false, holding(true, true)));
    }

    @Test
    void noHoldAndNoTrackerCountForNothing() {
        assertFalse(ContainmentEvaluator.bunkerHeld(true, holding(false, false)));
        assertFalse(ContainmentEvaluator.bunkerHeld(true, null));
    }
}
