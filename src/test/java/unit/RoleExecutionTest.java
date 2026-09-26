package unit;

import org.junit.jupiter.api.Test;
import unit.managed.UnitRole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleExecutionTest {

    @Test
    void aHarassingUnitActsOnItsOrderWithoutReassignment() {
        assertTrue(UnitManager.executesDirectly(UnitRole.HARASS));
        assertTrue(UnitManager.executesDirectly(UnitRole.RUNBY));
        assertTrue(UnitManager.executesDirectly(UnitRole.FIGHT));
    }

    @Test
    void idleAndScoutingUnitsGoThroughReassignment() {
        assertFalse(UnitManager.executesDirectly(UnitRole.IDLE));
        assertFalse(UnitManager.executesDirectly(UnitRole.SCOUT));
        assertFalse(UnitManager.executesDirectly(UnitRole.EGG));
        assertFalse(UnitManager.executesDirectly(UnitRole.SCREEN));
    }
}
