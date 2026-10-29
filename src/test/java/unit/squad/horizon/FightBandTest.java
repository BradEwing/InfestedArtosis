package unit.squad.horizon;

import bwapi.Race;
import org.junit.jupiter.api.Test;
import unit.squad.CombatSimulator.CombatResult;
import unit.squad.HeldRetreat;
import unit.squad.SquadStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.CombatSimulator.CombatResult.ADVANCE;
import static unit.squad.CombatSimulator.CombatResult.ENGAGE;
import static unit.squad.CombatSimulator.CombatResult.RETREAT;

class FightBandTest {

    private static final double ENGAGE_THRESH = HorizonCombatSimulator.engageThreshold(Race.Zerg);
    private static final double RETREAT_THRESH = HorizonCombatSimulator.retreatThreshold(Race.Zerg);
    private static final double IN_BAND = 1.0;
    private static final double BELOW_RETREAT = 0.6;
    private static final double ENEMY = 10;
    private static final int FRAME = 5000;
    private static final boolean NO_TANK = false;
    private static final int UNLOCKED = 0;

    private final HorizonCombatSimulator sim = new HorizonCombatSimulator();

    private CombatResult verdict(double ratio, boolean engaged, boolean retreating) {
        CombatResult single = HorizonCombatSimulator.selectResult(ratio * ENEMY, 0, ENEMY, 0, 0, false, ENGAGE_THRESH);
        CombatResult banded = FightBand.apply(single, engaged, ratio, RETREAT_THRESH);
        return sim.holdVerdict(new HeldRetreat(), banded, FRAME, ratio, ENGAGE_THRESH, NO_TANK, retreating, UNLOCKED);
    }

    @Test
    void theZergBandSitsBetweenTheRetreatAndEngageThresholds() {
        assertTrue(RETREAT_THRESH < IN_BAND && IN_BAND < ENGAGE_THRESH);
        assertTrue(BELOW_RETREAT < RETREAT_THRESH);
    }

    @Test
    void anEngagedSquadInsideTheBandKeepsFight() {
        assertEquals(RETREAT, HorizonCombatSimulator.selectResult(IN_BAND * ENEMY, 0, ENEMY, 0, 0, false,
                ENGAGE_THRESH), "the single engage cut alone retreats");
        assertEquals(ENGAGE, verdict(IN_BAND, true, false));
    }

    @Test
    void anEngagedSquadBelowTheRetreatThresholdRetreats() {
        assertEquals(RETREAT, verdict(BELOW_RETREAT, true, false));
    }

    @Test
    void aRetreatingSquadInsideTheBandKeepsRetreat() {
        assertEquals(RETREAT, verdict(IN_BAND, false, true));
    }

    @Test
    void aSquadThatIsNotEngagedNeedsTheEngageThreshold() {
        assertEquals(RETREAT, verdict(IN_BAND, false, false));
        assertEquals(ENGAGE, verdict(ENGAGE_THRESH, false, false));
    }

    @Test
    void theBandNeverTurnsAnAdvanceOrEngageIntoARetreat() {
        assertEquals(ADVANCE, FightBand.apply(ADVANCE, true, BELOW_RETREAT, RETREAT_THRESH));
        assertEquals(ENGAGE, FightBand.apply(ENGAGE, true, ENGAGE_THRESH, RETREAT_THRESH));
    }

    @Test
    void engagedNeedsTheSwitchAFightStatusAndARecentEngageRead() {
        int gap = HorizonCombatSimulator.MAX_SIM_GAP_FRAMES;
        assertTrue(FightBand.engaged(true, SquadStatus.FIGHT, false, ENGAGE, FRAME - 1, FRAME, gap));
        assertTrue(FightBand.engaged(true, SquadStatus.FIGHT, false, ENGAGE, FRAME - gap, FRAME, gap));
        assertFalse(FightBand.engaged(false, SquadStatus.FIGHT, true, ENGAGE, FRAME - 1, FRAME, gap), "switch off");
        assertFalse(FightBand.engaged(true, SquadStatus.RETREAT, true, ENGAGE, FRAME - 1, FRAME, gap));
        assertFalse(FightBand.engaged(true, SquadStatus.CONTAIN, true, ENGAGE, FRAME - 1, FRAME, gap));
        assertFalse(FightBand.engaged(true, SquadStatus.FIGHT, false, ADVANCE, FRAME - 1, FRAME, gap));
        assertFalse(FightBand.engaged(true, SquadStatus.FIGHT, false, RETREAT, FRAME - 1, FRAME, gap));
        assertFalse(FightBand.engaged(true, SquadStatus.FIGHT, false, null, FRAME - 1, FRAME, gap));
        assertFalse(FightBand.engaged(true, SquadStatus.FIGHT, false, ENGAGE, FRAME - gap - 1, FRAME, gap));
    }

    @Test
    void aCommittedFightSquadIsEngagedWithoutARecentEngageRead() {
        int gap = HorizonCombatSimulator.MAX_SIM_GAP_FRAMES;
        assertTrue(FightBand.engaged(true, SquadStatus.FIGHT, true, null, 0, FRAME, gap), "a merge has no read");
        assertTrue(FightBand.engaged(true, SquadStatus.FIGHT, true, RETREAT, FRAME - 1, FRAME, gap));
        assertTrue(FightBand.engaged(true, SquadStatus.FIGHT, true, ADVANCE, FRAME - 1, FRAME, gap));
    }

    @Test
    void withTheSwitchOffAnEngagedFightSquadInsideTheBandRetreatsAsOnMain() {
        boolean engaged = FightBand.engaged(false, SquadStatus.FIGHT, true, ENGAGE, FRAME - 1, FRAME,
                HorizonCombatSimulator.MAX_SIM_GAP_FRAMES);

        assertEquals(RETREAT, verdict(IN_BAND, engaged, false));
    }

    @Test
    void zoneNamesWhereARatioSits() {
        assertEquals(FightBand.Zone.BELOW_RETREAT, FightBand.zone(BELOW_RETREAT, RETREAT_THRESH, ENGAGE_THRESH));
        assertEquals(FightBand.Zone.IN_BAND, FightBand.zone(RETREAT_THRESH, RETREAT_THRESH, ENGAGE_THRESH));
        assertEquals(FightBand.Zone.IN_BAND, FightBand.zone(IN_BAND, RETREAT_THRESH, ENGAGE_THRESH));
        assertEquals(FightBand.Zone.CLEARS_ENGAGE, FightBand.zone(ENGAGE_THRESH, RETREAT_THRESH, ENGAGE_THRESH));
    }

    @Test
    void theRetreatThresholdNeverExceedsALoweredEngageThreshold() {
        assertEquals(RETREAT_THRESH, FightBand.retreatThreshold(RETREAT_THRESH, ENGAGE_THRESH));
        assertEquals(0.6, FightBand.retreatThreshold(RETREAT_THRESH, 0.6));
    }

    @Test
    void perRaceRetreatThresholdsSitBelowTheirEngageThresholds() {
        for (Race race : new Race[] {Race.Zerg, Race.Protoss, Race.Terran, Race.Random}) {
            assertTrue(HorizonCombatSimulator.retreatThreshold(race) < HorizonCombatSimulator.engageThreshold(race),
                    race.name());
        }
    }

    @Test
    void aRunOfBandHeldReadsKeepsTheFrameItBegan() {
        assertEquals(FRAME, FightBand.heldSinceFrame(true, -1, FRAME));
        assertEquals(FRAME - 7, FightBand.heldSinceFrame(true, FRAME - 7, FRAME));
        assertEquals(-1, FightBand.heldSinceFrame(false, FRAME - 7, FRAME));
    }

    @Test
    void theFightLockReleasesAtTheRetreatThresholdOnlyWithTheBandOn() {
        assertEquals(RETREAT_THRESH, FightBand.fightLockReleaseThreshold(true, ENGAGE_THRESH, RETREAT_THRESH));
        assertEquals(ENGAGE_THRESH, FightBand.fightLockReleaseThreshold(false, ENGAGE_THRESH, RETREAT_THRESH));
    }

    @Test
    void aMeasuredReadAtOrAboveTheRetreatThresholdHoldsAboveIt() {
        assertTrue(FightBand.holdsAboveRetreat(true, true, RETREAT_THRESH, RETREAT_THRESH));
        assertFalse(FightBand.holdsAboveRetreat(true, true, BELOW_RETREAT, RETREAT_THRESH));
        assertFalse(FightBand.holdsAboveRetreat(true, false, IN_BAND, RETREAT_THRESH));
        assertFalse(FightBand.holdsAboveRetreat(true, true, IN_BAND, 0));
        assertFalse(FightBand.holdsAboveRetreat(false, true, IN_BAND, RETREAT_THRESH));
    }
}
