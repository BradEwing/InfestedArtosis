package info.tracking.terran;

import bwapi.TilePosition;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import util.TileFootprint;
import util.Time;

import java.util.Arrays;
import java.util.function.Predicate;

import static info.tracking.terran.TerranWallNaturalTest.footprint;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The enemy main's depot is centred on tile (40, 40) and its one chokepoint on tile (60, 60).
 */
class TerranWallMainTest {

    private static final TilePosition MAIN_DEPOT_CENTRE = new TilePosition(40, 40);

    private static final TilePosition CHOKE = new TilePosition(60, 60);

    private static final Time EARLY = new Time(4, 0);

    private static final Time AFTER_CUTOFF = new Time(TerranWall.CUTOFF.getFrames() + 1);

    @Test
    void aBarracksAndDepotTouchingAtTheMainChokeIsAWall() {
        assertTrue(detects(EARLY, barracks(58, 58), depot(62, 58)));
    }

    @Test
    void aBarracksAndDepotOneTileApartAtTheMainChokeIsAWall() {
        assertTrue(detects(EARLY, barracks(58, 58), depot(63, 58)));
    }

    @Test
    void aBarracksAndDepotTwoTilesApartIsNotAWall() {
        assertFalse(detects(EARLY, barracks(58, 58), depot(64, 58)));
    }

    @Test
    void aBarracksAndBunkerTouchingAtTheMainChokeIsAWall() {
        assertTrue(detects(EARLY, barracks(58, 58), footprint(UnitType.Terran_Bunker, 58, 61)));
    }

    @Test
    void aPairWithOnlyThePartnerAtTheChokeIsAWall() {
        assertTrue(detects(EARLY, barracks(62, 67), depot(60, 65)));
    }

    @Test
    void aPairAwayFromTheMainChokeIsNotAWall() {
        assertFalse(detects(EARLY, barracks(80, 20), depot(84, 20)));
    }

    @Test
    void aBarracksBesideTheDepotIsNotAWallEvenAtAChoke() {
        Predicate<TilePosition> chokeByTheDepot = atChoke(new TilePosition(46, 40));

        assertFalse(TerranWallMain.isDetected(EARLY, Arrays.asList(barracks(42, 39), depot(46, 39)),
                chokeByTheDepot, MAIN_DEPOT_CENTRE));
        assertTrue(TerranWallMain.isDetected(EARLY, Arrays.asList(barracks(48, 39), depot(52, 39)),
                chokeByTheDepot, MAIN_DEPOT_CENTRE));
    }

    @Test
    void nothingIsDetectedAfterTheCutoff() {
        assertTrue(detects(TerranWall.CUTOFF, barracks(58, 58), depot(62, 58)));
        assertFalse(detects(AFTER_CUTOFF, barracks(58, 58), depot(62, 58)));
    }

    private static boolean detects(Time now, TileFootprint... footprints) {
        return TerranWallMain.isDetected(now, Arrays.asList(footprints), atChoke(CHOKE), MAIN_DEPOT_CENTRE);
    }

    private static Predicate<TilePosition> atChoke(TilePosition choke) {
        return tile -> Math.abs(tile.getX() - choke.getX()) + Math.abs(tile.getY() - choke.getY())
                <= TerranWallMain.CHOKE_TILE_RADIUS;
    }

    private static TileFootprint barracks(int left, int top) {
        return footprint(UnitType.Terran_Barracks, left, top);
    }

    private static TileFootprint depot(int left, int top) {
        return footprint(UnitType.Terran_Supply_Depot, left, top);
    }
}
