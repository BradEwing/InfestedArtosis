package info.tracking;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import util.Time;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the null filter every position query in ObservedUnitTracker runs through, and the completion stamp a
 * type change drops. Standing up the state that produces a null - a living building whose location
 * clearLastKnownLocationsAt() has cleared - needs a live bwapi Unit, and the project has no mocking framework,
 * so the filter is exercised directly and tracked units come from ObservedUnitFixture.
 */
class ObservedUnitTrackerTest {

    private static final Position KNOWN = new Position(320, 640);
    private static final Time DRONE_OBSERVED = new Time(1400);
    private static final Time DRONE_COMPLETED = new Time(1496);
    private static final Time POOL_COMPLETED = new Time(2801);
    private static final Time WINDOW = new Time(1, 52);

    @Test
    void recentWorkersOfAnyRaceAreFoundInsideTheArea() {
        for (UnitType worker : new UnitType[] {UnitType.Protoss_Probe, UnitType.Terran_SCV, UnitType.Zerg_Drone}) {
            ObservedUnit observed = ObservedUnitFixture.observedUnit(worker, new Time(5000));
            observed.setLastKnownLocation(KNOWN);
            ObservedUnitTracker tracker = ObservedUnitFixture.trackerHolding(observed);

            Set<Position> positions = tracker.getRecentWorkerPositionsIn(tile -> true, 5100, 240);

            assertEquals(1, positions.size(), worker.toString());
            assertTrue(positions.contains(KNOWN));
        }
    }

    @Test
    void recentWorkerQueryLeavesOutStaleWorkersOtherAreasAndNonWorkers() {
        ObservedUnit probe = ObservedUnitFixture.observedUnit(UnitType.Protoss_Probe, new Time(5000));
        probe.setLastKnownLocation(KNOWN);
        ObservedUnitTracker tracker = ObservedUnitFixture.trackerHolding(probe);

        assertTrue(tracker.getRecentWorkerPositionsIn(tile -> true, 5241, 240).isEmpty());
        assertEquals(1, tracker.getRecentWorkerPositionsIn(tile -> true, 5240, 240).size());
        assertTrue(tracker.getRecentWorkerPositionsIn(tile -> false, 5100, 240).isEmpty());

        ObservedUnit zealot = ObservedUnitFixture.observedUnit(UnitType.Protoss_Zealot, new Time(5000));
        zealot.setLastKnownLocation(KNOWN);
        assertTrue(ObservedUnitFixture.trackerHolding(zealot)
                .getRecentWorkerPositionsIn(tile -> true, 5100, 240).isEmpty());
    }

    @Test
    void aWorkerWhosePositionWasClearedIsLeftOut() {
        ObservedUnit probe = ObservedUnitFixture.observedUnit(UnitType.Protoss_Probe, new Time(5000));

        assertTrue(ObservedUnitFixture.trackerHolding(probe)
                .getRecentWorkerPositionsIn(tile -> true, 5100, 240).isEmpty());
    }

    @Test
    void clearedLocationIsDroppedRatherThanReturnedAsNull() {
        Set<Position> positions = ObservedUnitTracker.knownPositions(Stream.of(KNOWN, null));

        assertFalse(positions.contains(null));
        assertEquals(1, positions.size());
        assertTrue(positions.contains(KNOWN));
    }

    @Test
    void allLocationsClearedProducesAnEmptySet() {
        assertTrue(ObservedUnitTracker.knownPositions(Stream.of(null, null)).isEmpty());
    }

    @Test
    void typeChangeDropsTheCompletionStamp() {
        ObservedUnit morphed = completedDrone();

        ObservedUnitTracker.updateUnitTypeChange(morphed, UnitType.Zerg_Spawning_Pool);

        assertEquals(UnitType.Zerg_Spawning_Pool, morphed.getUnitType());
        assertFalse(morphed.isCompleted());
        assertNull(morphed.getCompletedFrame());
    }

    @Test
    void morphedUnitIsStampedAtTheFrameItIsNextObservedComplete() {
        ObservedUnit morphed = completedDrone();

        ObservedUnitTracker.updateUnitTypeChange(morphed, UnitType.Zerg_Spawning_Pool);
        morphed.markCompleted(POOL_COMPLETED);

        assertTrue(morphed.isCompleted());
        assertEquals(POOL_COMPLETED, morphed.getCompletedFrame());
    }

    @Test
    void unchangedTypeKeepsTheCompletionStamp() {
        ObservedUnit drone = completedDrone();

        ObservedUnitTracker.updateUnitTypeChange(drone, UnitType.Zerg_Drone);

        assertTrue(drone.isCompleted());
        assertEquals(DRONE_COMPLETED, drone.getCompletedFrame());
    }

    @Test
    void completedCountIgnoresAUnitThatHasSinceChangedType() {
        ObservedUnit morphed = completedDrone();
        ObservedUnitTracker tracker = ObservedUnitFixture.trackerHolding(morphed);

        assertEquals(1, tracker.getUnitTypeCountCompletedBeforeTime(UnitType.Zerg_Drone, WINDOW));

        ObservedUnitTracker.updateUnitTypeChange(morphed, UnitType.Zerg_Spawning_Pool);

        assertEquals(0, tracker.getUnitTypeCountCompletedBeforeTime(UnitType.Zerg_Spawning_Pool, WINDOW));
    }

    @Test
    void hatcheryThatMorphedIntoALairIsObservedAsLairTech() {
        ObservedUnit hatchery = ObservedUnitFixture.observedUnit(UnitType.Zerg_Hatchery, DRONE_OBSERVED);
        ObservedUnitTracker tracker = ObservedUnitFixture.trackerHolding(hatchery);

        assertFalse(tracker.hasObservedAnyBeforeTime(WINDOW, UnitType.Zerg_Lair, UnitType.Zerg_Spire));

        ObservedUnitFixture.changeType(hatchery, UnitType.Zerg_Lair);

        assertTrue(tracker.hasObservedAnyBeforeTime(WINDOW, UnitType.Zerg_Lair, UnitType.Zerg_Spire));
    }

    @Test
    void destroyedUnitStillCountsAsObserved() {
        ObservedUnit spire = ObservedUnitFixture.observedUnit(UnitType.Zerg_Spire, DRONE_OBSERVED);
        spire.setDestroyedFrame(POOL_COMPLETED);
        ObservedUnitTracker tracker = ObservedUnitFixture.trackerHolding(spire);

        assertTrue(tracker.hasObservedAnyBeforeTime(POOL_COMPLETED, UnitType.Zerg_Lair, UnitType.Zerg_Spire));
    }

    @Test
    void aDestroyedUnitTheFilterAcceptsHasBeenObserved() {
        ObservedUnit corsair = ObservedUnitFixture.observedUnit(UnitType.Protoss_Corsair, DRONE_OBSERVED);
        corsair.setDestroyedFrame(POOL_COMPLETED);
        ObservedUnitTracker tracker = ObservedUnitFixture.trackerHolding(corsair);

        assertTrue(tracker.hasObservedAny(type -> type == UnitType.Protoss_Corsair));
        assertFalse(tracker.hasObservedAny(type -> type == UnitType.Protoss_Scout));
    }

    @Test
    void unitFirstObservedAfterTheTimeIsNotCounted() {
        ObservedUnit lair = ObservedUnitFixture.observedUnit(UnitType.Zerg_Lair, POOL_COMPLETED);
        ObservedUnitTracker tracker = ObservedUnitFixture.trackerHolding(lair);

        assertFalse(tracker.hasObservedAnyBeforeTime(WINDOW, UnitType.Zerg_Lair));
    }

    private static ObservedUnit completedDrone() {
        ObservedUnit drone = ObservedUnitFixture.observedUnit(UnitType.Zerg_Drone, DRONE_OBSERVED);
        drone.markCompleted(DRONE_COMPLETED);
        return drone;
    }
}
