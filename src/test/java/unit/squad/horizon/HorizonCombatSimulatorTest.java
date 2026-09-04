package unit.squad.horizon;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.CombatSimulator.CombatResult.ADVANCE;
import static unit.squad.CombatSimulator.CombatResult.ENGAGE;
import static unit.squad.CombatSimulator.CombatResult.RETREAT;

class HorizonCombatSimulatorTest {

    private static final Position COLONY = new Position(1000, 1000);
    private static final int SUNKEN_RANGE = UnitType.Zerg_Sunken_Colony.groundWeapon().maxRange();
    private static final int SPORE_RANGE = UnitType.Zerg_Spore_Colony.airWeapon().maxRange();

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
    void noStrengthOnEitherSideRetreats() {
        assertEquals(RETREAT, HorizonCombatSimulator.selectResult(0, 0, 0, 0, false, 1.3));
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
    void attackerJustBeyondEvaluationRadiusIsAThreatBeyondRadius() {
        assertTrue(HorizonCombatSimulator.isThreatBeyondRadius(UnitType.Protoss_Zealot, 700, 640));
    }

    @Test
    void attackerInsideEvaluationRadiusIsMeasuredInsteadOfBeyond() {
        assertFalse(HorizonCombatSimulator.isThreatBeyondRadius(UnitType.Protoss_Zealot, 600, 640));
    }

    @Test
    void attackerPastTheNearbyThreatRadiusIsNotBeyondRadius() {
        assertFalse(HorizonCombatSimulator.isThreatBeyondRadius(UnitType.Protoss_Zealot, 900, 640));
    }

    @Test
    void workerJustBeyondRadiusIsNotAThreatBeyondRadius() {
        assertFalse(HorizonCombatSimulator.isThreatBeyondRadius(UnitType.Protoss_Probe, 700, 640));
    }

    @Test
    void nonAttackerJustBeyondRadiusIsNotAThreatBeyondRadius() {
        assertFalse(HorizonCombatSimulator.isThreatBeyondRadius(UnitType.Zerg_Overlord, 700, 640));
    }

    @Test
    void attackerAtTheNearbyThreatRadiusIsAThreatBeyondRadius() {
        assertTrue(HorizonCombatSimulator.isThreatBeyondRadius(UnitType.Terran_Marine, 832, 640));
    }

    @Test
    void attackerAtTheEvaluationRadiusIsNotBeyondRadius() {
        assertFalse(HorizonCombatSimulator.isThreatBeyondRadius(UnitType.Protoss_Zealot, 640, 640));
    }

    @Test
    void mobileEnemiesAreEvaluatedThroughTheOuterBand() {
        assertEquals(640, HorizonCombatSimulator.enemyEvaluationRadius(UnitType.Protoss_Zealot, 320));
    }

    @Test
    void positionalEnemiesKeepTheirRangeBasedEvaluationRadius() {
        assertEquals(448, HorizonCombatSimulator.enemyEvaluationRadius(
                UnitType.Terran_Siege_Tank_Siege_Mode, 448));
    }

    @Test
    void enemyInsideTheOuterBandContributesByTimeToContact() {
        double weight = HorizonCombatSimulator.enemyDistanceWeight(UnitType.Protoss_Zealot, 600, 320);

        assertTrue(weight > 0);
        assertTrue(weight < HorizonCombatSimulator.enemyDistanceWeight(UnitType.Protoss_Zealot, 320, 320));
    }

    @Test
    void fasterEnemyAtTheSameDistanceContributesMore() {
        double zerglingWeight = HorizonCombatSimulator.enemyDistanceWeight(UnitType.Zerg_Zergling, 600, 320);
        double zealotWeight = HorizonCombatSimulator.enemyDistanceWeight(UnitType.Protoss_Zealot, 600, 320);

        assertTrue(UnitType.Zerg_Zergling.topSpeed() > UnitType.Protoss_Zealot.topSpeed());
        assertTrue(zerglingWeight > zealotWeight);
    }

    @Test
    void enemyBeyondTheOuterBandContributesNothing() {
        assertEquals(0, HorizonCombatSimulator.enemyDistanceWeight(UnitType.Protoss_Zealot, 641, 320));
    }

    @Test
    void snapshotAttributesEnemyStrengthToEachDistanceBand() {
        HorizonCombatSimulator.DebugSnapshot snapshot = new HorizonCombatSimulator.DebugSnapshot();

        snapshot.addEnemyStrength(320, 1);
        snapshot.addEnemyStrength(480, 2);
        snapshot.addEnemyStrength(640, 3);
        snapshot.addExcludedEnemyStrength(200, 4);
        snapshot.addExcludedEnemyStrength(400, 5);
        snapshot.addExcludedEnemyStrength(600, 6);
        snapshot.addExcludedEnemyStrength(800, 7);

        assertEquals(1, snapshot.getEnemyStrengthInner());
        assertEquals(2, snapshot.getEnemyStrengthMiddle());
        assertEquals(3, snapshot.getEnemyStrengthOuter());
        assertEquals(4, snapshot.getExcludedEnemyStrengthInner());
        assertEquals(5, snapshot.getExcludedEnemyStrengthMiddle());
        assertEquals(6, snapshot.getExcludedEnemyStrengthOuter());
        assertEquals(7, snapshot.getExcludedEnemyStrengthBeyond());
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
}
