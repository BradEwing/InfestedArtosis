package unit.squad;

import bwapi.Position;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OverlordParkingTest {

    private static final Position MAIN = new Position(100, 100);
    private static final Position NEAR = new Position(500, 500);
    private static final Position FAR = new Position(2000, 2000);

    @Test
    void picksTheNearerOfTwoSpores() {
        List<Position> spores = Arrays.asList(FAR, NEAR);
        assertEquals(NEAR, OverlordParking.pickAnchor(new Position(600, 600), spores, MAIN));
        assertEquals(FAR, OverlordParking.pickAnchor(new Position(1900, 1900), spores, MAIN));
    }

    @Test
    void fallsBackToTheMainWithNoSpore() {
        assertEquals(MAIN, OverlordParking.pickAnchor(new Position(600, 600), Collections.emptyList(), MAIN));
    }

    @Test
    void aDeadSporeReroutesToTheNextNearestThenToMain() {
        Position overlord = new Position(600, 600);
        assertEquals(FAR, OverlordParking.pickAnchor(overlord, Collections.singletonList(FAR), MAIN));
        assertEquals(MAIN, OverlordParking.pickAnchor(overlord, Collections.emptyList(), MAIN));
    }

    @Test
    void aTieGoesToTheFirstListedSpore() {
        Position a = new Position(0, 100);
        Position b = new Position(200, 100);
        assertEquals(a, OverlordParking.pickAnchor(new Position(100, 100), Arrays.asList(a, b), MAIN));
    }

    @Test
    void reasonNamesWhyTheAnchorChanged() {
        List<Position> spores = Collections.singletonList(NEAR);
        assertEquals(OverlordParking.Reason.ASSIGNED, OverlordParking.reason(null, spores, MAIN));
        assertEquals(OverlordParking.Reason.SPORE_COMPLETED, OverlordParking.reason(MAIN, spores, MAIN));
        assertEquals(OverlordParking.Reason.NEARER_SPORE, OverlordParking.reason(NEAR, spores, MAIN));
        assertEquals(OverlordParking.Reason.SPORE_LOST,
                OverlordParking.reason(FAR, spores, MAIN));
        assertEquals(OverlordParking.Reason.SPORE_LOST,
                OverlordParking.reason(FAR, Collections.emptyList(), MAIN));
    }

    @Test
    void nearestSporeDistanceIsMinusOneWithNone() {
        assertEquals(-1, OverlordParking.nearestSporeDistance(MAIN, Collections.emptyList()));
        assertEquals(500, OverlordParking.nearestSporeDistance(new Position(0, 0),
                Collections.singletonList(new Position(300, 400))), 1e-9);
    }
}
