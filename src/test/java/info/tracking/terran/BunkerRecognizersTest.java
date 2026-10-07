package info.tracking.terran;

import bwapi.TilePosition;
import org.junit.jupiter.api.Test;
import util.TileFootprint;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The enemy natural's area stands in as the tiles with x in 30-50 and y in 30-50, the enemy main's as x in 0-29 and
 * y in 0-29, and the main's ramp as the tiles within 8 of (28, 30), which also lie in the natural's area window.
 */
class BunkerRecognizersTest {

    private static final Predicate<TilePosition> IN_NATURAL = tile -> tile.getX() >= 30 && tile.getX() <= 50
            && tile.getY() >= 30 && tile.getY() <= 50;

    private static final Predicate<TilePosition> IN_MAIN_AREA = tile -> tile.getX() < 30 && tile.getY() < 30;

    private static final Predicate<TilePosition> AT_RAMP = tile -> Math.abs(tile.getX() - 28)
            + Math.abs(tile.getY() - 30) <= 8;

    private static final Predicate<TilePosition> AT_MAIN = IN_MAIN_AREA.or(AT_RAMP);

    private static List<TilePosition> natural(TilePosition... living) {
        return BunkerNatural.heldBunkers(Arrays.asList(living), IN_NATURAL, AT_MAIN);
    }

    private static List<TilePosition> main(TilePosition... living) {
        return BunkerMain.heldBunkers(Arrays.asList(living), AT_MAIN);
    }

    @Test
    void aBunkerInTheNaturalAreaHoldsTheNatural() {
        assertEquals(1, natural(new TilePosition(40, 40)).size());
        assertTrue(main(new TilePosition(40, 40)).isEmpty());
    }

    @Test
    void aBunkerAtTheMainRampHoldsTheMainAndNotTheNatural() {
        TilePosition ramp = new TilePosition(33, 32);
        assertTrue(IN_NATURAL.test(ramp));
        assertEquals(1, main(ramp).size());
        assertTrue(natural(ramp).isEmpty());
    }

    @Test
    void aBunkerInTheMainAreaHoldsTheMain() {
        assertEquals(1, main(new TilePosition(10, 10)).size());
        assertTrue(natural(new TilePosition(10, 10)).isEmpty());
    }

    @Test
    void aBunkerElsewhereHoldsNeither() {
        assertTrue(natural(new TilePosition(90, 90)).isEmpty());
        assertTrue(main(new TilePosition(90, 90)).isEmpty());
    }

    @Test
    void aBunkerSeenDeadLeavesNothingHeld() {
        assertTrue(natural().isEmpty());
        assertTrue(main().isEmpty());
        assertTrue(natural(new TilePosition(40, 40)).size() > natural().size());
    }

    @Test
    void theHoldKeepsWhileAnyBunkerIsLeft() {
        List<TilePosition> living = Arrays.asList(new TilePosition(40, 40), new TilePosition(45, 44));
        assertEquals(2, BunkerNatural.heldBunkers(living, IN_NATURAL, AT_MAIN).size());
        assertEquals(1, BunkerNatural.heldBunkers(living.subList(1, 2), IN_NATURAL, AT_MAIN).size());
        assertTrue(BunkerNatural.heldBunkers(Collections.emptyList(), IN_NATURAL, AT_MAIN).isEmpty());
    }

    @Test
    void aBunkerAtEachBaseHoldsBoth() {
        TilePosition inNatural = new TilePosition(45, 44);
        TilePosition inMain = new TilePosition(10, 10);
        assertEquals(1, natural(inNatural, inMain).size());
        assertEquals(1, main(inNatural, inMain).size());
    }

    @Test
    void aWallAndABunkerAreBothDetectedFromTheSameNatural() {
        TileFootprint barracks = new TileFootprint(bwapi.UnitType.Terran_Barracks, new TilePosition(40, 40));
        TileFootprint depot = new TileFootprint(bwapi.UnitType.Terran_Supply_Depot, new TilePosition(44, 40));
        TerranWall.Evidence wall = TerranWallNatural.evidence(Arrays.asList(barracks, depot),
                Collections.emptyList(), IN_NATURAL, (b, p) -> false);

        assertNotNull(wall);
        assertEquals(1, natural(new TilePosition(42, 44)).size());
    }

    @Test
    void theLabelCarriesTheCountAndTheFirstBunkersTile() {
        List<TilePosition> held = natural(new TilePosition(40, 40), new TilePosition(45, 44));
        assertEquals("BunkerNatural:2@40x40", TerranBunker.label(BunkerNatural.NAME, held));
        assertEquals("BunkerMain", TerranBunker.label(BunkerMain.NAME, Collections.emptyList()));
    }
}
