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
    void fightingAndContainingLingsMayBePulled() {
        assertTrue(UnitManager.mayPullAsZerglingScout(UnitRole.FIGHT));
        assertTrue(UnitManager.mayPullAsZerglingScout(UnitRole.CONTAIN));
    }
}
