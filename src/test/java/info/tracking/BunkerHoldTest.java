package info.tracking;

import bwapi.Position;
import bwapi.Race;
import bwapi.UnitType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import telemetry.BunkerAdvanceEvent;
import telemetry.BunkerAttackEvent;
import telemetry.BunkerLossEvent;
import telemetry.BunkerSink;
import telemetry.BunkerStanceEvent;
import telemetry.BunkerTelemetry;
import util.Time;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BunkerHoldTest {

    private final List<String> holds = new ArrayList<>();

    @AfterEach
    void clearSink() {
        BunkerTelemetry.clear();
    }

    private void recordHolds() {
        BunkerTelemetry.register(new BunkerSink() {
            @Override
            public void onAdvance(BunkerAdvanceEvent event) {
            }

            @Override
            public void onHold(int frame, String event, String reason) {
                holds.add(frame + ":" + event + ":" + reason);
            }

            @Override
            public void onLoss(BunkerLossEvent event) {
            }

            @Override
            public void onAttack(BunkerAttackEvent event) {
            }

            @Override
            public void onStance(BunkerStanceEvent event) {
            }
        });
    }

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
        recordHolds();
        ObservedUnit bunker = completedBunker();
        StrategyTracker tracker = trackerHolding(bunker);

        tracker.applyBunkerHolds(true, false, 1000);
        assertTrue(tracker.isBunkerHeld());
        assertFalse(tracker.isBunkerBroken());

        bunker.setDestroyedFrame(new Time(2000));
        tracker.applyBunkerHolds(false, false, 2000);

        assertFalse(tracker.isBunkerHeld());
        assertTrue(tracker.isBunkerBroken());
        assertEquals(2, holds.size());
        assertEquals("1000:HOLD_START:NATURAL", holds.get(0));
        assertEquals("2000:HOLD_END:BROKEN", holds.get(1));
    }

    @Test
    void aHoldThatClearsWithNoBunkerDestroyedIsReportedClearedAndNotBroken() {
        recordHolds();
        StrategyTracker tracker = trackerHolding(completedBunker());

        tracker.applyBunkerHolds(false, true, 1000);
        tracker.applyBunkerHolds(false, false, 1500);

        assertFalse(tracker.isBunkerBroken());
        assertEquals("1000:HOLD_START:MAIN", holds.get(0));
        assertEquals("1500:HOLD_END:CLEARED", holds.get(1));
    }

    @Test
    void aHoldIsReportedOncePerChangeNotPerFrame() {
        recordHolds();
        StrategyTracker tracker = trackerHolding(completedBunker());

        tracker.applyBunkerHolds(true, true, 1000);
        tracker.applyBunkerHolds(true, true, 1001);
        tracker.applyBunkerHolds(true, false, 1002);

        assertEquals(1, holds.size());
        assertEquals("1000:HOLD_START:NATURAL+MAIN", holds.get(0));
    }

    @Test
    void aBunkerThatNeverCompletedDoesNotCountAsBroken() {
        ObservedUnit unfinished = ObservedUnitFixture.observedUnit(UnitType.Terran_Bunker, new Position(1000, 1000),
                new Time(100));
        StrategyTracker tracker = trackerHolding(unfinished);
        unfinished.setDestroyedFrame(new Time(300));

        tracker.applyBunkerHolds(false, false, 300);

        assertFalse(tracker.isBunkerBroken());
    }
}
