package util;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticDefenseZoneTest {

    private static final Position CENTER = new Position(1600, 1600);
    private static final int MARINE_RANGE = UnitType.Terran_Marine.groundWeapon().maxRange();
    private static final int CANNON_RANGE = UnitType.Protoss_Photon_Cannon.groundWeapon().maxRange();
    private static final int SUNKEN_RANGE = UnitType.Zerg_Sunken_Colony.groundWeapon().maxRange();
    private static final int NO_PADDING = 0;

    private static void assertReachEndsAtEveryEdge(UnitType structure, int range) {
        StaticDefenseZone zone = new StaticDefenseZone(structure, CENTER, range);
        Position[] edgeReach = {
            new Position(CENTER.getX() - structure.dimensionLeft() - range, CENTER.getY()),
            new Position(CENTER.getX() + structure.dimensionRight() + range, CENTER.getY()),
            new Position(CENTER.getX(), CENTER.getY() - structure.dimensionUp() - range),
            new Position(CENTER.getX(), CENTER.getY() + structure.dimensionDown() + range)
        };
        Position[] justBeyond = {
            new Position(edgeReach[0].getX() - 1, CENTER.getY()),
            new Position(edgeReach[1].getX() + 1, CENTER.getY()),
            new Position(CENTER.getX(), edgeReach[2].getY() - 1),
            new Position(CENTER.getX(), edgeReach[3].getY() + 1)
        };
        for (int i = 0; i < edgeReach.length; i++) {
            assertTrue(zone.covers(edgeReach[i], NO_PADDING), structure + " must reach " + edgeReach[i]);
            assertFalse(zone.covers(justBeyond[i], NO_PADDING), structure + " must not reach " + justBeyond[i]);
        }
    }

    @Test
    void bunkerCoverageIsMeasuredFromTheBunkersEdge() {
        assertReachEndsAtEveryEdge(UnitType.Terran_Bunker, MARINE_RANGE);
    }

    @Test
    void photonCannonCoverageIsMeasuredFromTheCannonsEdge() {
        assertReachEndsAtEveryEdge(UnitType.Protoss_Photon_Cannon, CANNON_RANGE);
    }

    @Test
    void sunkenColonyCoverageIsMeasuredFromTheColonysEdge() {
        assertReachEndsAtEveryEdge(UnitType.Zerg_Sunken_Colony, SUNKEN_RANGE);
    }

    @Test
    void aPointBeyondTheCenterRadiusIsStillCoveredAboveABunker() {
        StaticDefenseZone zone = new StaticDefenseZone(UnitType.Terran_Bunker, CENTER, MARINE_RANGE);
        Position aboveBeyondCenterRadius = new Position(CENTER.getX(),
                CENTER.getY() - MARINE_RANGE - UnitType.Terran_Bunker.dimensionUp());

        assertTrue(CENTER.getDistance(aboveBeyondCenterRadius) > MARINE_RANGE);
        assertTrue(zone.covers(aboveBeyondCenterRadius, NO_PADDING));
    }

    @Test
    void paddingExtendsTheReach() {
        StaticDefenseZone zone = new StaticDefenseZone(UnitType.Terran_Bunker, CENTER, MARINE_RANGE);
        int padding = 40;
        Position right = new Position(CENTER.getX() + UnitType.Terran_Bunker.dimensionRight() + MARINE_RANGE + padding,
                CENTER.getY());

        assertFalse(zone.covers(right, NO_PADDING));
        assertTrue(zone.covers(right, padding));
        assertFalse(zone.covers(new Position(right.getX() + 1, right.getY()), padding));
    }

    @Test
    void aDiagonalPointIsMeasuredFromTheNearestCorner() {
        UnitType bunker = UnitType.Terran_Bunker;
        StaticDefenseZone zone = new StaticDefenseZone(bunker, CENTER, MARINE_RANGE);
        int cornerX = CENTER.getX() + bunker.dimensionRight();
        int cornerY = CENTER.getY() + bunker.dimensionDown();

        assertEquals(50.0, zone.edgeDistance(cornerX + 30, cornerY + 40), 1e-9);
        assertEquals(0.0, zone.edgeDistance(CENTER.getX(), CENTER.getY()), 1e-9);
    }

    @Test
    void coveredGridPositionsAreAlignedAndInsideTheZone() {
        StaticDefenseZone zone = new StaticDefenseZone(UnitType.Terran_Bunker, new Position(1604, 1596), MARINE_RANGE);

        for (Position point : zone.coveredGridPositions()) {
            assertEquals(0, point.getX() % 8);
            assertEquals(0, point.getY() % 8);
            assertTrue(zone.covers(point, NO_PADDING));
        }
        assertTrue(zone.coveredGridPositions().contains(new Position(1600, 1600)));
    }
}
