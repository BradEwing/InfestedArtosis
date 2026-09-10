package unit.scout;

import bwapi.UnitType;
import info.map.PerchCalculator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerchThreatTest {

    @Test
    void flyingAirThreatsThreatenAtAnyDistance() {
        assertTrue(PerchThreat.threatens(UnitType.Terran_Wraith, 10000));
        assertTrue(PerchThreat.threatens(UnitType.Protoss_Corsair, 10000));
        assertTrue(PerchThreat.threatens(UnitType.Zerg_Mutalisk, 10000));
        assertTrue(PerchThreat.threatens(UnitType.Protoss_Scout, 10000));
    }

    @Test
    void airTechBuildingsAlwaysThreaten() {
        assertTrue(PerchThreat.threatens(UnitType.Zerg_Spire, 10000));
        assertTrue(PerchThreat.threatens(UnitType.Zerg_Hydralisk_Den, 10000));
        assertTrue(PerchThreat.threatens(UnitType.Protoss_Stargate, 10000));
        assertTrue(PerchThreat.threatens(UnitType.Terran_Starport, 10000));
    }

    @Test
    void fasterGroundUnitThreatensBeyondItsReachByTheGroundItClosesWhileFleeing() {
        double closingSpeed = UnitType.Terran_Marine.topSpeed() - UnitType.Zerg_Overlord.topSpeed();
        double expected = PerchCalculator.reachPixels(UnitType.Terran_Marine)
                + closingSpeed * PerchThreat.REACTION_FRAMES;

        assertEquals(expected, PerchThreat.leaveDistancePixels(UnitType.Terran_Marine), 1e-9);
        assertTrue(PerchThreat.threatens(UnitType.Terran_Marine, expected));
        assertFalse(PerchThreat.threatens(UnitType.Terran_Marine, expected + 1));
        assertTrue(PerchThreat.threatens(UnitType.Terran_Marine,
                PerchCalculator.reachPixels(UnitType.Terran_Marine) + 1));
    }

    @Test
    void staticDefenseThreatensWithinReachOnly() {
        int reach = PerchCalculator.reachPixels(UnitType.Terran_Missile_Turret);
        assertEquals(reach, PerchThreat.leaveDistancePixels(UnitType.Terran_Missile_Turret), 1e-9);
        assertTrue(PerchThreat.threatens(UnitType.Terran_Missile_Turret, reach));
        assertFalse(PerchThreat.threatens(UnitType.Terran_Missile_Turret, reach + 1));
    }

    @Test
    void leaveDistanceGrowsWithTheAttackerSpeedAdvantage() {
        double marineMargin = PerchThreat.leaveDistancePixels(UnitType.Terran_Marine)
                - PerchCalculator.reachPixels(UnitType.Terran_Marine);
        double hydraliskMargin = PerchThreat.leaveDistancePixels(UnitType.Zerg_Hydralisk)
                - PerchCalculator.reachPixels(UnitType.Zerg_Hydralisk);

        assertTrue(hydraliskMargin > 0);
        assertTrue(marineMargin > hydraliskMargin);
    }

    @Test
    void nonAirThreatTypesNeverThreaten() {
        assertFalse(PerchThreat.threatens(UnitType.Protoss_Zealot, 0));
        assertFalse(PerchThreat.threatens(UnitType.Terran_SCV, 0));
        assertFalse(PerchThreat.threatens(UnitType.Zerg_Overlord, 0));
    }

    @Test
    void flyerWithoutAirWeaponNeverThreatens() {
        assertFalse(PerchThreat.threatens(UnitType.Terran_Dropship, 0));
        assertFalse(PerchThreat.threatens(UnitType.Terran_Dropship, 100000));
    }
}
