package unit.squad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReinforcementEndTest {

    @Test
    void aRoutedSquadKeepsGoingWhileItsArmyFightsOrRetreats() {
        assertNull(SquadManager.reinforcementEnd(SquadStatus.RALLY, SquadStatus.FIGHT, false));
        assertNull(SquadManager.reinforcementEnd(SquadStatus.RALLY, SquadStatus.RETREAT, false));
    }

    @Test
    void theRoutingEndsWhenTheArmyIsGone() {
        assertEquals(SquadManager.REINFORCE_TARGET_GONE, SquadManager.reinforcementEnd(SquadStatus.RALLY, null, true));
    }

    @Test
    void theRoutingEndsWhenTheSquadLeavesTheRallyStatus() {
        assertEquals(SquadManager.REINFORCE_PREEMPTED,
                SquadManager.reinforcementEnd(SquadStatus.CONTAIN, SquadStatus.FIGHT, false));
    }

    @Test
    void theRoutingEndsWhenTheSquadIsThreatened() {
        assertEquals(SquadManager.REINFORCE_THREATENED,
                SquadManager.reinforcementEnd(SquadStatus.RALLY, SquadStatus.FIGHT, true));
    }

    @Test
    void theRoutingEndsWhenTheArmyStopsFighting() {
        assertEquals(SquadManager.REINFORCE_TARGET_IDLE,
                SquadManager.reinforcementEnd(SquadStatus.RALLY, SquadStatus.RALLY, false));
        assertEquals(SquadManager.REINFORCE_TARGET_IDLE,
                SquadManager.reinforcementEnd(SquadStatus.RALLY, SquadStatus.CONTAIN, false));
    }
}
