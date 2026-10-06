package unit.managed;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RallyGateTest {

    @Test
    void flyersWithAnAirWeaponCloseTheGate() {
        assertTrue(ManagedUnit.attacksAir(UnitType.Zerg_Mutalisk));
        assertTrue(ManagedUnit.attacksAir(UnitType.Zerg_Scourge));
        assertTrue(ManagedUnit.attacksAir(UnitType.Protoss_Corsair));
        assertTrue(ManagedUnit.attacksAir(UnitType.Terran_Valkyrie));
        assertTrue(ManagedUnit.attacksAir(UnitType.Terran_Wraith));
        assertTrue(ManagedUnit.attacksAir(UnitType.Protoss_Interceptor));
    }

    @Test
    void aCarrierClosesTheGateThroughItsInterceptors() {
        assertTrue(ManagedUnit.attacksAir(UnitType.Protoss_Carrier));
    }

    @Test
    void flyersThatCannotShootAirLeaveTheGateOpen() {
        assertFalse(ManagedUnit.attacksAir(UnitType.Zerg_Overlord));
        assertFalse(ManagedUnit.attacksAir(UnitType.Protoss_Observer));
        assertFalse(ManagedUnit.attacksAir(UnitType.Protoss_Shuttle));
        assertFalse(ManagedUnit.attacksAir(UnitType.Terran_Dropship));
        assertFalse(ManagedUnit.attacksAir(UnitType.Terran_Science_Vessel));
        assertFalse(ManagedUnit.attacksAir(UnitType.Terran_Barracks));
    }
}
