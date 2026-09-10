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
        assertFalse(PerchCalculator.contributesToClearance(UnitType.Terran_Goliath_Turret, Race.Terran));
        assertFalse(PerchCalculator.contributesToClearance(UnitType.Unknown, Race.Unknown));
    }

    @Test
    void techDepthOrdersFirstTierAntiAirBeforeLaterTiers() {
        assertTrue(PerchCalculator.techDepth(UnitType.Terran_Marine) < PerchCalculator.techDepth(UnitType.Terran_Goliath));
        assertTrue(PerchCalculator.techDepth(UnitType.Terran_Goliath) < PerchCalculator.techDepth(UnitType.Terran_Ghost));
        assertTrue(PerchCalculator.techDepth(UnitType.Protoss_Dragoon) < PerchCalculator.techDepth(UnitType.Protoss_Archon));
    }

    @Test
    void clearanceTilesMatchesTheDrivingUnitPerRace() {
        int protossClearance = PerchCalculator.clearanceTiles(Race.Protoss);
        int terranClearance = PerchCalculator.clearanceTiles(Race.Terran);
        int zergClearance = PerchCalculator.clearanceTiles(Race.Zerg);
        int unknownClearance = PerchCalculator.clearanceTiles(Race.Unknown);

        assertEquals((int) Math.ceil(PerchCalculator.reachPixels(UnitType.Protoss_Dragoon) / 32.0), protossClearance);
        assertEquals((int) Math.ceil(PerchCalculator.reachPixels(UnitType.Terran_Marine) / 32.0), terranClearance);
        assertEquals((int) Math.ceil(PerchCalculator.reachPixels(UnitType.Zerg_Hydralisk) / 32.0), zergClearance);
        assertEquals((int) Math.ceil(PerchCalculator.reachPixels(UnitType.Terran_Marine) / 32.0), unknownClearance);

        assertTrue(protossClearance * 32 < UnitType.Zerg_Overlord.sightRange());
        assertTrue(terranClearance * 32 < UnitType.Zerg_Overlord.sightRange());
        assertTrue(zergClearance * 32 < UnitType.Zerg_Overlord.sightRange());
        assertTrue(unknownClearance * 32 < UnitType.Zerg_Overlord.sightRange());
    }

    @Test
    void selectPerchReturnsNullOnEmptyInput() {
        assertNull(PerchCalculator.selectPerch(Collections.emptyList(), new Position(0, 0), new Position(0, 0),
                288, 2000));
    }

    @Test
    void selectPerchPrefersAPerchNearTheScoutOverOneBesideTheTarget() {
        MapTile nearScout = tileAt(10, 0);
        nearScout.setGroundHeight(0);
        MapTile besideTarget = tileAt(40, 0);
        besideTarget.setGroundHeight(2);

        List<MapTile> perches = new ArrayList<>();
        perches.add(nearScout);
        perches.add(besideTarget);

        Position scout = new Position(0, 16);
        Position target = new Position(1296, 16);

        MapTile selected = PerchCalculator.selectPerch(perches, target, scout, 1000, 2000);

        assertSame(nearScout, selected);
    }

    @Test
    void selectPerchPrefersAFartherPerchWhenOnlyItSeesTheTarget() {
        MapTile nearScout = tileAt(10, 0);
        nearScout.setGroundHeight(2);
        MapTile watching = tileAt(40, 0);
        watching.setGroundHeight(0);

        List<MapTile> perches = new ArrayList<>();
        perches.add(nearScout);
        perches.add(watching);

        Position scout = new Position(0, 16);
        Position target = new Position(1296, 16);

        MapTile selected = PerchCalculator.selectPerch(perches, target, scout, 288, 2000);

        assertSame(watching, selected);
    }

    @Test
    void selectPerchIgnoresVisionOnAPerchBeyondTheTransitBudget() {
        MapTile nearScout = tileAt(10, 0);
        MapTile watching = tileAt(120, 0);

        List<MapTile> perches = new ArrayList<>();
        perches.add(nearScout);
        perches.add(watching);

        Position scout = new Position(0, 16);
        Position target = new Position(3856, 16);

        MapTile selected = PerchCalculator.selectPerch(perches, target, scout, 288, 600);

        assertSame(nearScout, selected);
    }

    @Test
    void selectPerchTakesTheNearestToTheScoutWhenNothingIsWithinTheTransitBudget() {
        MapTile near = tileAt(60, 0);
        MapTile besideTarget = tileAt(120, 0);

        List<MapTile> perches = new ArrayList<>();
        perches.add(besideTarget);
        perches.add(near);

        Position scout = new Position(0, 16);
        Position target = new Position(3856, 16);

        MapTile selected = PerchCalculator.selectPerch(perches, target, scout, 288, 600);

        assertSame(near, selected);
    }

    @Test
    void selectPerchPrefersHigherGroundAmongEquallyPlacedPerches() {
        MapTile low = tileAt(0, 0);
        low.setGroundHeight(0);
        MapTile high = tileAt(0, 20);
        high.setGroundHeight(2);

        List<MapTile> perches = new ArrayList<>();
        perches.add(low);
        perches.add(high);

        Position scout = new Position(16, 336);
        Position target = new Position(16, 336);

        MapTile selected = PerchCalculator.selectPerch(perches, target, scout, 0, 2000);

        assertSame(high, selected);
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
