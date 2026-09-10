package util;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the air predicate the plan telemetry's enemy_air column counts with.
 */
class FilterTest {

    @Test
    void anArmedFlyerCounts() {
        assertTrue(Filter.isAirCombatUnit(UnitType.Terran_Wraith));
        assertTrue(Filter.isAirCombatUnit(UnitType.Terran_Battlecruiser));
        assertTrue(Filter.isAirCombatUnit(UnitType.Protoss_Corsair));
        assertTrue(Filter.isAirCombatUnit(UnitType.Zerg_Mutalisk));
    }

    @Test
    void anUnarmedFlyerDoesNot() {
        assertFalse(Filter.isAirCombatUnit(UnitType.Zerg_Overlord));
        assertFalse(Filter.isAirCombatUnit(UnitType.Protoss_Observer));
        assertFalse(Filter.isAirCombatUnit(UnitType.Protoss_Shuttle));
        assertFalse(Filter.isAirCombatUnit(UnitType.Terran_Dropship));
    }

    @Test
    void aGroundUnitDoesNot() {
        assertFalse(Filter.isAirCombatUnit(UnitType.Terran_Marine));
        assertFalse(Filter.isAirCombatUnit(UnitType.Zerg_Drone));
    }

    @Test
    void aBuildingThatShootsAirDoesNot() {
        assertFalse(Filter.isAirCombatUnit(UnitType.Terran_Missile_Turret));
        assertFalse(Filter.isAirCombatUnit(UnitType.Zerg_Spore_Colony));
    }
}
