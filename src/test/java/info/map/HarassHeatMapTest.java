package info.map;

import bwapi.Position;
import bwapi.TilePosition;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HarassHeatMapTest {

    private static final Position BASE = new Position(50 * 32 + 16, 50 * 32 + 16);
    private static final TilePosition MINERAL = new TilePosition(55, 50);
    private static final TilePosition PLAIN = new TilePosition(45, 50);

    private static HarassHeatMap heated(int steps, Set<TilePosition> resources) {
        HarassHeatMap map = new HarassHeatMap(128, 128);
        for (int i = 0; i < steps; i++) {
            map.accumulate(Collections.singletonList(BASE), resources::contains);
        }
        return map;
    }

    @Test
    void tilesNearAKnownEnemyBaseAccumulateEveryStep() {
        HarassHeatMap map = heated(3, Collections.emptySet());

        assertEquals(3 * HarassHeatMap.BASE_WEIGHT, map.heatAt(PLAIN));
        assertEquals(3 * HarassHeatMap.BASE_WEIGHT, map.heatAt(BASE.toTilePosition()));
    }

    @Test
    void tilesBeyondTheRadiusNeverHeat() {
        HarassHeatMap map = heated(5, Collections.emptySet());

        assertEquals(0, map.heatAt(new TilePosition(50 + HarassHeatMap.RADIUS_TILES + 1, 50)));
        assertEquals(0, map.heatAt(new TilePosition(50 + HarassHeatMap.RADIUS_TILES - 1,
                50 + HarassHeatMap.RADIUS_TILES - 1)));
        assertEquals(5 * HarassHeatMap.BASE_WEIGHT, map.heatAt(new TilePosition(50 + HarassHeatMap.RADIUS_TILES, 50)));
    }

    @Test
    void mineralAndGeyserTilesHeatFasterThanOpenGround() {
        HarassHeatMap map = heated(4, new HashSet<>(Collections.singletonList(MINERAL)));

        assertEquals(4 * HarassHeatMap.RESOURCE_WEIGHT, map.heatAt(MINERAL));
        assertEquals(4 * HarassHeatMap.BASE_WEIGHT, map.heatAt(PLAIN));
        assertEquals(true, map.heatAt(MINERAL) > map.heatAt(PLAIN));
    }

    @Test
    void aTileNearTwoBasesGainsOncePerStep() {
        HarassHeatMap map = new HarassHeatMap(128, 128);
        Position second = new Position(56 * 32 + 16, 50 * 32 + 16);

        map.accumulate(Arrays.asList(BASE, second), tile -> false);

        assertEquals(HarassHeatMap.BASE_WEIGHT, map.heatAt(new TilePosition(53, 50)));
    }

    @Test
    void offMapTilesAreIgnored() {
        HarassHeatMap map = new HarassHeatMap(20, 20);

        map.accumulate(Collections.singletonList(new Position(16, 16)), tile -> false);

        assertEquals(HarassHeatMap.BASE_WEIGHT, map.heatAt(new TilePosition(0, 0)));
        assertEquals(0, map.heatAt(new TilePosition(-1, 0)));
    }

    @Test
    void visitingZeroesTheGroundAroundTheVisitor() {
        HarassHeatMap map = heated(6, Collections.emptySet());

        map.visit(new Position(PLAIN.getX() * 32 + 16, PLAIN.getY() * 32 + 16), 2);

        assertEquals(0, map.heatAt(PLAIN));
        assertEquals(0, map.heatAt(new TilePosition(PLAIN.getX() + 2, PLAIN.getY())));
        assertEquals(6 * HarassHeatMap.BASE_WEIGHT, map.heatAt(new TilePosition(PLAIN.getX() + 3, PLAIN.getY())));
    }

    @Test
    void theHottestTileIsTheResourceTile() {
        HarassHeatMap map = heated(2, new HashSet<>(Collections.singletonList(MINERAL)));

        assertEquals(new Position(MINERAL.getX() * 32 + 16, MINERAL.getY() * 32 + 16),
                map.hottestNear(BASE, point -> true));
    }

    @Test
    void theHottestTileSkipsTilesTheFilterRefuses() {
        HarassHeatMap map = heated(2, new HashSet<>(Collections.singletonList(MINERAL)));
        Position mineral = new Position(MINERAL.getX() * 32 + 16, MINERAL.getY() * 32 + 16);

        Position hottest = map.hottestNear(BASE, point -> !point.equals(mineral));

        assertEquals(2 * HarassHeatMap.BASE_WEIGHT, map.heatAt(hottest.toTilePosition()));
        assertEquals(BASE, hottest);
    }

    @Test
    void aVisitedBaseMovesTheHottestTileAwayFromTheVisitor() {
        HarassHeatMap map = heated(2, Collections.emptySet());
        map.visit(BASE, 3);

        Position hottest = map.hottestNear(BASE, point -> true);

        assertEquals(true, hottest.getDistance(BASE) > 3 * 32);
        assertEquals(true, hottest.getDistance(BASE) < 4 * 32);
    }

    @Test
    void aColdBaseHasNoHottestTile() {
        HarassHeatMap map = new HarassHeatMap(128, 128);

        assertNull(map.hottestNear(BASE, point -> true));
    }
}
