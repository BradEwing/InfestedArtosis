package strategy.buildorder;

import bwapi.Race;
import org.junit.jupiter.api.Test;
import util.Time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the sunken target rules the build order layer and the reaction layer share.
 */
class SunkenTargetsTest {

    private static final boolean ONE_BASE = true;

    private static final boolean EXPANDED = false;

    private static final Time BEFORE_ONE_BASE_RESPONSE = new Time(4, 30);

    private static final Time ON_ONE_BASE_RESPONSE = new Time(5, 0);

    private static final Time AFTER_ONE_BASE_RESPONSE = new Time(5, 1);

    private static final Time LATE = new Time(14, 0);

    private static final int MATCHUP_BELOW_FLOOR = 1;

    private static final int MATCHUP_ABOVE_FLOOR = 3;

    private static final int THREE_RAX = 3;

    private static final int TWO_RAX = 2;

    private static final int NO_BARRACKS = 0;

    private static final int MATCHUP_SILENT = 0;

    private static final int MATCHUP_ABOVE_BARRACKS_FLOOR = 4;

    private static final int ENEMY_STILL_ON_ONE_BASE = 1;

    private static final int ENEMY_EXPANSION_SEEN = 2;

    private static final int NO_ENEMY_BASE_SEEN = 0;

    private static final boolean GROUND_LEAD = true;

    private static final boolean NO_GROUND_LEAD = false;

    private static final int EVIDENCE_OUR_ZERGLINGS = 20;

    private static final int EVIDENCE_ENEMY_ZERGLINGS = 7;

    @Test
    void readsASingleEarlyRaxAsNoPressure() {
        assertFalse(SunkenTargets.isBarracksPressure(0));
        assertFalse(SunkenTargets.isBarracksPressure(1));
    }

    @Test
    void readsTwoBarracksAsNoPressure() {
        assertFalse(SunkenTargets.isBarracksPressure(SunkenTargets.BARRACKS_PRESSURE_COUNT - 1));
    }

    @Test
    void readsThreeOrMoreBarracksAsPressure() {
        assertTrue(SunkenTargets.isBarracksPressure(SunkenTargets.BARRACKS_PRESSURE_COUNT));
        assertTrue(SunkenTargets.isBarracksPressure(SunkenTargets.BARRACKS_PRESSURE_COUNT + 2));
    }

    @Test
    void lowersThePressureReadWhenTheProductionBehindItDies() {
        assertTrue(SunkenTargets.isBarracksPressure(3));
        assertFalse(SunkenTargets.isBarracksPressure(2));
    }

    @Test
    void asksForNothingOffAnEnemyThatHasExpanded() {
        assertEquals(0, SunkenTargets.oneBaseSunkens(EXPANDED, ENEMY_STILL_ON_ONE_BASE, NO_GROUND_LEAD, AFTER_ONE_BASE_RESPONSE));
        assertEquals(0, SunkenTargets.oneBaseSunkens(EXPANDED, ENEMY_STILL_ON_ONE_BASE, NO_GROUND_LEAD, LATE));
    }

    @Test
    void holdsTheOneBaseFloorBackUntilItsTimeGateOpens() {
        assertEquals(0, SunkenTargets.oneBaseSunkens(ONE_BASE, ENEMY_STILL_ON_ONE_BASE, NO_GROUND_LEAD, BEFORE_ONE_BASE_RESPONSE));
        assertEquals(0, SunkenTargets.oneBaseSunkens(ONE_BASE, ENEMY_STILL_ON_ONE_BASE, NO_GROUND_LEAD, ON_ONE_BASE_RESPONSE));
    }

    @Test
    void asksForTwoSunkensOnceTheOneBaseGateOpens() {
        assertEquals(SunkenTargets.ONE_BASE_SUNKENS,
                SunkenTargets.oneBaseSunkens(ONE_BASE, ENEMY_STILL_ON_ONE_BASE, NO_GROUND_LEAD, AFTER_ONE_BASE_RESPONSE));
    }

    @Test
    void keepsTheOneBaseFloorForTheRestOfTheGame() {
        assertEquals(SunkenTargets.ONE_BASE_SUNKENS, SunkenTargets.oneBaseSunkens(ONE_BASE, ENEMY_STILL_ON_ONE_BASE, NO_GROUND_LEAD, LATE));
    }

    @Test
    void leavesAMatchupTargetAloneWhenTheFloorIsNotOwed() {
        assertEquals(MATCHUP_BELOW_FLOOR,
                SunkenTargets.sunkenTarget(MATCHUP_BELOW_FLOOR, EXPANDED, ENEMY_EXPANSION_SEEN, NO_GROUND_LEAD, NO_BARRACKS, LATE));
        assertEquals(0, SunkenTargets.sunkenTarget(0, ONE_BASE, ENEMY_STILL_ON_ONE_BASE, NO_GROUND_LEAD, NO_BARRACKS, BEFORE_ONE_BASE_RESPONSE));
    }

    @Test
    void raisesAMatchupTargetThatSitsUnderTheOneBaseFloor() {
        assertEquals(SunkenTargets.ONE_BASE_SUNKENS,
                SunkenTargets.sunkenTarget(MATCHUP_BELOW_FLOOR, ONE_BASE, ENEMY_STILL_ON_ONE_BASE,
                        NO_GROUND_LEAD, NO_BARRACKS, AFTER_ONE_BASE_RESPONSE));
    }

    @Test
    void keepsTheHigherMatchupTargetWhenBothRulesFire() {
        assertEquals(MATCHUP_ABOVE_FLOOR,
                SunkenTargets.sunkenTarget(MATCHUP_ABOVE_FLOOR, ONE_BASE, ENEMY_STILL_ON_ONE_BASE,
                        NO_GROUND_LEAD, NO_BARRACKS, AFTER_ONE_BASE_RESPONSE));
    }

    @Test
    void pinsTheThresholdsThemselvesAndNotOnlyTheirRelationships() {
        assertEquals(3, SunkenTargets.BARRACKS_PRESSURE_COUNT);
        assertEquals(3, SunkenTargets.BARRACKS_PRESSURE_SUNKENS);
        assertEquals(2, SunkenTargets.ONE_BASE_SUNKENS);
    }

    @Test
    void asksForThreeSunkensOffThreeBarracksWithNoMatchupClassInPlay() {
        assertEquals(SunkenTargets.BARRACKS_PRESSURE_SUNKENS, SunkenTargets.barracksPressureSunkens(THREE_RAX));
    }

    @Test
    void asksForNothingOffFewerBarracksThanThePressureCount() {
        assertEquals(0, SunkenTargets.barracksPressureSunkens(NO_BARRACKS));
        assertEquals(0, SunkenTargets.barracksPressureSunkens(TWO_RAX));
    }

    @Test
    void isInertAgainstAnOpponentWithNoBarracksAtAll() {
        assertEquals(MATCHUP_SILENT, SunkenTargets.sunkenTarget(MATCHUP_SILENT, EXPANDED, ENEMY_EXPANSION_SEEN, NO_GROUND_LEAD, NO_BARRACKS, LATE));
    }

    /**
     * SpeedlingAllIn and every opener extend BuildOrder directly, so matchupSunkens is the zero
     * default and this floor is the only thing that can answer three Barracks on those builds.
     */
    @Test
    void raisesASilentMatchupToTheBarracksFloor() {
        assertEquals(SunkenTargets.BARRACKS_PRESSURE_SUNKENS,
                SunkenTargets.sunkenTarget(MATCHUP_SILENT, EXPANDED, ENEMY_EXPANSION_SEEN, NO_GROUND_LEAD, THREE_RAX, BEFORE_ONE_BASE_RESPONSE));
    }

    @Test
    void keepsAMatchupTargetThatAlreadyOutbidsBothFloors() {
        assertEquals(MATCHUP_ABOVE_BARRACKS_FLOOR,
                SunkenTargets.sunkenTarget(MATCHUP_ABOVE_BARRACKS_FLOOR, ONE_BASE, ENEMY_STILL_ON_ONE_BASE,
                        NO_GROUND_LEAD, THREE_RAX, AFTER_ONE_BASE_RESPONSE));
    }

    @Test
    void takesTheHigherOfTheTwoFloorsWhenBothApply() {
        assertEquals(SunkenTargets.BARRACKS_PRESSURE_SUNKENS,
                SunkenTargets.sunkenTarget(MATCHUP_SILENT, ONE_BASE, ENEMY_STILL_ON_ONE_BASE, NO_GROUND_LEAD, THREE_RAX, AFTER_ONE_BASE_RESPONSE));
        assertEquals(SunkenTargets.ONE_BASE_SUNKENS,
                SunkenTargets.sunkenTarget(MATCHUP_SILENT, ONE_BASE, ENEMY_STILL_ON_ONE_BASE, NO_GROUND_LEAD, TWO_RAX, AFTER_ONE_BASE_RESPONSE));
    }

    @Test
    void dropsTheOneBaseFloorOnceTheEnemyExpansionIsActuallySeen() {
        assertEquals(0, SunkenTargets.oneBaseSunkens(ONE_BASE, ENEMY_EXPANSION_SEEN, NO_GROUND_LEAD, AFTER_ONE_BASE_RESPONSE));
        assertEquals(0, SunkenTargets.oneBaseSunkens(ONE_BASE, ENEMY_EXPANSION_SEEN, NO_GROUND_LEAD, LATE));
    }

    @Test
    void keepsTheOneBaseFloorWhileNothingSeenContradictsTheDetection() {
        assertEquals(SunkenTargets.ONE_BASE_SUNKENS,
                SunkenTargets.oneBaseSunkens(ONE_BASE, NO_ENEMY_BASE_SEEN, NO_GROUND_LEAD, AFTER_ONE_BASE_RESPONSE));
    }

    @Test
    void leavesTheBarracksFloorStandingWhenTheEnemyExpansionRetractsTheOneBaseFloor() {
        assertEquals(SunkenTargets.BARRACKS_PRESSURE_SUNKENS,
                SunkenTargets.sunkenTarget(MATCHUP_SILENT, ONE_BASE, ENEMY_EXPANSION_SEEN, NO_GROUND_LEAD, THREE_RAX, LATE));
    }

    @Test
    void dropsTheOneBaseFloorWhileOurGroundArmyLeads() {
        assertEquals(0, SunkenTargets.oneBaseSunkens(ONE_BASE, ENEMY_STILL_ON_ONE_BASE, GROUND_LEAD, AFTER_ONE_BASE_RESPONSE));
        assertEquals(0, SunkenTargets.oneBaseSunkens(ONE_BASE, NO_ENEMY_BASE_SEEN, GROUND_LEAD, LATE));
        assertEquals(0,
                SunkenTargets.sunkenTarget(MATCHUP_SILENT, ONE_BASE, ENEMY_STILL_ON_ONE_BASE, GROUND_LEAD, NO_BARRACKS, AFTER_ONE_BASE_RESPONSE));
    }

    @Test
    void readsTheZvZEvidenceBalanceAsAGroundLeadThatDropsTheFloor() {
        boolean lead = SunkenTargets.hasGroundLead(Race.Zerg, EVIDENCE_OUR_ZERGLINGS, EVIDENCE_ENEMY_ZERGLINGS);

        assertTrue(lead);
        assertEquals(0, SunkenTargets.oneBaseSunkens(ONE_BASE, ENEMY_STILL_ON_ONE_BASE, lead, AFTER_ONE_BASE_RESPONSE));
    }

    @Test
    void firesTheOneBaseFloorAsBeforeWhileTheEnemyLeadsOnGround() {
        boolean lead = SunkenTargets.hasGroundLead(Race.Zerg, EVIDENCE_ENEMY_ZERGLINGS, EVIDENCE_OUR_ZERGLINGS);

        assertFalse(lead);
        assertEquals(SunkenTargets.ONE_BASE_SUNKENS, SunkenTargets.oneBaseSunkens(ONE_BASE, ENEMY_STILL_ON_ONE_BASE, lead, AFTER_ONE_BASE_RESPONSE));
    }

    @Test
    void firesTheOneBaseFloorWhenTheZerglingsAreLevelOrOurLeadIsUnderTheThreshold() {
        assertFalse(SunkenTargets.hasGroundLead(Race.Zerg, 7, 7));
        assertFalse(SunkenTargets.hasGroundLead(Race.Zerg, 7 + SunkenTargets.ZERGLING_LEAD - 1, 7));
        assertTrue(SunkenTargets.hasGroundLead(Race.Zerg, 7 + SunkenTargets.ZERGLING_LEAD, 7));
    }

    @Test
    void readsNoGroundLeadAgainstARaceWhoseArmyIsNotZerglings() {
        assertFalse(SunkenTargets.hasGroundLead(Race.Protoss, EVIDENCE_OUR_ZERGLINGS, 0));
        assertFalse(SunkenTargets.hasGroundLead(Race.Terran, EVIDENCE_OUR_ZERGLINGS, 0));
        assertFalse(SunkenTargets.hasGroundLead(Race.Random, EVIDENCE_OUR_ZERGLINGS, 0));
        assertFalse(SunkenTargets.hasGroundLead(Race.Unknown, EVIDENCE_OUR_ZERGLINGS, 0));
    }

    @Test
    void leavesTheBarracksFloorStandingWhileOurGroundArmyLeads() {
        assertEquals(SunkenTargets.BARRACKS_PRESSURE_SUNKENS,
                SunkenTargets.sunkenTarget(MATCHUP_SILENT, ONE_BASE, ENEMY_STILL_ON_ONE_BASE, GROUND_LEAD, THREE_RAX, AFTER_ONE_BASE_RESPONSE));
        assertEquals(SunkenTargets.BARRACKS_PRESSURE_SUNKENS,
                SunkenTargets.sunkenTarget(MATCHUP_SILENT, EXPANDED, ENEMY_EXPANSION_SEEN, GROUND_LEAD, THREE_RAX, LATE));
    }

    @Test
    void leavesTheMatchupTargetStandingWhileOurGroundArmyLeads() {
        assertEquals(MATCHUP_BELOW_FLOOR,
                SunkenTargets.sunkenTarget(MATCHUP_BELOW_FLOOR, ONE_BASE, ENEMY_STILL_ON_ONE_BASE,
                        GROUND_LEAD, NO_BARRACKS, AFTER_ONE_BASE_RESPONSE));
        assertEquals(MATCHUP_ABOVE_FLOOR,
                SunkenTargets.sunkenTarget(MATCHUP_ABOVE_FLOOR, ONE_BASE, ENEMY_STILL_ON_ONE_BASE, GROUND_LEAD, NO_BARRACKS, LATE));
    }

    /**
     * The Zerg matchup's "enemy up zerglings" term reads this comparison with the enemy as leader,
     * and it must answer exactly as the inline enemyZerglings >= ourZerglings + 3 did.
     */
    @Test
    void answersTheZergMatchupZerglingComparisonExactlyAsTheInlineRuleDid() {
        for (int enemyZerglings = 0; enemyZerglings <= 40; enemyZerglings++) {
            for (int ourZerglings = 0; ourZerglings <= 40; ourZerglings++) {
                assertEquals(enemyZerglings >= ourZerglings + 3, SunkenTargets.isZerglingLead(enemyZerglings, ourZerglings));
            }
        }
    }

    @Test
    void pinsTheZerglingLeadThreshold() {
        assertEquals(3, SunkenTargets.ZERGLING_LEAD);
    }
}
