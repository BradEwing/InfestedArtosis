package info.map;

import bwapi.TilePosition;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coordinates come from two games in batch 20260907-200506 against BananaBrain. GAME_L0Z8I006 on
 * (4)CircuitBreaker.scx walled the natural choke roughly ten tiles from the depot; GAME_L0Z8I005 on
 * (4)Andromeda.scx placed its cannons about five tiles from the depot. bwem Base and Area cannot be
 * constructed outside their package, so the area lookup BaseArea.from() builds from BWMap is supplied
 * here as a fixture.
 */
class BaseAreaTest {

    private static final int MANHATTAN_RADIUS = 8;

    private static final TilePosition CIRCUIT_BREAKER_NATURAL = new TilePosition(117, 92);
    private static final TilePosition CIRCUIT_BREAKER_CHOKE = new TilePosition(105, 94);
    private static final TilePosition CIRCUIT_BREAKER_FORGE = new TilePosition(106, 95);
    private static final TilePosition CIRCUIT_BREAKER_CANNON = new TilePosition(109, 95);

    private static final TilePosition ANDROMEDA_NATURAL = new TilePosition(66, 20);
    private static final TilePosition ANDROMEDA_CANNON = new TilePosition(69, 22);

    private static final TilePosition PROXY_FORGE = new TilePosition(50, 110);

    private static final int NATURAL_AREA = 5;
    private static final int OUTSIDE_AREA = 9;

    @Test
    void wallInsideTheNaturalAreaIsContainedDespiteBeingOutsideTheDepotWindow() {
        BaseArea natural = new BaseArea(CIRCUIT_BREAKER_NATURAL, NATURAL_AREA, Collections.emptyList(), MANHATTAN_RADIUS,
                areaLookup(NATURAL_AREA, CIRCUIT_BREAKER_FORGE, CIRCUIT_BREAKER_CANNON));

        assertTrue(natural.contains(CIRCUIT_BREAKER_FORGE));
        assertTrue(natural.contains(CIRCUIT_BREAKER_CANNON));
    }

    @Test
    void wallOnTheFarSideOfTheNaturalChokeIsContained() {
        BaseArea natural = new BaseArea(CIRCUIT_BREAKER_NATURAL, NATURAL_AREA,
                Collections.singletonList(CIRCUIT_BREAKER_CHOKE), MANHATTAN_RADIUS,
                areaLookup(OUTSIDE_AREA, CIRCUIT_BREAKER_FORGE, CIRCUIT_BREAKER_CANNON));

        assertTrue(natural.contains(CIRCUIT_BREAKER_FORGE));
        assertTrue(natural.contains(CIRCUIT_BREAKER_CANNON));
    }

    @Test
    void theDepotWindowAloneDoesNotReachTheWall() {
        BaseArea natural = new BaseArea(CIRCUIT_BREAKER_NATURAL, NATURAL_AREA, Collections.emptyList(), MANHATTAN_RADIUS,
                areaLookup(OUTSIDE_AREA, CIRCUIT_BREAKER_FORGE, CIRCUIT_BREAKER_CANNON));

        assertFalse(natural.contains(CIRCUIT_BREAKER_FORGE));
        assertFalse(natural.contains(CIRCUIT_BREAKER_CANNON));
    }

    @Test
    void cannonWithinTheDepotWindowIsContainedFromAnotherArea() {
        BaseArea natural = new BaseArea(ANDROMEDA_NATURAL, NATURAL_AREA, Collections.emptyList(), MANHATTAN_RADIUS,
                areaLookup(OUTSIDE_AREA, ANDROMEDA_CANNON));

        assertTrue(natural.contains(ANDROMEDA_CANNON));
    }

    @Test
    void proxyForgeFarFromTheNaturalIsNotContained() {
        BaseArea natural = new BaseArea(CIRCUIT_BREAKER_NATURAL, NATURAL_AREA,
                Collections.singletonList(CIRCUIT_BREAKER_CHOKE), MANHATTAN_RADIUS,
                areaLookup(OUTSIDE_AREA, PROXY_FORGE));

        assertFalse(natural.contains(PROXY_FORGE));
    }

    @Test
    void tileBwemMapsToNoAreaFallsBackToTheManhattanWindows() {
        BaseArea natural = new BaseArea(CIRCUIT_BREAKER_NATURAL, NATURAL_AREA,
                Collections.singletonList(CIRCUIT_BREAKER_CHOKE), MANHATTAN_RADIUS, tile -> null);

        assertTrue(natural.contains(CIRCUIT_BREAKER_FORGE));
        assertFalse(natural.contains(PROXY_FORGE));
    }

    @Test
    void unknownNaturalAreaFallsBackToTheManhattanWindows() {
        BaseArea natural = new BaseArea(CIRCUIT_BREAKER_NATURAL, null,
                Collections.singletonList(CIRCUIT_BREAKER_CHOKE), MANHATTAN_RADIUS,
                areaLookup(NATURAL_AREA, CIRCUIT_BREAKER_FORGE, PROXY_FORGE));

        assertTrue(natural.contains(CIRCUIT_BREAKER_FORGE));
        assertFalse(natural.contains(PROXY_FORGE));
    }

    @Test
    void unknownTileIsNotContained() {
        BaseArea natural = new BaseArea(CIRCUIT_BREAKER_NATURAL, NATURAL_AREA, Collections.emptyList(), MANHATTAN_RADIUS,
                tile -> NATURAL_AREA);

        assertFalse(natural.contains(null));
    }

    private static Function<TilePosition, Integer> areaLookup(int areaId, TilePosition... tiles) {
        Map<TilePosition, Integer> areas = new HashMap<>();
        for (TilePosition tile : Arrays.asList(tiles)) {
            areas.put(tile, areaId);
        }
        return areas::get;
    }
}
