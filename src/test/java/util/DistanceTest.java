package util;

import bwapi.Position;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DistanceTest {

    private static final Position ORIGIN = new Position(0, 0);
    private static final Position NEAR = new Position(100, 0);
    private static final Position FAR = new Position(1000, 0);

    @Test
    void closestPositionPicksTheNearestCandidate() {
        assertEquals(NEAR, Distance.closestPosition(ORIGIN, Arrays.asList(FAR, NEAR)));
    }

    @Test
    void closestPositionSkipsUnknownCandidates() {
        assertEquals(FAR, Distance.closestPosition(ORIGIN, Arrays.asList(null, FAR)));
    }

    @Test
    void closestPositionIsNullWhenEveryCandidateIsUnknown() {
        assertNull(Distance.closestPosition(ORIGIN, Collections.singletonList(null)));
    }

    @Test
    void closestPositionIsNullWithoutCandidates() {
        assertNull(Distance.closestPosition(ORIGIN, Collections.emptyList()));
    }
}
