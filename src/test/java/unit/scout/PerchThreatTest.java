package unit.scout;

import bwapi.UnitType;
import info.map.PerchCalculator;
import org.junit.jupiter.api.Test;

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
        assertTrue(PerchThreat.threatens(UnitType.Protoss_Stargate, 10000));
        assertTrue(PerchThreat.threatens(UnitType.Terran_Starport, 10000));
    }

    @Test
    void groundUnitThreatensWithinReachOnly() {
        int reach = PerchCalculator.reachPixels(UnitType.Terran_Marine);
        assertTrue(PerchThreat.threatens(UnitType.Terran_Marine, reach));
        assertFalse(PerchThreat.threatens(UnitType.Terran_Marine, reach + 1));
    }

    @Test
    void staticDefenseThreatensWithinReachOnly() {
        int reach = PerchCalculator.reachPixels(UnitType.Terran_Missile_Turret);
        assertTrue(PerchThreat.threatens(UnitType.Terran_Missile_Turret, reach));
        assertFalse(PerchThreat.threatens(UnitType.Terran_Missile_Turret, reach + 1));
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
