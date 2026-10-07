package unit;

import org.junit.jupiter.api.Test;
import unit.managed.UnitRole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZerglingScoutPullTest {

    @Test
    void aLingOnARunbyIsNeverPulledToScout() {
        assertFalse(UnitManager.mayPullAsZerglingScout(UnitRole.RUNBY, true));
    }

    @Test
    void aLingAlreadyScoutingIsNotPulledAgain() {
        assertFalse(UnitManager.mayPullAsZerglingScout(UnitRole.SCOUT, true));
    }

    @Test
    void aContainingLingIsNeverPulled() {
        assertFalse(UnitManager.mayPullAsZerglingScout(UnitRole.CONTAIN, true));
    }

    @Test
    void aLingWhoseSquadCannotSpareItIsNeverPulledWhateverItsRole() {
        assertFalse(UnitManager.mayPullAsZerglingScout(UnitRole.FIGHT, false));
        assertFalse(UnitManager.mayPullAsZerglingScout(UnitRole.IDLE, false));
        assertFalse(UnitManager.mayPullAsZerglingScout(UnitRole.RALLY, false));
    }

    @Test
    void aLingItsSquadCanSpareMayBePulled() {
        assertTrue(UnitManager.mayPullAsZerglingScout(UnitRole.FIGHT, true));
        assertTrue(UnitManager.mayPullAsZerglingScout(UnitRole.RALLY, true));
        assertTrue(UnitManager.mayPullAsZerglingScout(UnitRole.IDLE, true));
    }

    @Test
    void aLingInNoFightSquadIsAlwaysFreeToScout() {
        assertTrue(UnitManager.squadCanLend(false, 0));
        assertTrue(UnitManager.mayPullAsZerglingScout(UnitRole.IDLE, UnitManager.squadCanLend(false, 0)));
    }

    @Test
    void aLingInAFightSquadNeedsTheSquadToHaveOneToSpare() {
        assertFalse(UnitManager.squadCanLend(true, 0));
        assertTrue(UnitManager.squadCanLend(true, 1));
    }

    @Test
    void aLingInNoSquadOnARunbyOrScoutingIsStillNotPulled() {
        assertFalse(UnitManager.mayPullAsZerglingScout(UnitRole.RUNBY, UnitManager.squadCanLend(false, 0)));
        assertFalse(UnitManager.mayPullAsZerglingScout(UnitRole.SCOUT, UnitManager.squadCanLend(false, 0)));
    }
}
