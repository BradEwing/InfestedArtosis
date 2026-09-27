package info.tracking.terran;

import bwapi.TilePosition;
import bwapi.UnitType;
import info.map.ChokeWall;
import info.map.GameMap;
import info.map.MapTile;
import info.map.MapTileType;
import org.junit.jupiter.api.Test;
import util.TileFootprint;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The enemy natural's area stands in as the tiles with x in 30-50 and y in 30-50.
 */
class TerranWallNaturalTest {

    private static final Predicate<TilePosition> IN_NATURAL = tile -> tile.getX() >= 30 && tile.getX() <= 50
            && tile.getY() >= 30 && tile.getY() <= 50;

    private static final BiPredicate<TileFootprint, TileFootprint> NO_MAIN_WALL = (barracks, partner) -> false;

    @Test
    void aBarracksAndDepotTouchingInTheNaturalIsAWall() {
        assertEquals(TerranWall.Evidence.AREA_PAIR, evidence(barracks(40, 40), depot(44, 40)));
    }

    @Test
    void aBarracksAndDepotOneTileApartInTheNaturalIsAWall() {
        assertEquals(TerranWall.Evidence.AREA_PAIR, evidence(barracks(40, 40), depot(45, 40)));
    }

    @Test
    void aBarracksAndDepotTwoTilesApartIsNotAWall() {
        assertNull(evidence(barracks(40, 40), depot(46, 40)));
        assertNull(evidence(barracks(40, 40), depot(40, 45)));
    }

    @Test
    void aBarracksAndBunkerTouchingInTheNaturalIsAWall() {
        assertEquals(TerranWall.Evidence.AREA_PAIR,
                evidence(barracks(40, 40), footprint(UnitType.Terran_Bunker, 40, 43)));
    }

    @Test
    void aPairWithOnlyThePartnerInTheNaturalIsAWall() {
        assertEquals(TerranWall.Evidence.AREA_PAIR, evidence(barracks(26, 40), depot(30, 40)));
    }

    @Test
    void aPairOutsideTheNaturalIsNotAWall() {
        assertNull(evidence(barracks(80, 80), depot(84, 80)));
    }

    @Test
    void aBarracksBesideAnotherBuildingIsNotAWall() {
        assertNull(evidence(barracks(40, 40), footprint(UnitType.Terran_Engineering_Bay, 44, 40)));
        assertNull(evidence(barracks(40, 40), barracks(44, 40)));
        assertNull(evidence(depot(40, 40), depot(43, 40)));
    }

    @Test
    void aPairTheMainDetectorReadsIsNotANaturalWall() {
        Predicate<TilePosition> atMainChoke = tile -> Math.abs(tile.getX() - 42) + Math.abs(tile.getY() - 32) <= 8;
        BiPredicate<TileFootprint, TileFootprint> mainWall = TerranWallMain.placement(atMainChoke, tile -> false,
                new TilePosition(20, 10), new TilePosition(40, 40));
        List<TileFootprint> rampWall = Arrays.asList(barracks(40, 30), depot(44, 30));
        List<TileFootprint> naturalWall = Arrays.asList(barracks(40, 44), depot(44, 44));

        assertNull(TerranWallNatural.evidence(rampWall, Collections.emptyList(), IN_NATURAL, mainWall));
        assertEquals(TerranWall.Evidence.CHOKE_PAIR, TerranWallMain.evidence(rampWall, Collections.emptyList(),
                atMainChoke, tile -> false, new TilePosition(20, 10), new TilePosition(40, 40)));
        assertEquals(TerranWall.Evidence.AREA_PAIR,
                TerranWallNatural.evidence(naturalWall, Collections.emptyList(), IN_NATURAL, mainWall));
    }

    @Test
    void buildingsCoveringEverySpotOfANaturalBridgeWallAreSealed() {
        GameMap map = new GameMap(24, 24);
        for (int x = 0; x < 24; x++) {
            for (int y = 0; y < 24; y++) {
                boolean water = y == 5;
                boolean bridge = water && x >= 5 && x <= 7;
                map.addTile(new MapTile(new TilePosition(x, y), 0, !water, !water || bridge, MapTileType.NORMAL),
                        x, y);
            }
        }
        ChokeWall choke = ChokeWall.across(map, Arrays.asList(new TilePosition(5, 5), new TilePosition(6, 5),
                new TilePosition(7, 5)), tile -> tile.getY() < 5);

        assertEquals(TerranWall.Evidence.SEALED, TerranWallNatural.evidence(
                Collections.singletonList(depot(5, 3)), Collections.singletonList(choke), tile -> false,
                NO_MAIN_WALL));
        assertNull(TerranWallNatural.evidence(Collections.singletonList(depot(6, 3)),
                Collections.singletonList(choke), tile -> false, NO_MAIN_WALL));
    }

    private static TerranWall.Evidence evidence(TileFootprint... footprints) {
        return TerranWallNatural.evidence(Arrays.asList(footprints), Collections.emptyList(), IN_NATURAL,
                NO_MAIN_WALL);
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
