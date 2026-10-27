package unit.squad.horizon;

import bwapi.Race;
import bwapi.UnitType;
import info.TechProgression;
import org.junit.jupiter.api.Test;
import unit.squad.CombatSimulator.CombatResult;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelativeSpeedTest {

    private static final double LING = RelativeSpeed.speed(UnitType.Zerg_Zergling, false);
    private static final double SPED_LING = RelativeSpeed.speed(UnitType.Zerg_Zergling, true);
    private static final double ENGAGE_THRESH = HorizonCombatSimulator.engageThreshold(Race.Zerg);
    private static final double RETREAT_THRESH = HorizonCombatSimulator.retreatThreshold(Race.Zerg);
    private static final double ENEMY = 10;
    private static final double NEAR = 64;

    private static RelativeSpeed.Mix lings(boolean upgraded, double distance) {
        RelativeSpeed.Mix mix = new RelativeSpeed.Mix();
        mix.addGround(RelativeSpeed.speed(UnitType.Zerg_Zergling, upgraded), ENEMY, distance);
        return mix;
    }

    private static TechProgression tech(boolean metabolicBoost) {
        TechProgression tech = new TechProgression();
        tech.setMetabolicBoost(metabolicBoost);
        return tech;
    }

    @Test
    void anUpgradedUnitOutrunsAnUnupgradedOneOfItsTypeAndOtherTypesRunAtTheirTopSpeed() {
        assertTrue(SPED_LING > LING);
        assertEquals(UnitType.Protoss_Zealot.topSpeed(), RelativeSpeed.speed(UnitType.Protoss_Zealot, true), 1e-9);
        assertEquals(SPED_LING, RelativeSpeed.speed(UnitType.Zerg_Zergling, true), 1e-9);
    }

    @Test
    void unspedLingsAgainstUnspedLingsAreNotPenalisedOnEitherSide() {
        List<Double> ours = Collections.singletonList(LING);

        assertEquals(1.0, HorizonCombatSimulator.speedPenalty(UnitType.Zerg_Zergling, tech(false), lings(false, NEAR)),
                1e-9);
        assertEquals(1.0, HorizonCombatSimulator.enemySpeedMod(true, UnitType.Zerg_Zergling, false, ours), 1e-9);
    }

    @Test
    void unspedLingsAgainstSpedLingsArePenalisedAndSpedAgainstUnspedAreNotPenalisedForUs() {
        assertEquals(RelativeSpeed.UPGRADE_PENALTY,
                HorizonCombatSimulator.speedPenalty(UnitType.Zerg_Zergling, tech(false), lings(true, NEAR)), 1e-9);
        assertEquals(1.0, HorizonCombatSimulator.speedPenalty(UnitType.Zerg_Zergling, tech(true), lings(false, NEAR)),
                1e-9);
        assertEquals(1.0, HorizonCombatSimulator.speedPenalty(UnitType.Zerg_Zergling, tech(true), lings(true, NEAR)),
                1e-9);
    }

    @Test
    void anUnupgradedEnemyLingIsPricedDownOnlyAgainstOurFasterUnits() {
        List<Double> sped = Collections.singletonList(SPED_LING);
        List<Double> unsped = Collections.singletonList(LING);

        assertEquals(RelativeSpeed.UPGRADE_PENALTY, HorizonCombatSimulator.enemySpeedMod(true,
                UnitType.Zerg_Zergling, false, sped), 1e-9);
        assertEquals(1.0, HorizonCombatSimulator.enemySpeedMod(true, UnitType.Zerg_Zergling, false, unsped), 1e-9);
        assertEquals(1.0, HorizonCombatSimulator.enemySpeedMod(true, UnitType.Zerg_Zergling, true, sped), 1e-9);
        assertEquals(1.0, HorizonCombatSimulator.enemySpeedMod(true, UnitType.Protoss_Zealot, false, sped), 1e-9);
    }

    @Test
    void thePenaltyScalesWithTheShareOfTheEnemyThatIsFaster() {
        RelativeSpeed.Mix mix = new RelativeSpeed.Mix();
        mix.addGround(SPED_LING, ENEMY, NEAR);
        mix.addGround(LING, ENEMY, NEAR);

        assertEquals(0.5, mix.fasterShare(LING), 1e-9);
        assertEquals(1.0 - 0.5 * (1.0 - RelativeSpeed.UPGRADE_PENALTY),
                HorizonCombatSimulator.speedPenalty(UnitType.Zerg_Zergling, tech(false), mix), 1e-9);
        assertEquals(0.5, RelativeSpeed.shareFasterThan(Arrays.asList(SPED_LING, LING), LING), 1e-9);
        assertEquals(0.0, RelativeSpeed.shareFasterThan(Collections.emptyList(), LING), 1e-9);
    }

    @Test
    void aSquadCannotEscapeAnEqualSpeedMajorityWithinTheEscapeDistance() {
        assertFalse(lings(false, NEAR).canEscape(LING));
        assertFalse(lings(true, NEAR).canEscape(LING));
        assertTrue(lings(false, RelativeSpeed.ESCAPE_DISTANCE + 1).canEscape(LING));
        assertTrue(lings(false, NEAR).canEscape(SPED_LING));
    }

    @Test
    void aSquadEscapesWhenTheChasersAreNotTheMajorityOfTheEnemyStrength() {
        RelativeSpeed.Mix mix = new RelativeSpeed.Mix();
        mix.addGround(LING, ENEMY, NEAR);
        mix.addOther(ENEMY * 2);
        assertTrue(mix.canEscape(LING));

        RelativeSpeed.Mix slow = new RelativeSpeed.Mix();
        slow.addGround(LING / 2, ENEMY, NEAR);
        assertTrue(slow.canEscape(LING));

        assertTrue(new RelativeSpeed.Mix().canEscape(LING));
        assertTrue(lings(false, NEAR).canEscape(Double.POSITIVE_INFINITY));
    }

    @Test
    void anEngagedSquadJustUnderTheRetreatThresholdKeepsFightWhenItCannotEscape() {
        double ratio = RETREAT_THRESH - 0.05;
        CombatResult raw = HorizonCombatSimulator.selectResult(ratio * ENEMY, 0, ENEMY, 0, 0, false, ENGAGE_THRESH);
        boolean blocked = !lings(false, NEAR).canEscape(LING);
        double blockedThreshold = RelativeSpeed.retreatThreshold(true, blocked, RETREAT_THRESH);

        assertEquals(CombatResult.RETREAT, raw);
        assertTrue(blocked);
        assertEquals(CombatResult.ENGAGE, FightBand.apply(raw, true, ratio, blockedThreshold));
        assertEquals(CombatResult.RETREAT, FightBand.apply(raw, true, ratio, RETREAT_THRESH));
    }

    @Test
    void aSquadFarFromEqualSpeedChasersStillRetreatsBelowTheRetreatThreshold() {
        double ratio = RETREAT_THRESH - 0.05;
        CombatResult raw = HorizonCombatSimulator.selectResult(ratio * ENEMY, 0, ENEMY, 0, 0, false, ENGAGE_THRESH);
        boolean blocked = !lings(false, RelativeSpeed.ESCAPE_DISTANCE + 1).canEscape(LING);
        double threshold = RelativeSpeed.retreatThreshold(true, blocked, RETREAT_THRESH);

        assertEquals(RETREAT_THRESH, threshold, 1e-9);
        assertEquals(CombatResult.RETREAT, FightBand.apply(raw, true, ratio, threshold));
    }

    @Test
    void aSquadBelowTheLoweredThresholdStillRetreats() {
        double ratio = RelativeSpeed.retreatThreshold(true, true, RETREAT_THRESH) - 0.05;
        CombatResult raw = HorizonCombatSimulator.selectResult(ratio * ENEMY, 0, ENEMY, 0, 0, false, ENGAGE_THRESH);

        assertEquals(CombatResult.RETREAT,
                FightBand.apply(raw, true, ratio, RelativeSpeed.retreatThreshold(true, true, RETREAT_THRESH)));
    }

    @Test
    void damageNoLongerTurnsAnEngagedSquadIntoARetreatItCannotFinishUntilItIsFarWeaker() {
        double fullHealthRatio = 1.1;
        double blockedThreshold = RelativeSpeed.retreatThreshold(true, true, RETREAT_THRESH);
        int firstFreeRetreat = -1;
        int firstBlockedRetreat = -1;
        for (int step = 0; step <= 10; step++) {
            int hp = 35 - step * 3;
            double ratio = fullHealthRatio * HorizonCombatSimulator.hpWeighting(hp, 0, 35, 0);
            CombatResult raw = HorizonCombatSimulator.selectResult(ratio * ENEMY, 0, ENEMY, 0, 0, false,
                    ENGAGE_THRESH);
            if (firstFreeRetreat < 0 && FightBand.apply(raw, true, ratio, RETREAT_THRESH) == CombatResult.RETREAT) {
                firstFreeRetreat = step;
            }
            if (firstBlockedRetreat < 0 && FightBand.apply(raw, true, ratio, blockedThreshold) == CombatResult.RETREAT) {
                firstBlockedRetreat = step;
            }
        }

        assertTrue(firstFreeRetreat > 0);
        assertTrue(firstBlockedRetreat > firstFreeRetreat);
    }

    @Test
    void withTheSwitchOffTheDecisionIsMains() {
        assertEquals(RETREAT_THRESH, RelativeSpeed.retreatThreshold(false, true, RETREAT_THRESH), 1e-9);
        assertNull(RelativeSpeed.pricedAgainst(false, lings(true, NEAR)));
        assertEquals(RelativeSpeed.UPGRADE_PENALTY,
                HorizonCombatSimulator.speedPenalty(UnitType.Zerg_Zergling, tech(false), null), 1e-9);
        assertEquals(RelativeSpeed.UPGRADE_PENALTY, HorizonCombatSimulator.speedPenalty(UnitType.Zerg_Zergling,
                tech(false), RelativeSpeed.pricedAgainst(false, lings(false, NEAR))), 1e-9);
        assertEquals(1.0, HorizonCombatSimulator.speedPenalty(UnitType.Zerg_Zergling, tech(true), null), 1e-9);
        assertEquals(1.0, HorizonCombatSimulator.enemySpeedMod(false, UnitType.Zerg_Zergling, false,
                Collections.singletonList(SPED_LING)), 1e-9);
    }

    @Test
    void upgradedSpeedsOrderAsInTheGame() {
        assertEquals(LING * 1.5, SPED_LING, 1e-9);
        assertTrue(SPED_LING > RelativeSpeed.speed(UnitType.Terran_Vulture, false));
        assertTrue(RelativeSpeed.speed(UnitType.Zerg_Hydralisk, true) > RelativeSpeed.speed(UnitType.Protoss_Dragoon,
                false));
        assertEquals(LING, RelativeSpeed.speed(UnitType.Zerg_Hydralisk, true), 1e-9);
    }

    @Test
    void lurkersMinesScarabsAndBuildingsAreNotChasers() {
        assertFalse(RelativeSpeed.canChase(UnitType.Zerg_Lurker));
        assertFalse(RelativeSpeed.canChase(UnitType.Terran_Vulture_Spider_Mine));
        assertFalse(RelativeSpeed.canChase(UnitType.Protoss_Scarab));
        assertFalse(RelativeSpeed.canChase(UnitType.Zerg_Sunken_Colony));
        assertFalse(RelativeSpeed.canChase(UnitType.Zerg_Mutalisk));
        assertTrue(RelativeSpeed.canChase(UnitType.Zerg_Zergling));
        assertTrue(RelativeSpeed.canChase(UnitType.Protoss_Zealot));
    }

    @Test
    void thePenaltyClampsTheShare() {
        assertEquals(1.0, RelativeSpeed.penalty(-1), 1e-9);
        assertEquals(RelativeSpeed.UPGRADE_PENALTY, RelativeSpeed.penalty(2), 1e-9);
    }
}
