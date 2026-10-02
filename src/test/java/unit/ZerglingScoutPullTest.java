package unit;

import org.junit.jupiter.api.Test;
import unit.managed.UnitRole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZerglingScoutPullTest {

    @Test
    void aLingOnARunbyIsNeverPulledToScout() {
        assertFalse(UnitManager.mayPullAsZerglingScout(UnitRole.RUNBY));
    }

    @Test
    void aLingAlreadyScoutingIsNotPulledAgain() {
        assertFalse(UnitManager.mayPullAsZerglingScout(UnitRole.SCOUT));
    }

    @Test
    void fightingAndContainingLingsAreNeverPulled() {
        assertFalse(UnitManager.mayPullAsZerglingScout(UnitRole.FIGHT));
        assertFalse(UnitManager.mayPullAsZerglingScout(UnitRole.CONTAIN));
    }

    @Test
    void anIdleLingMayBePulled() {
        assertTrue(UnitManager.mayPullAsZerglingScout(UnitRole.IDLE));
    }
}
