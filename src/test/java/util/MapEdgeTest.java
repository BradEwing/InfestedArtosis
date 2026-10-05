package util;

import bwapi.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapEdgeTest {

    private static final int W = 4096;
    private static final int H = 4096;

    @Test
    void aFlightInTheOpenIsNotChanged() {
        Position end = MapEdge.flee(new Position(2000, 2000), 1, 0, 256, W, H);

        assertEquals(new Position(2256, 2000), end);
    }

    @Test
    void aFlightThatWouldEndOnTheEdgeStopsTheInsetShort() {
        Position end = MapEdge.toward(new Position(1000, 3900), new Position(1000, 4096), W, H);

        assertEquals(new Position(1000, H - 1 - MapEdge.INSET), end);
    }

    @Test
    void aFlightIntoTheBottomEdgeFromAnEdgeParkedFlyerSlidesAlongTheEdge() {
        Position from = new Position(1583, 4036);

        Position end = MapEdge.flee(from, 0.1, 1, 256, W, H);

        assertTrue(MapEdge.inside(end, W, H, MapEdge.INSET));
        assertEquals(256, from.getDistance(end), 5);
        assertTrue(end.getX() > from.getX());
    }

    @Test
    void aFlightStraightIntoTheEdgeSlidesTowardTheSideWithRoom() {
        Position left = MapEdge.flee(new Position(500, 4036), 0, 1, 256, W, H);
        Position right = MapEdge.flee(new Position(3500, 4036), 0, 1, 256, W, H);

        assertTrue(left.getX() > 500);
        assertTrue(right.getX() < 3500);
    }

    @Test
    void aFlightIntoACornerSlidesAlongTheEdgeItOpposesLess() {
        Position from = new Position(20, 30);

        Position end = MapEdge.flee(from, -1, -0.6, 256, W, H);

        assertTrue(MapEdge.inside(end, W, H, MapEdge.INSET));
        assertTrue(from.getDistance(end) >= 128);
        assertEquals(MapEdge.INSET, end.getX());
        assertTrue(end.getY() > from.getY());
    }

    @Test
    void aFlightAwayFromTheEdgeIsNotSlid() {
        Position from = new Position(1583, 4036);

        Position end = MapEdge.flee(from, 0, -1, 256, W, H);

        assertEquals(new Position(1583, 3780), end);
    }

    @Test
    void aTargetBeyondTheMapIsHeldInsideTheInset() {
        Position end = MapEdge.inset(new Position(-50, 9000), W, H);

        assertEquals(new Position(MapEdge.INSET, H - 1 - MapEdge.INSET), end);
    }

    @Test
    void aFlightOfNoLengthStaysInsideTheInset() {
        Position end = MapEdge.flee(new Position(10, 4090), 0, 0, 256, W, H);

        assertEquals(new Position(MapEdge.INSET, H - 1 - MapEdge.INSET), end);
    }

    @Test
    void theEdgeBandIsTheBandOfPixelsAroundTheMap() {
        assertTrue(MapEdge.inBand(new Position(1583, 4036), W, H));
        assertTrue(MapEdge.inBand(new Position(MapEdge.BAND - 1, 2000), W, H));
        assertFalse(MapEdge.inBand(new Position(MapEdge.BAND, 2000), W, H));
        assertFalse(MapEdge.inBand(new Position(2000, H - MapEdge.BAND - 1), W, H));
        assertTrue(MapEdge.inBand(new Position(2000, H - MapEdge.BAND), W, H));
    }

    @Test
    void aReleaseGoesStraightInFromTheEdgeAndFromACornerInFromBoth() {
        assertEquals(new Position(1583, H - 1 - MapEdge.RELEASE_DEPTH),
                MapEdge.release(new Position(1583, 4036), W, H));
        assertEquals(new Position(MapEdge.RELEASE_DEPTH, MapEdge.RELEASE_DEPTH),
                MapEdge.release(new Position(10, 20), W, H));
        assertFalse(MapEdge.inBand(MapEdge.release(new Position(10, 20), W, H), W, H));
        assertNotEquals(MapEdge.release(new Position(10, 20), W, H), new Position(10, 20));
    }

    @Test
    void aPointIsInsideWhenItKeepsTheMarginFromEveryEdge() {
        assertTrue(MapEdge.inside(new Position(32, 32), W, H, 32));
        assertFalse(MapEdge.inside(new Position(31, 100), W, H, 32));
        assertFalse(MapEdge.inside(new Position(100, H - 32), W, H, 32));
    }
}
