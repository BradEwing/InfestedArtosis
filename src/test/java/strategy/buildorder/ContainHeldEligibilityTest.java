package strategy.buildorder;

import bwapi.Race;
import info.GameState;
import org.junit.jupiter.api.Test;
import strategy.buildorder.opener.FourPool;
import strategy.buildorder.terran.CrazyZerg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainHeldEligibilityTest {

    @Test
    void containHeldRoundsRunOnlyAgainstTerranAndProtoss() {
        assertTrue(BuildOrder.containHeldMatchup(Race.Terran));
        assertTrue(BuildOrder.containHeldMatchup(Race.Protoss));
        assertFalse(BuildOrder.containHeldMatchup(Race.Zerg));
        assertFalse(BuildOrder.containHeldMatchup(Race.Unknown));
        assertFalse(BuildOrder.containHeldMatchup(Race.Random));
    }

    @Test
    void speedlingAllInRunsContainHeldRoundsOnlyWithTwelveLivingZerglings() {
        assertEquals(SpeedlingAllIn.ZERGLINGS_BEFORE_EXTRA_DRONES, SpeedlingAllIn.ZERGLINGS_FOR_CONTAIN_HELD_ROUND);
        assertEquals(12, SpeedlingAllIn.ZERGLINGS_FOR_CONTAIN_HELD_ROUND);
        assertFalse(SpeedlingAllIn.runsContainHeldRounds(SpeedlingAllIn.ZERGLINGS_FOR_CONTAIN_HELD_ROUND - 1));
        assertTrue(SpeedlingAllIn.runsContainHeldRounds(SpeedlingAllIn.ZERGLINGS_FOR_CONTAIN_HELD_ROUND));
    }

    @Test
    void fourPoolRunsNoContainHeldRounds() {
        assertFalse(runsContainHeldRounds(new FourPool()));
    }

    @Test
    void aMacroBuildRunsContainHeldRoundsByDefault() {
        assertTrue(runsContainHeldRounds(new CrazyZerg()));
    }

    @Test
    void theSoftCapIsOnePerPatchAndThreePerMiningGeyser() {
        assertEquals(8, GameState.workerSoftCap(8, 0));
        assertEquals(14, GameState.workerSoftCap(8, 2));
        assertEquals(0, GameState.workerSoftCap(0, 0));
    }

    private static boolean runsContainHeldRounds(BuildOrder buildOrder) {
        return buildOrder.runsContainHeldRounds(null);
    }
}
