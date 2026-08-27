package info.map;

import bwapi.TilePosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for GroundPathComparator.
 *
 * <p>A null GroundPath means "distance unknown". BaseData stores no path for our own main base, so a null could reach
 * this comparator and take down the JVM from inside TimSort (IA-239). Null must order last instead.
 */
public class GroundPathComparatorTest {

    private GroundPathComparator comparator;
    private GroundPath shortPath;
    private GroundPath longPath;

    @BeforeEach
    void setUp() {
        comparator = new GroundPathComparator();
        shortPath = pathOfLength(2);
        longPath = pathOfLength(10);
    }

    private GroundPath pathOfLength(int tiles) {
        ArrayDeque<MapTile> waypoints = new ArrayDeque<>();
        for (int i = 0; i < tiles; i++) {
            waypoints.add(new MapTile(new TilePosition(i, 0), 0, true, true, MapTileType.NORMAL));
        }
        return new GroundPath(waypoints);
    }

    @Test
    void testOrdersByGroundDistance() {
        assertTrue(comparator.compare(shortPath, longPath) < 0);
        assertTrue(comparator.compare(longPath, shortPath) > 0);
        assertEquals(0, comparator.compare(shortPath, shortPath));
    }

    @Test
    void testNullOrdersLast() {
        assertTrue(comparator.compare(null, shortPath) > 0);
        assertTrue(comparator.compare(shortPath, null) < 0);
        assertEquals(0, comparator.compare(null, null));
    }

    /**
     * Reproduces the IA-239 crash: sorting a list that contains a null path threw an NPE out of TimSort, which
     * escaped the JBWAPI event dispatch and killed the client mid-game.
     */
    @Test
    void testSortingWithNullDoesNotThrow() {
        List<GroundPath> paths = new ArrayList<>();
        paths.add(longPath);
        paths.add(null);
        paths.add(shortPath);

        paths.sort(comparator);

        assertSame(shortPath, paths.get(0));
        assertSame(longPath, paths.get(1));
        assertNull(paths.get(2));
    }
}
