package unit.squad.horizon;

import bwapi.Position;
import bwapi.Race;
import bwapi.UnitSizeType;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.CombatSimulator.CombatResult.ENGAGE;
import static unit.squad.CombatSimulator.CombatResult.RETREAT;

class AirTurretEdgeOfFireTest {

    private static final UnitType TURRET = UnitType.Terran_Missile_Turret;
    private static final UnitType MARINE = UnitType.Terran_Marine;
    private static final UnitType MUTALISK = UnitType.Zerg_Mutalisk;
    private static final int TURRET_AIR_RANGE = TURRET.airWeapon().maxRange();
    private static final Map<UnitSizeType, Double> MUTALISKS = Collections.singletonMap(UnitSizeType.Small, 1.0);
    private static final double TERRAN_ENGAGE_THRESHOLD = HorizonCombatSimulator.engageThreshold(Race.Terran);
    private static final Position CENTRE = new Position(1000, 1000);
    private static final int FLOCK = 4;
    private static final int MARINES = 2;
    private static final double MARINE_DISTANCE = 200;
    private static final double PULLED_BACK = 400;
    private static final UnitType[] OTHER_AIR_SQUAD_ENEMIES = {MARINE, UnitType.Terran_Goliath,
        UnitType.Terran_Bunker, UnitType.Zerg_Spore_Colony, UnitType.Protoss_Photon_Cannon,
        UnitType.Terran_Siege_Tank_Siege_Mode};
    private static final UnitType[] POSITIONAL_ENEMIES = {UnitType.Terran_Siege_Tank_Siege_Mode,
        UnitType.Zerg_Lurker, UnitType.Protoss_Photon_Cannon, UnitType.Zerg_Sunken_Colony};

    private static double priced(UnitType type, double distance, boolean enabled, int learnedGroundReach) {
        boolean airEdge = HorizonCombatSimulator.pricedAtAirEdgeOfFire(type, true, enabled);
        boolean edgeOfFire = type != UnitType.Terran_Bunker
                && (airEdge || HorizonCombatSimulator.pricedAtEdgeOfFire(type, true));
        int reach = edgeOfFire ? HorizonCombatSimulator.edgeReach(type, airEdge, learnedGroundReach) : 0;
        double radius = edgeOfFire
                ? HorizonCombatSimulator.edgeOfFireRadius(reach)
                : HorizonCombatSimulator.engagementRadius(type);
        if (distance > radius) return 0;
        return HorizonCombatSimulator.enemyDistanceWeight(type, distance, edgeOfFire, reach);
    }

    private static HorizonCombatSimulator.FriendlyForce flock() {
        HorizonCombatSimulator.FriendlyForce force = new HorizonCombatSimulator.FriendlyForce();
        for (int i = 0; i < FLOCK; i++) {
            force.add(MUTALISK, CENTRE, 1.0, false);
        }
        return force;
    }

    private static void addEnemy(HorizonCombatSimulator.EnemySample sample, UnitType type, double weight) {
        sample.add(type, HorizonCombatSimulator.weightedGroundStrength(type, MUTALISKS) * weight,
                HorizonCombatSimulator.weightedAntiAirStrength(type, MUTALISKS) * weight);
    }

    private static unit.squad.CombatSimulator.CombatResult verdict(double turretDistance, boolean enabled) {
        HorizonCombatSimulator.EnemySample sample = new HorizonCombatSimulator.EnemySample();
        for (int i = 0; i < MARINES; i++) {
            addEnemy(sample, MARINE, priced(MARINE, MARINE_DISTANCE, enabled, 0));
        }
        double turretWeight = priced(TURRET, turretDistance, enabled, 0);
        if (turretWeight > 0) {
            addEnemy(sample, TURRET, turretWeight);
        }
        HorizonCombatSimulator.PricedEngagement priced = HorizonCombatSimulator.price(flock(), sample);
        return HorizonCombatSimulator.selectResult(priced.getFriendlyGround(), priced.getFriendlyAir(),
                priced.getEnemyGround(), priced.getEnemyAntiAir(), priced.getEnemyEngaged(), true,
                TERRAN_ENGAGE_THRESHOLD);
    }

    @Test
    void aTurretIsPricedFromItsEdgeOfAirFireForAnAirSquad() {
        assertTrue(HorizonCombatSimulator.pricedAtAirEdgeOfFire(TURRET, true, true));
        assertEquals(TURRET_AIR_RANGE, HorizonCombatSimulator.edgeReach(TURRET, true, 0));
    }

    @Test
    void aFlockPulledBackPastTheFixedRadiusStillPricesTheTurretInFull() {
        assertTrue(PULLED_BACK > HorizonCombatSimulator.engagementRadius(TURRET));

        assertEquals(1.0, priced(TURRET, PULLED_BACK, true, 0));
        assertEquals(0.0, priced(TURRET, PULLED_BACK, false, 0));
    }

    @Test
    void theTurretFallsOutOfTheSampleAtTheEdgeOfFireRadius() {
        double radius = HorizonCombatSimulator.edgeOfFireRadius(TURRET_AIR_RANGE);

        assertTrue(priced(TURRET, radius, true, 0) > 0);
        assertEquals(0.0, priced(TURRET, radius + 1, true, 0));
    }

    @Test
    void aFlockThatPulledBackOffATurretLineNoLongerReadsEngageOnTheUnitsUnderIt() {
        assertEquals(ENGAGE, verdict(PULLED_BACK, false));
        assertEquals(RETREAT, verdict(PULLED_BACK, true));
    }

    @Test
    void aTurretInsideTheFixedRadiusIsReadTheSameEitherWay() {
        assertEquals(RETREAT, verdict(250, false));
        assertEquals(RETREAT, verdict(250, true));
    }

    @Test
    void withNoTurretNearTheVerdictIsUnchanged() {
        double farAway = HorizonCombatSimulator.edgeOfFireRadius(TURRET_AIR_RANGE) + 100;

        assertEquals(ENGAGE, verdict(farAway, true));
        assertEquals(verdict(farAway, false), verdict(farAway, true));
    }

    @Test
    void otherEnemiesKeepTheirAirSquadPricing() {
        for (UnitType type : OTHER_AIR_SQUAD_ENEMIES) {
            assertFalse(HorizonCombatSimulator.pricedAtAirEdgeOfFire(type, true, true));
            for (double distance : new double[]{100, 300, 400, 700}) {
                assertEquals(priced(type, distance, false, 0), priced(type, distance, true, 0));
            }
        }
    }

    @Test
    void aGroundSquadIsUnaffected() {
        assertFalse(HorizonCombatSimulator.pricedAtAirEdgeOfFire(TURRET, false, true));
        assertFalse(HorizonCombatSimulator.pricedAtEdgeOfFire(TURRET, false));
    }

    @Test
    void withTheSwitchOffATurretKeepsTheFixedRadius() {
        assertFalse(HorizonCombatSimulator.pricedAtAirEdgeOfFire(TURRET, true, false));
        for (double distance : new double[]{100, 300, 320, 321, 400, 700}) {
            boolean inside = distance <= HorizonCombatSimulator.engagementRadius(TURRET);
            assertEquals(inside ? 1.0 : 0.0, priced(TURRET, distance, false, 0));
        }
    }

    @Test
    void theReachOfEveryOtherEdgeOfFireEnemyIsItsPositionalReach() {
        for (UnitType type : POSITIONAL_ENEMIES) {
            for (int learned : new int[]{0, 200, 400, 600}) {
                assertEquals(HorizonCombatSimulator.positionalReach(type, learned),
                        HorizonCombatSimulator.edgeReach(type, false, learned));
            }
        }
    }
}
