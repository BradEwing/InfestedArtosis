package unit.managed;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirThreatFilterTest {

    @Test
    void aCompletedEnemyWithAnAirWeaponFiresOnAir() {
        assertTrue(ManagedUnit.firesOnAirNow(UnitType.Terran_Marine, true, true, true, false, false));
        assertTrue(ManagedUnit.firesOnAirNow(UnitType.Zerg_Spore_Colony, true, true, true, false, false));
        assertTrue(ManagedUnit.firesOnAirNow(UnitType.Terran_Bunker, true, true, true, false, false));
    }

    @Test
    void anAllyOrAUnitWithoutAnAirWeaponDoesNot() {
        assertFalse(ManagedUnit.firesOnAirNow(UnitType.Terran_Marine, false, true, true, false, false));
        assertFalse(ManagedUnit.firesOnAirNow(UnitType.Zerg_Zergling, true, true, true, false, false));
        assertFalse(ManagedUnit.firesOnAirNow(UnitType.Zerg_Sunken_Colony, true, true, true, false, false));
    }

    @Test
    void anIncompleteOrUnpoweredBuildingDoesNot() {
        assertFalse(ManagedUnit.firesOnAirNow(UnitType.Zerg_Spore_Colony, true, false, true, false, false));
        assertFalse(ManagedUnit.firesOnAirNow(UnitType.Protoss_Photon_Cannon, true, true, false, false, false));
    }

    @Test
    void anUnpoweredFlagOnAUnitThatIsNotABuildingIsIgnored() {
        assertTrue(ManagedUnit.firesOnAirNow(UnitType.Protoss_Dragoon, true, true, false, false, false));
    }

    @Test
    void aBurrowedOrDisabledUnitDoesNot() {
        assertFalse(ManagedUnit.firesOnAirNow(UnitType.Zerg_Hydralisk, true, true, true, true, false));
        assertFalse(ManagedUnit.firesOnAirNow(UnitType.Terran_Goliath, true, true, true, false, true));
    }

    @Test
    void scourgePassesTheFilterButIsNeverCountedByTheKite() {
        assertTrue(ManagedUnit.firesOnAirNow(UnitType.Zerg_Scourge, true, true, true, false, false));
        CooldownKite.Threat scourge = new CooldownKite.Threat(new bwapi.Position(0, 0), 37, true,
                UnitType.Zerg_Scourge.topSpeed());
        assertFalse(scourge.counts(UnitType.Zerg_Mutalisk.topSpeed()));
    }
}
