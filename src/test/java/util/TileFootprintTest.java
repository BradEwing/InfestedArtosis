package util;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TileFootprintTest {

    private static final TileFootprint BARRACKS = new TileFootprint(UnitType.Terran_Barracks, new TilePosition(10, 10));

    @Test
    void aBuildingsCentreMapsBackToItsTopLeftTile() {
        TileFootprint barracks = TileFootprint.centredAt(UnitType.Terran_Barracks, new Position(384, 368));
        TileFootprint depot = TileFootprint.centredAt(UnitType.Terran_Supply_Depot, new Position(496, 352));

        assertEquals(new TilePosition(10, 10), barracks.getTopLeft());
        assertEquals(new TilePosition(14, 10), depot.getTopLeft());
    }

    @Test
    void theCentreTileIsTheTileTheReportedPositionRoundsInto() {
        TileFootprint depot = TileFootprint.centredAt(UnitType.Terran_Supply_Depot, new Position(496, 352));

        assertEquals(new Position(496, 352).toTilePosition(), depot.centreTile());
        assertEquals(new Position(384, 368).toTilePosition(), BARRACKS.centreTile());
    }

    @Test
    void edgeAdjacentFootprintsHaveNoGap() {
        assertEquals(0, BARRACKS.tileGap(depot(14, 10)));
        assertEquals(0, BARRACKS.tileGap(depot(10, 13)));
        assertEquals(0, BARRACKS.tileGap(depot(7, 10)));
    }

    @Test
    void cornerTouchingAndOverlappingFootprintsHaveNoGap() {
        assertEquals(0, BARRACKS.tileGap(depot(14, 13)));
        assertEquals(0, BARRACKS.tileGap(depot(12, 11)));
    }

    @Test
    void theGapCountsTheTilesBetweenAlongTheWidestAxis() {
        assertEquals(1, BARRACKS.tileGap(depot(15, 10)));
        assertEquals(1, BARRACKS.tileGap(depot(15, 14)));
        assertEquals(2, BARRACKS.tileGap(depot(16, 10)));
        assertEquals(2, BARRACKS.tileGap(depot(10, 15)));
        assertEquals(2, depot(16, 10).tileGap(BARRACKS));
    }

    private static TileFootprint depot(int left, int top) {
        return new TileFootprint(UnitType.Terran_Supply_Depot, new TilePosition(left, top));
    }
}
