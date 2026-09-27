package info.tracking.terran;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.UnitType;
import info.map.ChokeWall;
import info.map.GameMap;
import info.map.MapTile;
import info.map.MapTileType;
import org.junit.jupiter.api.Test;
import util.Distance;
import util.TileFootprint;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static info.tracking.terran.TerranWallNaturalTest.footprint;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The enemy main's depot is centred on tile (40, 40) and its one chokepoint on tile (60, 60), unless a test says
 * otherwise.
 */
class TerranWallMainTest {

    private static final TilePosition MAIN_DEPOT_CENTRE = new TilePosition(40, 40);

    private static final TilePosition CHOKE = new TilePosition(60, 60);

    private static final Predicate<TilePosition> NO_EXIT_PATH = tile -> false;

    /**
     * The ground path out of GrimHammer's NeoMoonGlaive top start (Command Center at (2208, 240)), from its depot
     * down to the plateau's entrance, as A* over the map's walkability runs it.
     */
    private static final List<TilePosition> NEO_MOON_GLAIVE_TOP_EXIT = Arrays.asList(
            new TilePosition(69, 7), new TilePosition(68, 7), new TilePosition(67, 7), new TilePosition(66, 7),
            new TilePosition(65, 8), new TilePosition(64, 9), new TilePosition(63, 10), new TilePosition(62, 10),
            new TilePosition(61, 11), new TilePosition(60, 12), new TilePosition(59, 13), new TilePosition(58, 14),
            new TilePosition(57, 15), new TilePosition(56, 16),
            new TilePosition(55, 17), new TilePosition(54, 18), new TilePosition(53, 18), new TilePosition(52, 18),
            new TilePosition(51, 18), new TilePosition(50, 18), new TilePosition(49, 17), new TilePosition(48, 16));

    private static final TilePosition NEO_MOON_GLAIVE_TOP_DEPOT = new TilePosition(69, 7);

    /**
     * About where the top start's natural depot stands, beside the geyser at (34, 15); the exact tile is not needed.
     */
    private static final TilePosition NEO_MOON_GLAIVE_TOP_NATURAL = new TilePosition(40, 16);

    private static final TilePosition FAR_NATURAL_DEPOT_CENTRE = new TilePosition(100, 100);

    @Test
    void aBarracksAndDepotTouchingAtTheMainChokeIsAWall() {
        assertEquals(TerranWall.Evidence.CHOKE_PAIR, evidence(barracks(58, 58), depot(62, 58)));
    }

    @Test
    void aBarracksAndDepotOneTileApartAtTheMainChokeIsAWall() {
        assertEquals(TerranWall.Evidence.CHOKE_PAIR, evidence(barracks(58, 58), depot(63, 58)));
    }

    @Test
    void aBarracksAndDepotTwoTilesApartIsNotAWall() {
        assertNull(evidence(barracks(58, 58), depot(64, 58)));
    }

    @Test
    void aBarracksAndBunkerTouchingAtTheMainChokeIsAWall() {
        assertEquals(TerranWall.Evidence.CHOKE_PAIR,
                evidence(barracks(58, 58), footprint(UnitType.Terran_Bunker, 58, 61)));
    }

    @Test
    void aPairWithOnlyThePartnerAtTheChokeIsAWall() {
        assertEquals(TerranWall.Evidence.CHOKE_PAIR, evidence(barracks(62, 67), depot(60, 65)));
    }

    @Test
    void aPairAwayFromTheMainChokeAndThePathOutIsNotAWall() {
        assertNull(evidence(barracks(80, 20), depot(84, 20)));
    }

    @Test
    void aBarracksBesideTheDepotIsNotAWallEvenAtAChoke() {
        Predicate<TilePosition> chokeByTheDepot = atChoke(new TilePosition(46, 40));

        assertNull(TerranWallMain.evidence(Arrays.asList(barracks(42, 39), depot(46, 39)), Collections.emptyList(),
                chokeByTheDepot, NO_EXIT_PATH, MAIN_DEPOT_CENTRE, FAR_NATURAL_DEPOT_CENTRE));
        assertEquals(TerranWall.Evidence.CHOKE_PAIR, TerranWallMain.evidence(
                Arrays.asList(barracks(48, 39), depot(52, 39)), Collections.emptyList(), chokeByTheDepot,
                NO_EXIT_PATH, MAIN_DEPOT_CENTRE, FAR_NATURAL_DEPOT_CENTRE));
    }

    @Test
    void theNeoMoonGlaiveTopStartWallBesideThePathOutIsAWall() {
        TileFootprint barracks = TileFootprint.centredAt(UnitType.Terran_Barracks, new Position(1824, 688));
        TileFootprint depot = TileFootprint.centredAt(UnitType.Terran_Supply_Depot, new Position(1840, 608));

        assertEquals(TerranWall.Evidence.EXIT_PAIR, TerranWallMain.evidence(Arrays.asList(barracks, depot),
                Collections.emptyList(), tile -> false, neoMoonGlaiveExit(), NEO_MOON_GLAIVE_TOP_DEPOT, NEO_MOON_GLAIVE_TOP_NATURAL));
    }

    @Test
    void aPairOffThePathOutOrBesideTheDepotIsNotAnExitWall() {
        Predicate<TilePosition> exit = neoMoonGlaiveExit();

        assertNull(TerranWallMain.evidence(Arrays.asList(barracks(58, 20), depot(62, 20)), Collections.emptyList(),
                tile -> false, exit, NEO_MOON_GLAIVE_TOP_DEPOT, NEO_MOON_GLAIVE_TOP_NATURAL));
        assertNull(TerranWallMain.evidence(Arrays.asList(barracks(62, 5), depot(62, 8)), Collections.emptyList(),
                tile -> false, exit, NEO_MOON_GLAIVE_TOP_DEPOT, NEO_MOON_GLAIVE_TOP_NATURAL));
    }

    @Test
    void aProductionBlockBesideTheNaturalDepotOnThePathOutIsNotAnExitWall() {
        Predicate<TilePosition> exit = neoMoonGlaiveExit();
        List<TileFootprint> besideTheNatural = Arrays.asList(barracks(47, 14), depot(47, 17));

        assertNull(TerranWallMain.evidence(besideTheNatural, Collections.emptyList(), tile -> false, exit,
                NEO_MOON_GLAIVE_TOP_DEPOT, new TilePosition(48, 12)));
        assertEquals(TerranWall.Evidence.EXIT_PAIR, TerranWallMain.evidence(besideTheNatural, Collections.emptyList(),
                tile -> false, exit, NEO_MOON_GLAIVE_TOP_DEPOT, NEO_MOON_GLAIVE_TOP_NATURAL));
    }

    @Test
    void productionInsideTheMainOnThePathOutIsNotAnExitWall() {
        Predicate<TilePosition> wholeMainOnThePath = tile -> true;

        assertNull(TerranWallMain.evidence(Arrays.asList(barracks(49, 39), depot(53, 39)), Collections.emptyList(),
                tile -> false, wholeMainOnThePath, MAIN_DEPOT_CENTRE, FAR_NATURAL_DEPOT_CENTRE));
        assertNull(TerranWallMain.evidence(Arrays.asList(barracks(50, 39), depot(54, 39)), Collections.emptyList(),
                tile -> false, wholeMainOnThePath, MAIN_DEPOT_CENTRE, FAR_NATURAL_DEPOT_CENTRE));
        assertEquals(TerranWall.Evidence.EXIT_PAIR, TerranWallMain.evidence(
                Arrays.asList(barracks(51, 39), depot(55, 39)), Collections.emptyList(), tile -> false,
                wholeMainOnThePath, MAIN_DEPOT_CENTRE, FAR_NATURAL_DEPOT_CENTRE));
    }

    @Test
    void theChokeWindowKeepsItsOwnProductionGate() {
        Predicate<TilePosition> chokeInTheMain = atChoke(new TilePosition(52, 40));

        assertEquals(TerranWall.Evidence.CHOKE_PAIR, TerranWallMain.evidence(
                Arrays.asList(barracks(48, 39), depot(52, 39)), Collections.emptyList(), chokeInTheMain,
                NO_EXIT_PATH, MAIN_DEPOT_CENTRE, FAR_NATURAL_DEPOT_CENTRE));
    }

    @Test
    void thereIsNoExitWallWhileTheNaturalIsUnknown() {
        TileFootprint barracks = TileFootprint.centredAt(UnitType.Terran_Barracks, new Position(1824, 688));
        TileFootprint depot = TileFootprint.centredAt(UnitType.Terran_Supply_Depot, new Position(1840, 608));

        assertNull(TerranWallMain.evidence(Arrays.asList(barracks, depot), Collections.emptyList(), tile -> false,
                neoMoonGlaiveExit(), NEO_MOON_GLAIVE_TOP_DEPOT, null));
    }

    @Test
    void buildingsCoveringEverySpotOfAMainChokeWallAreSealedWhateverTheirTypes() {
        ChokeWall ramp = rampWall();
        List<TileFootprint> sealing = Arrays.asList(footprint(UnitType.Terran_Engineering_Bay, 11, 8),
                depot(11, 11));

        assertEquals(TerranWall.Evidence.SEALED, TerranWallMain.evidence(sealing, Collections.singletonList(ramp),
                tile -> false, NO_EXIT_PATH, MAIN_DEPOT_CENTRE, FAR_NATURAL_DEPOT_CENTRE));
        assertNull(TerranWallMain.evidence(sealing.subList(0, 1), Collections.singletonList(ramp),
                tile -> false, NO_EXIT_PATH, MAIN_DEPOT_CENTRE, FAR_NATURAL_DEPOT_CENTRE));
    }

    @Test
    void aSealedChokeIsReportedAheadOfAPair() {
        List<TileFootprint> footprints = Arrays.asList(barracks(11, 8), depot(11, 11));

        assertEquals(TerranWall.Evidence.SEALED, TerranWallMain.evidence(footprints,
                Collections.singletonList(rampWall()), tile -> true, NO_EXIT_PATH, new TilePosition(40, 40),
                FAR_NATURAL_DEPOT_CENTRE));
    }

    @Test
    void theMainPlacementTakesEitherWindow() {
        Predicate<TilePosition> exit = neoMoonGlaiveExit();

        assertTrue(TerranWallMain.placement(atChoke(CHOKE), exit, MAIN_DEPOT_CENTRE, FAR_NATURAL_DEPOT_CENTRE)
                .test(barracks(58, 58), depot(62, 58)));
        assertTrue(TerranWallMain.placement(tile -> false, exit, NEO_MOON_GLAIVE_TOP_DEPOT, NEO_MOON_GLAIVE_TOP_NATURAL)
                .test(barracks(55, 20), depot(56, 18)));
        assertFalse(TerranWallMain.placement(tile -> false, tile -> false, MAIN_DEPOT_CENTRE, FAR_NATURAL_DEPOT_CENTRE)
                .test(barracks(58, 58), depot(62, 58)));
    }

    private static TerranWall.Evidence evidence(TileFootprint... footprints) {
        return TerranWallMain.evidence(Arrays.asList(footprints), Collections.emptyList(), atChoke(CHOKE),
                NO_EXIT_PATH, MAIN_DEPOT_CENTRE, FAR_NATURAL_DEPOT_CENTRE);
    }

    private static Predicate<TilePosition> neoMoonGlaiveExit() {
        Set<TilePosition> tiles = new HashSet<>();
        for (TilePosition pathTile : NEO_MOON_GLAIVE_TOP_EXIT) {
            tiles.addAll(Distance.tilesWithinManhattanDistance(pathTile, TerranWallMain.EXIT_PATH_TILE_RADIUS));
        }
        return tiles::contains;
    }

    private static Predicate<TilePosition> atChoke(TilePosition choke) {
        return tile -> Math.abs(tile.getX() - choke.getX()) + Math.abs(tile.getY() - choke.getY())
                <= TerranWallMain.CHOKE_TILE_RADIUS;
    }

    /**
     * A cliff along x = 10 crossed by a ramp at y 8-11, the main on the high ground x > 10.
     */
    private static ChokeWall rampWall() {
        GameMap map = new GameMap(24, 24);
        for (int x = 0; x < 24; x++) {
            for (int y = 0; y < 24; y++) {
                boolean cliff = x == 10;
                boolean ramp = cliff && y >= 8 && y <= 11;
                map.addTile(new MapTile(new TilePosition(x, y), 0, !cliff, !cliff || ramp, MapTileType.NORMAL), x, y);
            }
        }
        Collection<TilePosition> choke = Collections.singletonList(new TilePosition(10, 9));
        return ChokeWall.across(map, choke, tile -> tile.getX() > 10);
    }

    private static TileFootprint barracks(int left, int top) {
        return footprint(UnitType.Terran_Barracks, left, top);
    }

    private static TileFootprint depot(int left, int top) {
        return footprint(UnitType.Terran_Supply_Depot, left, top);
    }
}
