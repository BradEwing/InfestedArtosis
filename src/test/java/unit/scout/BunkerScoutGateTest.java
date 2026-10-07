package unit.scout;

import bwapi.TilePosition;
import org.junit.jupiter.api.Test;
import unit.scout.BunkerScoutGate.Destination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BunkerScoutGateTest {

    private static final TilePosition MAIN = new TilePosition(10, 10);
    private static final TilePosition NATURAL = new TilePosition(40, 40);
    private static final TilePosition OTHER = new TilePosition(90, 90);

    @Test
    void nothingHeldOpensEveryRoute() {
        for (Destination destination : Destination.values()) {
            assertTrue(BunkerScoutGate.mayRoute(false, false, destination));
        }
    }

    @Test
    void aNaturalHoldClosesTheNaturalAndTheMainBehindIt() {
        assertFalse(BunkerScoutGate.mayRoute(true, false, Destination.ENEMY_NATURAL));
        assertFalse(BunkerScoutGate.mayRoute(true, false, Destination.ENEMY_MAIN));
        assertTrue(BunkerScoutGate.mayRoute(true, false, Destination.OTHER));
    }

    @Test
    void aMainHoldClosesTheMainAndLeavesTheNaturalOpen() {
        assertFalse(BunkerScoutGate.mayRoute(false, true, Destination.ENEMY_MAIN));
        assertTrue(BunkerScoutGate.mayRoute(false, true, Destination.ENEMY_NATURAL));
        assertTrue(BunkerScoutGate.mayRoute(false, true, Destination.OTHER));
    }

    @Test
    void aBaseIsClassifiedByItsLocation() {
        assertEquals(Destination.ENEMY_MAIN, BunkerScoutGate.destination(MAIN, MAIN, NATURAL));
        assertEquals(Destination.ENEMY_NATURAL, BunkerScoutGate.destination(NATURAL, MAIN, NATURAL));
        assertEquals(Destination.OTHER, BunkerScoutGate.destination(OTHER, MAIN, NATURAL));
        assertEquals(Destination.OTHER, BunkerScoutGate.destination(OTHER, null, null));
        assertEquals(Destination.OTHER, BunkerScoutGate.destination(null, MAIN, NATURAL));
    }

    @Test
    void theBaseFormReadsTheClassifiedDestination() {
        assertFalse(BunkerScoutGate.mayRouteToBase(true, false, MAIN, MAIN, NATURAL));
        assertTrue(BunkerScoutGate.mayRouteToBase(true, false, OTHER, MAIN, NATURAL));
        assertTrue(BunkerScoutGate.mayRouteToBase(false, false, MAIN, MAIN, NATURAL));
    }

    @Test
    void theSkipLabelNamesTheReasonAndTheDestination() {
        assertEquals("BUNKER:ENEMY_MAIN", BunkerScoutGate.skipLabel(Destination.ENEMY_MAIN));
    }
}
