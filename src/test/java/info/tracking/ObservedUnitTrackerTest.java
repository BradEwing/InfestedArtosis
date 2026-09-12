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

    private static ObservedUnit completedDrone() {
        ObservedUnit drone = ObservedUnitFixture.observedUnit(UnitType.Zerg_Drone, DRONE_OBSERVED);
        drone.markCompleted(DRONE_COMPLETED);
        return drone;
    }
}
