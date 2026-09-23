package unit.squad;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainmentAttritionTest {

    private static final int LING = UnitType.Zerg_Zergling.supplyRequired();
    private static final int MARINE = UnitType.Terran_Marine.supplyRequired();
    private static final int START = 9000;

    private static ContainmentAttrition lingsLost(int count, int frame) {
        ContainmentAttrition attrition = new ContainmentAttrition();
        for (int i = 0; i < count; i++) {
            attrition.recordLoss(frame, LING);
        }
        return attrition;
    }

    @Test
    void losingAFifthOfTheSquadInsideTheWindowWhileKillingNothingBleeds() {
        ContainmentAttrition attrition = lingsLost(5, START);

        assertTrue(attrition.isBleeding(START + ContainmentAttrition.WINDOW_FRAMES - 1, 17 * LING));
    }

    @Test
    void smallerLossesHold() {
        ContainmentAttrition attrition = lingsLost(2, START);

        assertFalse(attrition.isBleeding(START + 1, 20 * LING));
    }

    @Test
    void lossesOutsideTheWindowAreForgiven() {
        ContainmentAttrition attrition = lingsLost(5, START);

        assertFalse(attrition.isBleeding(START + ContainmentAttrition.WINDOW_FRAMES, 17 * LING));
        assertEquals(5 * LING, attrition.getTotalLost());
    }

    @Test
    void aSquadTradingEvenlyIsNotBleeding() {
        ContainmentAttrition attrition = lingsLost(6, START);
        attrition.recordKill(START, MARINE);
        attrition.recordKill(START, MARINE);

        assertFalse(attrition.isBleeding(START + 1, 16 * LING));
    }

    @Test
    void killingTooLittleStillBleeds() {
        ContainmentAttrition attrition = lingsLost(6, START);
        attrition.recordKill(START, MARINE);

        assertTrue(attrition.isBleeding(START + 1, 16 * LING));
    }

    @Test
    void theRuleNeedsALoss() {
        assertFalse(ContainmentAttrition.bleeding(0, 0, 0));
        assertFalse(ContainmentAttrition.bleeding(0, 0, 22));
    }

    @Test
    void resetStartsTheEpisodeOver() {
        ContainmentAttrition attrition = lingsLost(6, START);

        attrition.reset();

        assertEquals(0, attrition.getTotalLost());
        assertFalse(attrition.isBleeding(START + 1, 16 * LING));
    }

    @Test
    void anEpisodeStartClearsTheLastEpisodesLosses() {
        Squad squad = new GroundSquad();
        squad.getContainmentAttrition().recordLoss(START, LING);

        squad.startContainLock(START + 10);

        assertEquals(0, squad.getContainmentAttrition().getTotalLost());
    }

    @Test
    void renewingTheLockInsideAnEpisodeKeepsItsLosses() {
        Squad squad = new GroundSquad();
        squad.startContainLock(START);
        squad.getContainmentAttrition().recordLoss(START + 5, LING);

        squad.startContainLock(START + 10);

        assertEquals(LING, squad.getContainmentAttrition().getTotalLost());
    }
}
