package unit.squad.horizon;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdgeOfFirePricingTest {

    private static final UnitType SIEGED = UnitType.Terran_Siege_Tank_Siege_Mode;
    private static final int TANK_API_RANGE = SIEGED.groundWeapon().maxRange();
    private static final int LEARNED_TANK_REACH = 400;

    @Test
    void aSiegedTankIsPricedFromItsEdgeOfFireAgainstAGroundSquad() {
        assertTrue(HorizonCombatSimulator.pricedAtEdgeOfFire(SIEGED, false));
        assertTrue(HorizonCombatSimulator.pricedAtEdgeOfFire(UnitType.Zerg_Lurker, false));
        assertTrue(HorizonCombatSimulator.pricedAtEdgeOfFire(UnitType.Terran_Bunker, false));
        assertTrue(HorizonCombatSimulator.pricedAtEdgeOfFire(UnitType.Protoss_Photon_Cannon, false));
    }

    @Test
    void mobileUnitsAndAirOnlyDefenceKeepTheFixedRadius() {
        assertFalse(HorizonCombatSimulator.pricedAtEdgeOfFire(UnitType.Terran_Siege_Tank_Tank_Mode, false));
        assertFalse(HorizonCombatSimulator.pricedAtEdgeOfFire(UnitType.Terran_Marine, false));
        assertFalse(HorizonCombatSimulator.pricedAtEdgeOfFire(UnitType.Terran_Missile_Turret, false));
        assertFalse(HorizonCombatSimulator.pricedAtEdgeOfFire(UnitType.Terran_Barracks, false));
    }

    @Test
    void anAirSquadKeepsTheFixedRadiusForASiegedTank() {
        assertFalse(HorizonCombatSimulator.pricedAtEdgeOfFire(SIEGED, true));
    }

    @Test
    void theEngagementRadiusIsTheLearnedReachPlusTheFalloff() {
        int reach = HorizonCombatSimulator.positionalReach(SIEGED, LEARNED_TANK_REACH);

        assertEquals(LEARNED_TANK_REACH, reach);
        assertEquals(LEARNED_TANK_REACH + HorizonCombatSimulator.FALLOFF_EXTENT,
                HorizonCombatSimulator.edgeOfFireRadius(reach));
    }

    @Test
    void aReachNotYetLearnedIsTheBaseRange() {
        assertEquals(TANK_API_RANGE, HorizonCombatSimulator.positionalReach(SIEGED, 0));
    }

    @Test
    void theReachIsBounded() {
        assertEquals(HorizonCombatSimulator.MAX_POSITIONAL_REACH, HorizonCombatSimulator.positionalReach(SIEGED, 584));
    }

    @Test
    void aTankJustPastTheOldRadiusWeighsInFull() {
        double distance = 600;

        assertEquals(0.0, HorizonCombatSimulator.distanceWeight(distance));
        assertEquals(1.0, HorizonCombatSimulator.enemyDistanceWeight(SIEGED, distance, true, LEARNED_TANK_REACH));
    }

    @Test
    void theWeightFallsOffPastTheEdgeOfFire() {
        double full = HorizonCombatSimulator.enemyDistanceWeight(SIEGED, LEARNED_TANK_REACH + 256, true,
                LEARNED_TANK_REACH);
        double partial = HorizonCombatSimulator.enemyDistanceWeight(SIEGED, LEARNED_TANK_REACH + 384, true,
                LEARNED_TANK_REACH);

        assertEquals(1.0, full);
        assertTrue(partial > 0 && partial < 1.0);
        assertEquals(0.0, HorizonCombatSimulator.enemyDistanceWeight(SIEGED,
                HorizonCombatSimulator.edgeOfFireRadius(LEARNED_TANK_REACH) + 1, true, LEARNED_TANK_REACH));
    }

    @Test
    void insideItsReachAPositionalEnemyWeighsInFull() {
        assertEquals(1.0, HorizonCombatSimulator.enemyDistanceWeight(SIEGED, 100, true, LEARNED_TANK_REACH));
    }

    @Test
    void aBuildingWithinTheOldRadiusStillWeighsInFull() {
        int bunkerReach = HorizonCombatSimulator.positionalReach(UnitType.Terran_Bunker, 0);

        assertEquals(1.0, HorizonCombatSimulator.enemyDistanceWeight(UnitType.Terran_Bunker, 320, true, bunkerReach));
    }

    @Test
    void unitsNotPricedAtTheirEdgeKeepTheirOldWeights() {
        assertEquals(1.0, HorizonCombatSimulator.enemyDistanceWeight(UnitType.Terran_Barracks, 300, false, 0));
        assertEquals(HorizonCombatSimulator.distanceWeight(400),
                HorizonCombatSimulator.enemyDistanceWeight(UnitType.Terran_Marine, 400, false, 0));
    }

    @Test
    void aTankPricedAcrossItsWholeRadiusIsNeverFlaggedBeyondIt() {
        double radius = HorizonCombatSimulator.edgeOfFireRadius(LEARNED_TANK_REACH);

        assertFalse(HorizonCombatSimulator.isThreatBeyondRadius(SIEGED, radius + 1, radius));
        assertFalse(HorizonCombatSimulator.isThreatBeyondRadius(SIEGED, 700, radius));
    }
}
