package info.map;

import bwapi.Position;
import bwapi.Race;
import bwapi.TilePosition;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for PerchCalculator: the ground distance BFS, attacker reach, clearance filtering and
 * perch selection are all pure functions of data already extracted from UnitType/WeaponType, so no
 * Game instance is needed.
 */
class PerchCalculatorTest {

    private static MapTile tileAt(int x, int y) {
        return new MapTile(new TilePosition(x, y), 0, true, true, MapTileType.NORMAL);
    }

    @Test
    void singleGroundTileProducesChebyshevRing() {
        boolean[][] groundOccupiable = new boolean[7][7];
        groundOccupiable[3][3] = true;

        int[][] distances = PerchCalculator.groundDistances(groundOccupiable);

        assertEquals(0, distances[3][3]);
        assertEquals(3, distances[0][0]);
        assertEquals(3, distances[6][6]);
        assertEquals(3, distances[0][6]);
        assertEquals(3, distances[6][0]);
        assertEquals(1, distances[2][3]);
        assertEquals(1, distances[4][3]);
        assertEquals(1, distances[3][2]);
        assertEquals(1, distances[3][4]);
        assertEquals(1, distances[2][2]);
        assertEquals(1, distances[4][4]);
    }

    @Test
    void allGroundGridIsAllZero() {
        boolean[][] groundOccupiable = new boolean[5][5];
        for (boolean[] column : groundOccupiable) {
            java.util.Arrays.fill(column, true);
        }

        int[][] distances = PerchCalculator.groundDistances(groundOccupiable);

        for (int[] column : distances) {
            for (int distance : column) {
                assertEquals(0, distance);
            }
        }
    }

    @Test
    void noGroundGridIsAllUnreachable() {
        boolean[][] groundOccupiable = new boolean[5][5];

        int[][] distances = PerchCalculator.groundDistances(groundOccupiable);

        for (int[] column : distances) {
            for (int distance : column) {
                assertEquals(Integer.MAX_VALUE, distance);
            }
        }
    }

    @Test
    void nonSquareGridIndexesCorrectly() {
        boolean[][] groundOccupiable = new boolean[5][3];
        groundOccupiable[0][0] = true;

        int[][] distances = PerchCalculator.groundDistances(groundOccupiable);

        assertEquals(5, distances.length);
        assertEquals(3, distances[0].length);
        assertEquals(0, distances[0][0]);
        assertEquals(4, distances[4][0]);
        assertEquals(2, distances[0][2]);
        assertEquals(4, distances[4][2]);
    }

    @Test
    void reachPixelsMatchesFormulaFromTheSameApiCalls() {
        UnitType marine = UnitType.Terran_Marine;
        int attackerHalfSpan = Math.max(marine.width(), marine.height()) / 2;
        int overlordHalfSpan = Math.max(UnitType.Zerg_Overlord.width(), UnitType.Zerg_Overlord.height()) / 2;
        int expected = marine.airWeapon().maxRange() + attackerHalfSpan + overlordHalfSpan + 16;

        assertEquals(expected, PerchCalculator.reachPixels(marine));
    }

    @Test
    void contributesToClearanceFiltersByRaceAndVisibility() {
        assertTrue(PerchCalculator.contributesToClearance(UnitType.Terran_Goliath, Race.Terran));
        assertTrue(PerchCalculator.contributesToClearance(UnitType.Terran_Goliath, Race.Unknown));
        assertFalse(PerchCalculator.contributesToClearance(UnitType.Terran_Goliath, Race.Protoss));

        assertFalse(PerchCalculator.contributesToClearance(UnitType.Terran_Ghost, Race.Terran));

        assertTrue(PerchCalculator.contributesToClearance(UnitType.Protoss_Dragoon, Race.Protoss));

        assertFalse(PerchCalculator.contributesToClearance(UnitType.Terran_Missile_Turret, Race.Terran));
        assertFalse(PerchCalculator.contributesToClearance(UnitType.Terran_Wraith, Race.Terran));
        assertFalse(PerchCalculator.contributesToClearance(UnitType.Unknown, Race.Unknown));
    }

    @Test
    void clearanceTilesMatchesTheDrivingUnitPerRace() {
        int protossClearance = PerchCalculator.clearanceTiles(Race.Protoss);
        int terranClearance = PerchCalculator.clearanceTiles(Race.Terran);
        int zergClearance = PerchCalculator.clearanceTiles(Race.Zerg);
        int unknownClearance = PerchCalculator.clearanceTiles(Race.Unknown);

        assertEquals((int) Math.ceil(PerchCalculator.reachPixels(UnitType.Protoss_Dragoon) / 32.0), protossClearance);
        assertEquals((int) Math.ceil(PerchCalculator.reachPixels(UnitType.Terran_Goliath) / 32.0), terranClearance);

        assertTrue(unknownClearance >= protossClearance);
        assertTrue(unknownClearance >= terranClearance);
        assertTrue(unknownClearance >= zergClearance);

        assertTrue(protossClearance * 32 < UnitType.Zerg_Overlord.sightRange());
        assertTrue(terranClearance * 32 < UnitType.Zerg_Overlord.sightRange());
        assertTrue(zergClearance * 32 < UnitType.Zerg_Overlord.sightRange());
        assertTrue(unknownClearance * 32 < UnitType.Zerg_Overlord.sightRange());
    }

    @Test
    void selectPerchReturnsNullOnEmptyInput() {
        assertNull(PerchCalculator.selectPerch(Collections.emptyList(), new Position(0, 0), 288));
    }

    @Test
    void selectPerchPrefersHigherGroundWithinSightOverNearerLowerGround() {
        MapTile nearLow = tileAt(0, 0);
        nearLow.setGroundHeight(0);
        MapTile fartherHigh = tileAt(2, 0);
        fartherHigh.setGroundHeight(2);

        List<MapTile> perches = new ArrayList<>();
        perches.add(nearLow);
        perches.add(fartherHigh);

        MapTile selected = PerchCalculator.selectPerch(perches, new Position(0, 0), 288);

        assertSame(fartherHigh, selected);
    }

    @Test
    void selectPerchPicksNearestWhenNoneWithinSight() {
        MapTile near = tileAt(0, 0);
        near.setGroundHeight(0);
        MapTile far = tileAt(2, 0);
        far.setGroundHeight(2);

        List<MapTile> perches = new ArrayList<>();
        perches.add(near);
        perches.add(far);

        MapTile selected = PerchCalculator.selectPerch(perches, new Position(0, 0), 10);

        assertSame(near, selected);
    }

    @Test
    void selectPerchTiebreaksEqualDistanceByHigherGround() {
        MapTile left = tileAt(0, 0);
        left.setGroundHeight(1);
        MapTile right = tileAt(5, 0);
        right.setGroundHeight(5);

        List<MapTile> perches = new ArrayList<>();
        perches.add(left);
        perches.add(right);

        Position target = new Position(96, 16);
        MapTile selected = PerchCalculator.selectPerch(perches, target, 0);

        assertSame(right, selected);
    }

    @Test
    void groundDistancesCompletesQuicklyOnALargeGrid() {
        int size = 256;
        boolean[][] groundOccupiable = new boolean[size][size];
        for (int x = 0; x < size / 2; x++) {
            for (int y = 0; y < size; y++) {
                groundOccupiable[x][y] = true;
            }
        }

        long start = System.nanoTime();
        PerchCalculator.groundDistances(groundOccupiable);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("groundDistances 256x256 elapsed: " + elapsedMs + " ms");
        assertTrue(elapsedMs < 2000);
    }
}
