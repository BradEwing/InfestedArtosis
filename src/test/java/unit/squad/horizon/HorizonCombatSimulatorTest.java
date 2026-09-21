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
    private static final double SUPERSEDED_BUNKER_LITERAL = 224.4994432064365;
    private static final int BUNKER_BUST_ZERGLINGS = 27;
    private static final int BUNKER_BUST_MARINES = 8;
    private static final double ZERGLING_GROUND_BEFORE_DURABILITY = 1.8644709320919568;
    private static final double MARINE_GROUND_BEFORE_DURABILITY = 1.5484804043631566;
    private static final int NEAR_THRESHOLD_ZERGLINGS = 8;
    private static final int NEAR_THRESHOLD_MARINES = 6;
    private static final int NEAR_THRESHOLD_MEDICS = 2;

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
    void sunkenColonyIsDiscountedOnceForExplosiveDamage() {
        double formula = UnitStrength.formulaStrength(UnitType.Zerg_Sunken_Colony)[0];
        assertEquals(formula * EXPLOSIVE_VERSUS_SMALL, HorizonCombatSimulator.weightedGroundStrength(
                UnitType.Zerg_Sunken_Colony, ALL_SMALL), 1e-9);
    }

    @Test
    void normalDamageDefencesAreNeverDiscounted() {
        double cannon = UnitStrength.formulaStrength(UnitType.Protoss_Photon_Cannon)[0];
        double bunker = UnitStrength.formulaStrength(UnitType.Terran_Bunker)[1];
        assertEquals(cannon, HorizonCombatSimulator.weightedGroundStrength(
                UnitType.Protoss_Photon_Cannon, ALL_SMALL), 1e-9);
        assertEquals(cannon, HorizonCombatSimulator.weightedAntiAirStrength(
                UnitType.Protoss_Photon_Cannon, ALL_SMALL), 1e-9);
        assertEquals(bunker, HorizonCombatSimulator.weightedAntiAirStrength(
                UnitType.Terran_Bunker, ALL_SMALL), 1e-9);
    }

    @Test
    void zerglingsThatOutnumberAFullBunkerAndItsEscortEngage() {
        double zerglings = BUNKER_BUST_ZERGLINGS * UnitStrength.groundToGround(UnitType.Zerg_Zergling);
        double escort = BUNKER_BUST_MARINES * UnitStrength.groundToGround(UnitType.Terran_Marine);
        double bunker = HorizonCombatSimulator.weightedGroundStrength(UnitType.Terran_Bunker, ALL_SMALL);
        assertEquals(RETREAT, HorizonCombatSimulator.selectResult(zerglings, 0,
                SUPERSEDED_BUNKER_LITERAL + escort, 0, false, TERRAN_ENGAGE_THRESHOLD));
        assertEquals(ENGAGE, HorizonCombatSimulator.selectResult(zerglings, 0,
                bunker + escort, 0, false, TERRAN_ENGAGE_THRESHOLD));
    }

    @Test
    void aHealthFractionIsRootedToMatchTheDurabilityTerm() {
        assertEquals(1.0, HorizonCombatSimulator.hpWeighting(35, 0, 35, 0), 1e-9);
        assertEquals(Math.sqrt(0.5), HorizonCombatSimulator.hpWeighting(20, 0, 40, 0), 1e-9);
        assertEquals(0.0, HorizonCombatSimulator.hpWeighting(0, 0, 40, 0), 1e-9);
    }

    @Test
    void anIsolatedMedicContributesNothing() {
        assertEquals(0.0, HorizonCombatSimulator.medicSupportBonus(3, 0), 1e-9);
        assertEquals(0.0, bioSample(0, 3).groundTotal(), 1e-9);
        assertEquals(0.0, bioSample(0, 3).antiAirTotal(), 1e-9);
    }

    @Test
    void marinesWithoutAMedicCarryNoSupportTerm() {
        assertEquals(0.0, HorizonCombatSimulator.medicSupportBonus(0, 6), 1e-9);
        assertEquals(6 * marineGround(), bioSample(6, 0).groundTotal(), 1e-9);
    }

    @Test
    void aMedicMakesTheSameMarineSampleMeasureStronger() {
        assertTrue(bioSample(6, 1).groundTotal() > bioSample(6, 0).groundTotal());
        assertTrue(bioSample(6, 1).antiAirTotal() > bioSample(6, 0).antiAirTotal());
    }

    @Test
    void everyExtraMedicAddsLessThanTheOneBeforeIt() {
        double none = bioSample(6, 0).groundTotal();
        double one = bioSample(6, 1).groundTotal();
        double two = bioSample(6, 2).groundTotal();
        double three = bioSample(6, 3).groundTotal();
        assertTrue(one > none);
        assertTrue(two > one);
        assertTrue(three > two);
        assertTrue(two - one < one - none);
        assertTrue(three - two < two - one);
    }

    @Test
    void theMedicSupportTermIsCapped() {
        assertTrue(bioSample(6, 200).groundTotal() < 1.4 * bioSample(6, 0).groundTotal());
    }

    @Test
    void removingTheSupportedUnitsReturnsTheSupportTermToZero() {
        assertTrue(bioSample(6, 2).groundTotal() > bioSample(6, 0).groundTotal());
        assertEquals(0.0, bioSample(0, 2).groundTotal(), 1e-9);
    }

    @Test
    void aSupportedMedicIsNoLongerUnscoredSupply() {
        assertEquals(0, bioSample(6, 2).unscoredSupply());
        assertEquals(2 * UnitType.Terran_Medic.supplyRequired(), bioSample(0, 2).unscoredSupply());
    }

    @Test
    void theSnapshotCarriesTheMedicSupportOnTheMedicEntries() {
        HorizonCombatSimulator.DebugSnapshot withMedics = snapshotFor(NEAR_THRESHOLD_MARINES, NEAR_THRESHOLD_MEDICS);
        HorizonCombatSimulator.DebugSnapshot withoutMedics = snapshotFor(NEAR_THRESHOLD_MARINES, 0);

        assertTrue(withMedics.getEnemyTotal() > withoutMedics.getEnemyTotal());
        assertEquals(0, withMedics.getEnemyUnscoredSupply());
        for (HorizonCombatSimulator.UnitDebugEntry entry : withMedics.getEnemyUnits()) {
            assertTrue(entry.getStrength() > 0, entry.getType().toString());
        }
        assertEquals(withMedics.getEnemyTotal(), sumOfEntries(withMedics), 1e-9);
    }

    @Test
    void aSnapshotOfMedicsAloneStillReportsThemUnscored() {
        HorizonCombatSimulator.DebugSnapshot snapshot = snapshotFor(0, 2);

        assertEquals(0.0, snapshot.getEnemyTotal(), 1e-9);
        assertEquals(2 * UnitType.Terran_Medic.supplyRequired(), snapshot.getEnemyUnscoredSupply());
    }

    private static HorizonCombatSimulator.DebugSnapshot snapshotFor(int marines, int medics) {
        HorizonCombatSimulator.EnemySample sample = bioSample(marines, medics);
        HorizonCombatSimulator.DebugSnapshot snapshot = new HorizonCombatSimulator.DebugSnapshot();
        for (int i = 0; i < marines; i++) {
            snapshot.getEnemyUnits().add(new HorizonCombatSimulator.UnitDebugEntry(
                    COLONY, UnitType.Terran_Marine, marineGround(), false, false));
        }
        for (int i = 0; i < medics; i++) {
            snapshot.getEnemyUnits().add(enemyEntry(UnitType.Terran_Medic));
        }
        HorizonCombatSimulator.creditMedicSupport(snapshot, sample, false);
        snapshot.setEnemyUnscoredSupply(sample.unscoredSupply());
        snapshot.setEnemyTotal(sample.groundTotal());
        return snapshot;
    }

    private static double sumOfEntries(HorizonCombatSimulator.DebugSnapshot snapshot) {
        double total = 0;
        for (HorizonCombatSimulator.UnitDebugEntry entry : snapshot.getEnemyUnits()) {
            total += entry.getStrength();
        }
        return total;
    }

    @Test
    void theEnemyCompositionNamesEveryTypeSampled() {
        HorizonCombatSimulator.DebugSnapshot snapshot = new HorizonCombatSimulator.DebugSnapshot();
        snapshot.getEnemyUnits().add(enemyEntry(UnitType.Terran_Medic));
        snapshot.getEnemyUnits().add(enemyEntry(UnitType.Terran_Marine));
        snapshot.getEnemyUnits().add(enemyEntry(UnitType.Terran_Marine));

        assertEquals("Terran_Marine:2;Terran_Medic:1", HorizonCombatSimulator.enemyComposition(snapshot));
    }

    @Test
    void anEmptySampleHasNoComposition() {
        assertEquals("", HorizonCombatSimulator.enemyComposition(new HorizonCombatSimulator.DebugSnapshot()));
    }

    @Test
    void theNearThresholdMarineMedicScenarioMainEngagedNowRetreats() {
        double mainFriendly = NEAR_THRESHOLD_ZERGLINGS * ZERGLING_GROUND_BEFORE_DURABILITY;
        double mainEnemy = NEAR_THRESHOLD_MARINES * MARINE_GROUND_BEFORE_DURABILITY;
        assertEquals(1.6054, mainFriendly / mainEnemy, 1e-4);
        assertEquals(ENGAGE, HorizonCombatSimulator.selectResult(
                mainFriendly, 0, mainEnemy, 0, false, TERRAN_ENGAGE_THRESHOLD));

        HorizonCombatSimulator.EnemySample sample = bioSample(NEAR_THRESHOLD_MARINES, NEAR_THRESHOLD_MEDICS);
        assertEquals(1.2514, zerglingStrength(NEAR_THRESHOLD_ZERGLINGS) / sample.groundTotal(), 1e-4);
        assertEquals(RETREAT, HorizonCombatSimulator.selectResult(
                zerglingStrength(NEAR_THRESHOLD_ZERGLINGS), 0, sample.groundTotal(), sample.antiAirTotal(),
                false, TERRAN_ENGAGE_THRESHOLD));
    }

    @Test
    void theSameScenarioWithoutTheMedicsStillEngages() {
        HorizonCombatSimulator.EnemySample sample = bioSample(NEAR_THRESHOLD_MARINES, 0);
        assertEquals(1.5017, zerglingStrength(NEAR_THRESHOLD_ZERGLINGS) / sample.groundTotal(), 1e-4);
        assertEquals(ENGAGE, HorizonCombatSimulator.selectResult(
                zerglingStrength(NEAR_THRESHOLD_ZERGLINGS), 0, sample.groundTotal(), sample.antiAirTotal(),
                false, TERRAN_ENGAGE_THRESHOLD));
    }

    private static double marineGround() {
        return HorizonCombatSimulator.weightedGroundStrength(UnitType.Terran_Marine, ALL_SMALL);
    }

    private static HorizonCombatSimulator.EnemySample bioSample(int marines, int medics) {
        HorizonCombatSimulator.EnemySample sample = new HorizonCombatSimulator.EnemySample();
        double ground = marineGround();
        double antiAir = HorizonCombatSimulator.weightedAntiAirStrength(UnitType.Terran_Marine, ALL_SMALL);
        for (int i = 0; i < marines; i++) {
            sample.add(UnitType.Terran_Marine, ground, antiAir);
        }
        for (int i = 0; i < medics; i++) {
            sample.add(UnitType.Terran_Medic, 0, 0);
        }
        return sample;
    }

    private static HorizonCombatSimulator.UnitDebugEntry enemyEntry(UnitType type) {
        return new HorizonCombatSimulator.UnitDebugEntry(COLONY, type, 0, false, false);
    }
}
