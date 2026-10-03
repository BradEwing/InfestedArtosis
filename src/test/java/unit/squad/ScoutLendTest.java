package unit.squad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoutLendTest {

    private static final int FLOOR = SquadManager.SCOUT_LEND_FLOOR;
    private static final int FIGHT_FLOOR = SquadManager.FIGHT_SCOUT_LEND_FLOOR;

    @Test
    void aRallyingGroundSquadLendsTheLingsAboveTheFloor() {
        assertEquals(3, SquadManager.scoutLendSpare(SquadStatus.RALLY, true, false, false, FLOOR + 3));
    }

    @Test
    void aSquadAtOrBelowTheFloorLendsNothing() {
        assertEquals(0, SquadManager.scoutLendSpare(SquadStatus.RALLY, true, false, false, FLOOR));
        assertEquals(0, SquadManager.scoutLendSpare(SquadStatus.RALLY, true, false, false, 1));
    }

    @Test
    void squadsThatAreContainingRetreatingOnARunbyOrHarassingLendNothing() {
        for (SquadStatus status : SquadStatus.values()) {
            if (status != SquadStatus.RALLY && status != SquadStatus.FIGHT) {
                assertEquals(0, SquadManager.scoutLendSpare(status, true, false, false, 40), status.name());
            }
        }
    }

    @Test
    void aFightingGroundSquadLendsOnlyAboveItsLargerFloor() {
        assertEquals(1, SquadManager.scoutLendSpare(SquadStatus.FIGHT, true, false, false, FIGHT_FLOOR + 1));
        assertEquals(0, SquadManager.scoutLendSpare(SquadStatus.FIGHT, true, false, false, FIGHT_FLOOR));
    }

    @Test
    void aSquadHeldInAFightItMustSeeThroughLendsNothing() {
        assertEquals(0, SquadManager.scoutLendSpare(SquadStatus.FIGHT, true, false, true, 40));
        assertEquals(0, SquadManager.scoutLendSpare(SquadStatus.RALLY, true, false, true, 40));
    }

    @Test
    void aSquadJoiningAContainmentLendsNothing() {
        assertEquals(0, SquadManager.scoutLendSpare(SquadStatus.RALLY, true, true, false, 40));
    }

    @Test
    void anAirSquadLendsNothing() {
        assertEquals(0, SquadManager.scoutLendSpare(SquadStatus.RALLY, false, false, false, 40));
    }
}
