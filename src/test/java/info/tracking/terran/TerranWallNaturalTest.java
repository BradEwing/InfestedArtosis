package info.tracking.terran;

import bwapi.TilePosition;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import util.TileFootprint;
import util.Time;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The enemy natural's area stands in as the tiles with x in 30-50 and y in 30-50.
 */
class TerranWallNaturalTest {

    private static final Predicate<TilePosition> IN_NATURAL = tile -> tile.getX() >= 30 && tile.getX() <= 50
            && tile.getY() >= 30 && tile.getY() <= 50;

    private static final BiPredicate<TileFootprint, TileFootprint> NO_MAIN_WALL = (barracks, partner) -> false;

    private static final Time EARLY = new Time(4, 0);

    private static final Time AFTER_CUTOFF = new Time(TerranWall.CUTOFF.getFrames() + 1);

    @Test
    void aBarracksAndDepotTouchingInTheNaturalIsAWall() {
        assertTrue(detects(EARLY, barracks(40, 40), depot(44, 40)));
    }

    @Test
    void aBarracksAndDepotOneTileApartInTheNaturalIsAWall() {
        assertTrue(detects(EARLY, barracks(40, 40), depot(45, 40)));
    }

    @Test
    void aBarracksAndDepotTwoTilesApartIsNotAWall() {
        assertFalse(detects(EARLY, barracks(40, 40), depot(46, 40)));
        assertFalse(detects(EARLY, barracks(40, 40), depot(40, 45)));
    }

    @Test
    void aBarracksAndBunkerTouchingInTheNaturalIsAWall() {
        assertTrue(detects(EARLY, barracks(40, 40), footprint(UnitType.Terran_Bunker, 40, 43)));
    }

    @Test
    void aPairWithOnlyThePartnerInTheNaturalIsAWall() {
        assertTrue(detects(EARLY, barracks(26, 40), depot(30, 40)));
    }

    @Test
    void aPairOutsideTheNaturalIsNotAWall() {
        assertFalse(detects(EARLY, barracks(80, 80), depot(84, 80)));
    }

    @Test
    void aBarracksBesideAnotherBuildingIsNotAWall() {
        assertFalse(detects(EARLY, barracks(40, 40), footprint(UnitType.Terran_Engineering_Bay, 44, 40)));
        assertFalse(detects(EARLY, barracks(40, 40), barracks(44, 40)));
        assertFalse(detects(EARLY, depot(40, 40), depot(43, 40)));
    }

    @Test
    void nothingIsDetectedAfterTheCutoff() {
        assertTrue(detects(TerranWall.CUTOFF, barracks(40, 40), depot(44, 40)));
        assertFalse(detects(AFTER_CUTOFF, barracks(40, 40), depot(44, 40)));
    }

    @Test
    void aPairTheMainDetectorReadsIsNotANaturalWall() {
        Predicate<TilePosition> atMainChoke = tile -> Math.abs(tile.getX() - 42) + Math.abs(tile.getY() - 32) <= 8;
        BiPredicate<TileFootprint, TileFootprint> mainWall = TerranWallMain.placement(atMainChoke,
                new TilePosition(20, 10));
        List<TileFootprint> rampWall = Arrays.asList(barracks(40, 30), depot(44, 30));
        List<TileFootprint> naturalWall = Arrays.asList(barracks(40, 44), depot(44, 44));

        assertFalse(TerranWallNatural.isDetected(EARLY, rampWall, IN_NATURAL, mainWall));
        assertTrue(TerranWallMain.isDetected(EARLY, rampWall, atMainChoke, new TilePosition(20, 10)));
        assertTrue(TerranWallNatural.isDetected(EARLY, naturalWall, IN_NATURAL, mainWall));
    }

    private static boolean detects(Time now, TileFootprint... footprints) {
        return TerranWallNatural.isDetected(now, Arrays.asList(footprints), IN_NATURAL, NO_MAIN_WALL);
    }

    private static TileFootprint barracks(int left, int top) {
        return footprint(UnitType.Terran_Barracks, left, top);
    }

    private static TileFootprint depot(int left, int top) {
        return footprint(UnitType.Terran_Supply_Depot, left, top);
    }

    static TileFootprint footprint(UnitType type, int left, int top) {
        return new TileFootprint(type, new TilePosition(left, top));
    }
}
