package unit.squad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadManagerBunkerGateTest {

    @Test
    void theGateReadsAnAdvanceIntoFightOnlyFromASquadNotAlreadyFighting() {
        assertTrue(SquadManager.bunkerGateReads(SquadStatus.RETREAT, false));
        assertTrue(SquadManager.bunkerGateReads(SquadStatus.RALLY, false));
        assertFalse(SquadManager.bunkerGateReads(SquadStatus.FIGHT, false));
    }

    @Test
    void theGateStandsDownWhileOneOfOurBasesIsThreatened() {
        assertFalse(SquadManager.bunkerGateReads(SquadStatus.RETREAT, true));
    }

    @Test
    void aSquadInFightCountsAsTheArmyAttacking() {
        Squad fighting = new GroundSquad();
        fighting.setStatus(SquadStatus.FIGHT);
        Squad rallying = new GroundSquad();
        rallying.setStatus(SquadStatus.RALLY);

        assertTrue(SquadManager.anyGroundSquadAttacking(java.util.Arrays.asList(rallying, fighting)));
        assertFalse(SquadManager.anyGroundSquadAttacking(java.util.Collections.singletonList(rallying)));
    }
}
