package unit.squad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoutLendTest {

    @Test
    void aRallyingGroundSquadAboveTheFloorLendsAScout() {
        assertTrue(SquadManager.mayLendScout(SquadStatus.RALLY, true, false, SquadManager.SCOUT_LEND_FLOOR + 1));
    }

    @Test
    void aSquadAtTheFloorLendsNothing() {
        assertFalse(SquadManager.mayLendScout(SquadStatus.RALLY, true, false, SquadManager.SCOUT_LEND_FLOOR));
    }

    @Test
    void squadsThatAreContainingRetreatingOnARunbyOrHarassingLendNothing() {
        for (SquadStatus status : SquadStatus.values()) {
            if (status != SquadStatus.RALLY && status != SquadStatus.FIGHT) {
                assertFalse(SquadManager.mayLendScout(status, true, false, 40), status.name());
            }
        }
    }

    @Test
    void aFightingGroundSquadLendsOnlyAboveItsLargerFloor() {
        assertTrue(SquadManager.mayLendScout(SquadStatus.FIGHT, true, false,
                SquadManager.FIGHT_SCOUT_LEND_FLOOR + 1));
        assertFalse(SquadManager.mayLendScout(SquadStatus.FIGHT, true, false,
                SquadManager.FIGHT_SCOUT_LEND_FLOOR));
    }

    @Test
    void aSquadJoiningAContainmentLendsNothing() {
        assertFalse(SquadManager.mayLendScout(SquadStatus.RALLY, true, true, 40));
    }

    @Test
    void anAirSquadLendsNothing() {
        assertFalse(SquadManager.mayLendScout(SquadStatus.RALLY, false, false, 40));
    }
}
