package unit.squad;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

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

    @Test
    void absorbingAnotherEpisodeAddsItsLossesToTheWindowAndTheTotal() {
        ContainmentAttrition attrition = lingsLost(3, START);

        attrition.absorb(lingsLost(2, START + 10));

        assertEquals(5 * LING, attrition.getTotalLost());
        assertTrue(attrition.isBleeding(START + 20, 17 * LING));
    }

    @Test
    void absorbedLossesExpireInFrameOrder() {
        ContainmentAttrition attrition = lingsLost(1, START + 400);

        attrition.absorb(lingsLost(1, START));

        assertTrue(attrition.isBleeding(START + ContainmentAttrition.WINDOW_FRAMES - 1, 6 * LING));
        assertFalse(attrition.isBleeding(START + ContainmentAttrition.WINDOW_FRAMES, 6 * LING));
    }

    @Test
    void absorbedKillsCountAgainstTheLosses() {
        ContainmentAttrition attrition = lingsLost(6, START);
        ContainmentAttrition other = new ContainmentAttrition();
        other.recordKill(START, MARINE);
        other.recordKill(START, MARINE);

        attrition.absorb(other);

        assertFalse(attrition.isBleeding(START + 1, 16 * LING));
    }

    @Test
    void aMergeThatStaysInContainCarriesTheAttritionOfEveryContainingSource() {
        Squad merged = new GroundSquad();
        merged.inheritStateFrom(Arrays.asList(containing(3), containing(2)));

        assertEquals(SquadStatus.CONTAIN, merged.getStatus());
        assertEquals(5 * LING, merged.getContainmentAttrition().getTotalLost());
        assertTrue(merged.getContainmentAttrition().isBleeding(START + 1, 17 * LING));
    }

    @Test
    void aMergeWithANonContainingSquadKeepsOnlyTheContainingSourcesAttrition() {
        Squad rally = new GroundSquad();
        rally.setStatus(SquadStatus.RALLY);
        rally.getContainmentAttrition().recordLoss(START, LING);

        Squad merged = new GroundSquad();
        merged.inheritStateFrom(Arrays.asList(containing(3), rally));

        assertEquals(SquadStatus.CONTAIN, merged.getStatus());
        assertEquals(3 * LING, merged.getContainmentAttrition().getTotalLost());
    }

    @Test
    void aMergeOutOfContainDropsTheAttrition() {
        Squad fight = new GroundSquad();
        fight.setStatus(SquadStatus.FIGHT);

        Squad merged = new GroundSquad();
        merged.inheritStateFrom(Arrays.asList(containing(3), fight));

        assertEquals(SquadStatus.FIGHT, merged.getStatus());
        assertEquals(0, merged.getContainmentAttrition().getTotalLost());
    }

    private static Squad containing(int lingsLost) {
        Squad squad = new GroundSquad();
        squad.setStatus(SquadStatus.CONTAIN);
        squad.startContainLock(START - 100);
        for (int i = 0; i < lingsLost; i++) {
            squad.getContainmentAttrition().recordLoss(START, LING);
        }
        return squad;
    }
}
