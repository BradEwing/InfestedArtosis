package unit.squad.horizon;

import bwapi.DamageType;
import bwapi.Position;
import bwapi.Race;
import bwapi.UnitSizeType;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import unit.squad.CombatSimulator.CombatResult;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.CombatSimulator.CombatResult.ADVANCE;
import static unit.squad.CombatSimulator.CombatResult.ENGAGE;
import static unit.squad.CombatSimulator.CombatResult.RETREAT;

class HorizonCombatSimulatorTest {

    private static final Position COLONY = new Position(1000, 1000);
    private static final int SUNKEN_RANGE = UnitType.Zerg_Sunken_Colony.groundWeapon().maxRange();
    private static final int SPORE_RANGE = UnitType.Zerg_Spore_Colony.airWeapon().maxRange();
    private static final Map<UnitSizeType, Double> ALL_SMALL =
            Collections.singletonMap(UnitSizeType.Small, 1.0);
    private static final double SUPERSEDED_ANTI_AIR_LITERAL = 2.0;
    private static final double ZERG_ENGAGE_THRESHOLD = HorizonCombatSimulator.engageThreshold(Race.Zerg);
    private static final double TERRAN_ENGAGE_THRESHOLD = HorizonCombatSimulator.engageThreshold(Race.Terran);
    private static final double DISPERSED_SQUAD_STRENGTH = 0;
    private static final double EXPLOSIVE_VERSUS_SMALL =
            UnitStrength.effectiveness(DamageType.Explosive, UnitSizeType.Small);

    private static List<Position> at(int offsetX) {
        return Collections.singletonList(new Position(COLONY.getX() + offsetX, COLONY.getY()));
    }

    private static List<Position> none() {
        return Collections.emptyList();
    }

    @Test
    void friendlyStrengthWithoutRelevantEnemyAdvances() {
        assertEquals(ADVANCE, HorizonCombatSimulator.selectResult(1, 0, 0, 0, false, 1.3));
    }

    @Test
    void noBelievedEnemyAndNoFriendlyStrengthDoesNotRetreat() {
        CombatResult result = HorizonCombatSimulator.selectResult(0, 0, 0, 0, false, ZERG_ENGAGE_THRESHOLD);
        assertNotEquals(RETREAT, result);
        assertEquals(ADVANCE, result);
    }

    @Test
    void aDispersedSquadWithNoBelievedEnemyAdvances() {
        assertEquals(ADVANCE, HorizonCombatSimulator.selectResult(
                DISPERSED_SQUAD_STRENGTH, 0, 0, 0, false, ZERG_ENGAGE_THRESHOLD));
    }

    @Test
    void aDispersedSquadAgainstAMeasuredEnemyStillRetreats() {
        assertEquals(RETREAT, HorizonCombatSimulator.selectResult(
                DISPERSED_SQUAD_STRENGTH, 0, zerglingStrength(4), 0, false, ZERG_ENGAGE_THRESHOLD));
    }

    @Test
    void aMemberBeyondTheFalloffContributesNothing() {
        assertEquals(0.0, HorizonCombatSimulator.distanceWeight(513));
    }

    @Test
    void aMemberInsideTheFalloffStillContributes() {
        assertEquals(1.0, HorizonCombatSimulator.distanceWeight(256));
        assertTrue(HorizonCombatSimulator.distanceWeight(512) > 0);
    }

    @Test
    void anOutnumberedSquadAgainstARealEnemyStillRetreats() {
        assertEquals(RETREAT, HorizonCombatSimulator.selectResult(
                zerglingStrength(2), 0, zerglingStrength(6), 0, false, ZERG_ENGAGE_THRESHOLD));
    }

    @Test
    void enemyStrengthAtMinimumStillAdvances() {
        assertEquals(ADVANCE, HorizonCombatSimulator.selectResult(1, 0, 0.01, 0, false, 1.3));
    }

    @Test
    void enemyStrengthAboveMinimumUsesMeasuredVerdict() {
        assertEquals(RETREAT, HorizonCombatSimulator.selectResult(0.001, 0, 0.0101, 0, false, 1.3));
        assertEquals(ENGAGE, HorizonCombatSimulator.selectResult(1, 0, 0.0101, 0, false, 1.3));
    }

    @Test
    void sunkenCoversGroundThreatInRange() {
        assertTrue(HorizonCombatSimulator.coversThreat(
                UnitType.Zerg_Sunken_Colony, COLONY, at(SUNKEN_RANGE), none()));
    }

    @Test
    void sunkenDoesNotCoverAirThreat() {
        assertFalse(HorizonCombatSimulator.coversThreat(
                UnitType.Zerg_Sunken_Colony, COLONY, none(), at(0)));
    }

    @Test
    void sunkenDoesNotCoverAirThreatWhileCoveringGroundIsImpossible() {
        assertFalse(HorizonCombatSimulator.coversThreat(
                UnitType.Zerg_Sunken_Colony, COLONY, none(), at(SUNKEN_RANGE)));
    }

    @Test
    void sunkenDoesNotCoverDistantGroundThreat() {
        assertFalse(HorizonCombatSimulator.coversThreat(
                UnitType.Zerg_Sunken_Colony, COLONY, at(SUNKEN_RANGE + 512), none()));
    }

    @Test
    void sporeCoversAirThreatInRange() {
        assertTrue(HorizonCombatSimulator.coversThreat(
                UnitType.Zerg_Spore_Colony, COLONY, none(), at(SPORE_RANGE)));
    }

    @Test
    void sporeDoesNotCoverGroundThreat() {
        assertFalse(HorizonCombatSimulator.coversThreat(
                UnitType.Zerg_Spore_Colony, COLONY, at(0), none()));
    }

    @Test
    void creepColonyCoversNothing() {
        assertFalse(HorizonCombatSimulator.coversThreat(
                UnitType.Zerg_Creep_Colony, COLONY, at(0), at(0)));
    }

    @Test
    void noThreatsMeansNoCoverage() {
        assertFalse(HorizonCombatSimulator.coversThreat(
                UnitType.Zerg_Sunken_Colony, COLONY, none(), none()));
    }

    @Test
    void mixedThreatsCoverOnlyViaTheMatchingWeapon() {
        assertTrue(HorizonCombatSimulator.coversThreat(
                UnitType.Zerg_Sunken_Colony, COLONY, at(0), at(0)));
        assertTrue(HorizonCombatSimulator.coversThreat(
                UnitType.Zerg_Spore_Colony, COLONY, at(0), at(0)));
    }

    @Test
    void withholdingGroundThreatsLeavesSunkenWithNothingToCover() {
        assertFalse(HorizonCombatSimulator.coversThreat(
                UnitType.Zerg_Sunken_Colony, COLONY, none(), at(0)));
    }

    @Test
    void sporeStillCoversAirWhenGroundThreatsAreWithheld() {
        assertTrue(HorizonCombatSimulator.coversThreat(
                UnitType.Zerg_Spore_Colony, COLONY, none(), at(0)));
    }

    @Test
    void colonyWeaponRangesMatchTheGameData() {
        assertTrue(UnitType.Zerg_Sunken_Colony.airWeapon() == bwapi.WeaponType.None);
        assertTrue(UnitType.Zerg_Spore_Colony.groundWeapon() == bwapi.WeaponType.None);
        assertTrue(UnitType.Zerg_Creep_Colony.groundWeapon() == bwapi.WeaponType.None);
        assertTrue(UnitType.Zerg_Creep_Colony.airWeapon() == bwapi.WeaponType.None);
    }

    @Test
    void nonCombatFlyersAreNotTreatedAsAttackers() {
        assertFalse(UnitType.Zerg_Overlord.canAttack());
        assertFalse(UnitType.Protoss_Observer.canAttack());
        assertFalse(UnitType.Protoss_Shuttle.canAttack());
        assertFalse(UnitType.Terran_Dropship.canAttack());
        assertTrue(UnitType.Protoss_Carrier.canAttack());
        assertTrue(UnitType.Zerg_Mutalisk.canAttack());
    }

    @Test
    void attackerJustBeyondRadiusIsAThreatBeyondRadius() {
        assertTrue(HorizonCombatSimulator.isThreatBeyondRadius(UnitType.Protoss_Zealot, 400, 320));
    }

    @Test
    void attackerInsideRadiusIsMeasuredInsteadOfBeyond() {
        assertFalse(HorizonCombatSimulator.isThreatBeyondRadius(UnitType.Protoss_Zealot, 300, 320));
    }

    @Test
    void attackerPastTheNearbyThreatRadiusIsNotBeyondRadius() {
        assertFalse(HorizonCombatSimulator.isThreatBeyondRadius(UnitType.Protoss_Zealot, 600, 320));
    }

    @Test
    void workerJustBeyondRadiusIsNotAThreatBeyondRadius() {
        assertFalse(HorizonCombatSimulator.isThreatBeyondRadius(UnitType.Protoss_Probe, 400, 320));
    }

    @Test
    void nonAttackerJustBeyondRadiusIsNotAThreatBeyondRadius() {
        assertFalse(HorizonCombatSimulator.isThreatBeyondRadius(UnitType.Zerg_Overlord, 400, 320));
    }

    @Test
    void attackerAtTheNearbyThreatRadiusIsAThreatBeyondRadius() {
        assertTrue(HorizonCombatSimulator.isThreatBeyondRadius(UnitType.Terran_Marine, 512, 320));
    }

    @Test
    void attackerAtItsOwnEngagementRadiusIsNotBeyondRadius() {
        assertFalse(HorizonCombatSimulator.isThreatBeyondRadius(UnitType.Protoss_Zealot, 320, 320));
    }

    @Test
    void unmeasuredEnemyNeverEngagesHoweverStrongTheSquad() {
        assertEquals(ADVANCE, HorizonCombatSimulator.selectResult(1000, 0, 0, 0, false, 1.3));
    }

    @Test
    void measuredEnemyBelowThresholdRetreats() {
        assertEquals(RETREAT, HorizonCombatSimulator.selectResult(1.3, 0, 1, 0, false, 1.4));
    }

    @Test
    void airSquadIgnoresGroundOnlyEnemyStrength() {
        assertEquals(ADVANCE, HorizonCombatSimulator.selectResult(0, 5, 100, 0, true, 1.3));
    }

    @Test
    void airSquadRetreatsAgainstMeasuredAntiAir() {
        assertEquals(RETREAT, HorizonCombatSimulator.selectResult(0, 5, 0, 10, true, 1.3));
    }

    private static double zerglingStrength(int zerglings) {
        return zerglings * UnitStrength.totalStrength(UnitType.Zerg_Zergling);
    }

    private static double mutaliskAirStrength(int mutalisks) {
        return mutalisks * UnitStrength.totalStrength(UnitType.Zerg_Mutalisk);
    }

    private static CombatResult mutalisksVersus(int mutalisks, UnitType defence, double engageThreshold) {
        double enemyAntiAir = HorizonCombatSimulator.weightedAntiAirStrength(defence, ALL_SMALL);
        return HorizonCombatSimulator.selectResult(
                0, mutaliskAirStrength(mutalisks), 0, enemyAntiAir, true, engageThreshold);
    }

    @Test
    void oneMutaliskDoesNotEngageASporeColony() {
        assertEquals(RETREAT, mutalisksVersus(1, UnitType.Zerg_Spore_Colony, ZERG_ENGAGE_THRESHOLD));
    }

    @Test
    void oneMutaliskDoesNotEngageAMissileTurret() {
        assertEquals(RETREAT, mutalisksVersus(1, UnitType.Terran_Missile_Turret, TERRAN_ENGAGE_THRESHOLD));
    }

    @Test
    void theSupersededSporeLiteralWouldHaveEngagedWithASingleMutalisk() {
        assertEquals(ENGAGE, HorizonCombatSimulator.selectResult(
                0, mutaliskAirStrength(1), 0, SUPERSEDED_ANTI_AIR_LITERAL, true, ZERG_ENGAGE_THRESHOLD));
    }

    @Test
    void aSporeColonyOutweighsASingleMutalisk() {
        assertTrue(HorizonCombatSimulator.weightedAntiAirStrength(UnitType.Zerg_Spore_Colony, ALL_SMALL)
                > mutaliskAirStrength(1));
    }

    @Test
    void sporeColonyIsNotDiscountedAgainstSmallUnits() {
        assertEquals(UnitStrength.antiAirStrength(UnitType.Zerg_Spore_Colony),
                HorizonCombatSimulator.weightedAntiAirStrength(UnitType.Zerg_Spore_Colony, ALL_SMALL),
                1e-9);
    }

    @Test
    void missileTurretIsDiscountedOnceAndStillOutweighsTheSupersededLiteral() {
        double formula = UnitStrength.formulaStrength(UnitType.Terran_Missile_Turret)[1];
        double weighted = HorizonCombatSimulator.weightedAntiAirStrength(
                UnitType.Terran_Missile_Turret, ALL_SMALL);
        assertEquals(formula * EXPLOSIVE_VERSUS_SMALL, weighted, 1e-9);
        assertTrue(weighted > SUPERSEDED_ANTI_AIR_LITERAL);
    }

    @Test
    void handTunedLiteralsAreNotDiscountedBelowTheirOwnFormulaBasis() {
        for (UnitType type : UnitType.values()) {
            if (!UnitStrength.isHandTuned(type)) continue;
            double[] formula = UnitStrength.formulaStrength(type);
            double formulaGround = formula[0] + formula[2];
            double weightedGround = HorizonCombatSimulator.weightedGroundStrength(type, ALL_SMALL);
            DamageType groundDamage = type.groundWeapon() == bwapi.WeaponType.None
                    ? DamageType.Normal
                    : type.groundWeapon().damageType();
            double weightedFormulaGround = formulaGround
                    * UnitStrength.effectiveness(groundDamage, UnitSizeType.Small);
            assertTrue(weightedGround >= weightedFormulaGround - 1e-9, type.toString());
        }
    }

    @Test
    void sunkenColonyKeepsItsLiteralAboveTheFormulaAfterTheExplosiveDiscount() {
        double weighted = HorizonCombatSimulator.weightedGroundStrength(
                UnitType.Zerg_Sunken_Colony, ALL_SMALL);
        double formulaWeighted = UnitStrength.formulaStrength(UnitType.Zerg_Sunken_Colony)[0]
                * EXPLOSIVE_VERSUS_SMALL;
        assertEquals(3.0, weighted, 1e-9);
        assertTrue(weighted > formulaWeighted);
    }

    @Test
    void normalDamageDefencesAreNeverDiscounted() {
        assertEquals(6.0, HorizonCombatSimulator.weightedGroundStrength(
                UnitType.Protoss_Photon_Cannon, ALL_SMALL), 1e-9);
        assertEquals(6.0, HorizonCombatSimulator.weightedAntiAirStrength(
                UnitType.Protoss_Photon_Cannon, ALL_SMALL), 1e-9);
        assertEquals(12.0, HorizonCombatSimulator.weightedAntiAirStrength(
                UnitType.Terran_Bunker, ALL_SMALL), 1e-9);
    }
}
