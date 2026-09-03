package info.tracking;

import bwapi.Position;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the null filter every position query in ObservedUnitTracker runs through. Standing up the state
 * that produces a null - a living building whose location clearLastKnownLocationsAt() has cleared - needs a
 * live bwapi Unit, and the project has no mocking framework, so the filter is exercised directly.
 */
class ObservedUnitTrackerTest {

    private static final Position KNOWN = new Position(320, 640);

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
}
