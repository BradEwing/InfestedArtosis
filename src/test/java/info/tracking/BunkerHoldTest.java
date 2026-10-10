package info.tracking;

import bwapi.Position;
import bwapi.Race;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import util.Time;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BunkerHoldTest {

    private static ObservedUnit completedBunker() {
        ObservedUnit bunker = ObservedUnitFixture.observedUnit(UnitType.Terran_Bunker, new Position(1000, 1000),
                new Time(100));
        bunker.markCompleted(new Time(400));
        return bunker;
    }

    private static StrategyTracker trackerHolding(ObservedUnit bunker) {
        return new StrategyTracker(null, Race.Terran, ObservedUnitFixture.trackerHolding(bunker), null, null, null,
                null);
    }

    @Test
    void aBunkerSeenDestroyedLiftsTheHoldAndMarksItBroken() {
        ObservedUnit bunker = completedBunker();
        StrategyTracker tracker = trackerHolding(bunker);

        tracker.applyBunkerHolds(true, false);
        assertTrue(tracker.isBunkerHeld());
        assertFalse(tracker.isBunkerBroken());

        bunker.setDestroyedFrame(new Time(2000));
        tracker.applyBunkerHolds(false, false);

        assertFalse(tracker.isBunkerHeld());
        assertTrue(tracker.isBunkerBroken());
    }

    @Test
    void aHoldThatClearsWithNoBunkerDestroyedIsNotBroken() {
        StrategyTracker tracker = trackerHolding(completedBunker());

        tracker.applyBunkerHolds(false, true);
        assertTrue(tracker.isBunkerHeld());
        tracker.applyBunkerHolds(false, false);

        assertFalse(tracker.isBunkerHeld());
        assertFalse(tracker.isBunkerBroken());
    }

    @Test
    void aBunkerThatNeverCompletedDoesNotCountAsBroken() {
        ObservedUnit unfinished = ObservedUnitFixture.observedUnit(UnitType.Terran_Bunker, new Position(1000, 1000),
                new Time(100));
        StrategyTracker tracker = trackerHolding(unfinished);
        unfinished.setDestroyedFrame(new Time(300));

        tracker.applyBunkerHolds(false, false);

        assertFalse(tracker.isBunkerBroken());
    }
}
