package unit.squad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReinforcementEndTest {

    @Test
    void aRoutedSquadKeepsGoingWhileItsArmyFightsOrRetreats() {
        assertNull(SquadManager.reinforcementEnd(SquadStatus.RALLY, SquadStatus.FIGHT, false, false));
        assertNull(SquadManager.reinforcementEnd(SquadStatus.RALLY, SquadStatus.RETREAT, false, false));
    }

    @Test
    void theRoutingEndsWhenTheArmyIsGone() {
        assertEquals(SquadManager.REINFORCE_TARGET_GONE,
                SquadManager.reinforcementEnd(SquadStatus.RALLY, null, true, false));
    }

    @Test
    void theRoutingEndsWhenTheSquadLeavesTheRallyStatus() {
        assertEquals(SquadManager.REINFORCE_PREEMPTED,
                SquadManager.reinforcementEnd(SquadStatus.CONTAIN, SquadStatus.FIGHT, false, false));
    }

    @Test
    void theRoutingEndsWhenTheSquadIsThreatened() {
        assertEquals(SquadManager.REINFORCE_THREATENED,
                SquadManager.reinforcementEnd(SquadStatus.RALLY, SquadStatus.FIGHT, true, false));
    }

    @Test
    void aSquadThreatenedInsideTheArmysSupportRadiusHasArrived() {
        assertEquals(SquadManager.REINFORCE_ARRIVED,
                SquadManager.reinforcementEnd(SquadStatus.RALLY, SquadStatus.FIGHT, true, true));
    }

    @Test
    void theRoutingEndsWhenTheArmyStopsFighting() {
        assertEquals(SquadManager.REINFORCE_TARGET_IDLE,
                SquadManager.reinforcementEnd(SquadStatus.RALLY, SquadStatus.RALLY, false, false));
        assertEquals(SquadManager.REINFORCE_TARGET_IDLE,
                SquadManager.reinforcementEnd(SquadStatus.RALLY, SquadStatus.CONTAIN, false, false));
    }

    @Test
    void aMergeClosesTheRoutingByWhatTheSquadMergedWith() {
        assertEquals(SquadManager.REINFORCE_JOINED, SquadManager.mergeOutcome(true, true));
        assertEquals(SquadManager.REINFORCE_JOINED, SquadManager.mergeOutcome(true, false));
        assertEquals(SquadManager.REINFORCE_JOINED_OTHER, SquadManager.mergeOutcome(false, true));
        assertEquals(SquadManager.REINFORCE_MERGED_ELSEWHERE, SquadManager.mergeOutcome(false, false));
    }
}
