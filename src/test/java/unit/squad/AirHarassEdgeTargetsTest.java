package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.AirHarassTargeting.Kind;
import static unit.squad.AirHarassTargeting.Tier;

class AirHarassEdgeTargetsTest {

    private static final int NOW = 12000;
    private static final Position MUTA_AT = new Position(2000, 2000);
    private static final Position SEEK = new Position(2600, 2000);

    private static AirHarassTargeting.Muta muta() {
        return new AirHarassTargeting.Muta(1, MUTA_AT);
    }

    private static Position east(int pixels) {
        return new Position(MUTA_AT.getX() + pixels, MUTA_AT.getY());
    }

    private static AirHarassTargeting.Contact contact(int id, UnitType type, Position position) {
        return new AirHarassTargeting.Contact(id, type, position, type.maxHitPoints() + type.maxShields(), 1.0);
    }

    private static AirHarassTargeting.AirThreat threat(int id, UnitType type, Position position) {
        return AirHarassTargeting.AirThreat.of(id, type, position,
                AirHarassTargeting.airRange(type, weapon -> weapon.maxRange()));
    }

    private static AirHarassTargeting.Situation.SituationBuilder situation(int flock) {
        return AirHarassTargeting.Situation.builder()
                .flockSize(flock)
                .seekPoint(SEEK)
                .now(NOW);
    }

    private static List<AirHarassTargeting.AirThreat> threats(AirHarassTargeting.AirThreat... threats) {
        return Arrays.asList(threats);
    }

    @Test
    void aLoneTurretIsAnEdgeTurretForAFlockThatKillsItWithinTheDamageBudget() {
        AirHarassTargeting.AirThreat turret = threat(7, UnitType.Terran_Missile_Turret, east(600));
        double budget = AirHarassTargeting.EDGE_TURRET_DAMAGE_BUDGET * UnitType.Zerg_Mutalisk.maxHitPoints();

        assertTrue(AirHarassTargeting.turretDamageBeforeKill(turret, 5) > budget);
        assertTrue(AirHarassTargeting.turretDamageBeforeKill(turret, 6) <= budget);
        assertTrue(AirHarassTargeting.edgeTurrets(threats(turret), 5).isEmpty());
        assertTrue(AirHarassTargeting.edgeTurrets(threats(turret), 6).contains(7));
        assertTrue(AirHarassTargeting.edgeTurrets(threats(turret), 12).contains(7));
    }

    @Test
    void theDamageATurretDealsFallsAsTheFlockGrowsAndIsUnboundedForNone() {
        AirHarassTargeting.AirThreat turret = threat(7, UnitType.Terran_Missile_Turret, east(600));

        assertTrue(AirHarassTargeting.turretDamageBeforeKill(turret, 10)
                < AirHarassTargeting.turretDamageBeforeKill(turret, 6));
        assertEquals(Double.POSITIVE_INFINITY, AirHarassTargeting.turretDamageBeforeKill(turret, 0));
        assertTrue(AirHarassTargeting.edgeTurrets(threats(turret), 0).isEmpty());
    }

    @Test
    void aTurretWithOtherAntiAirCoveringItIsNotAnEdgeTurret() {
        AirHarassTargeting.AirThreat turret = threat(7, UnitType.Terran_Missile_Turret, east(600));
        AirHarassTargeting.AirThreat goliath = threat(8, UnitType.Terran_Goliath, east(700));
        AirHarassTargeting.AirThreat second = threat(9, UnitType.Terran_Missile_Turret, east(900));

        assertTrue(AirHarassTargeting.edgeTurrets(threats(turret, goliath), 12).isEmpty());
        assertTrue(AirHarassTargeting.edgeTurrets(threats(turret, second), 12).isEmpty());
    }

    @Test
    void aTurretFarFromOtherAntiAirIsStillAnEdgeTurret() {
        AirHarassTargeting.AirThreat turret = threat(7, UnitType.Terran_Missile_Turret, east(600));
        AirHarassTargeting.AirThreat goliath = threat(8, UnitType.Terran_Goliath, east(2400));

        assertEquals(Collections.singleton(7), AirHarassTargeting.edgeTurrets(threats(turret, goliath), 12));
    }

    @Test
    void onlyAMissileTurretCanBeAnEdgeTurret() {
        AirHarassTargeting.AirThreat cannon = threat(7, UnitType.Protoss_Photon_Cannon, east(600));
        AirHarassTargeting.AirThreat goliath = threat(8, UnitType.Terran_Goliath, east(2400));

        assertTrue(AirHarassTargeting.edgeTurrets(threats(cannon, goliath), 12).isEmpty());
    }

    @Test
    void theFlockPricesEveryThreatButItsEdgeTurrets() {
        AirHarassTargeting.AirThreat turret = threat(7, UnitType.Terran_Missile_Turret, east(600));
        AirHarassTargeting.AirThreat goliath = threat(8, UnitType.Terran_Goliath, east(2400));
        List<AirHarassTargeting.AirThreat> all = threats(turret, goliath);
        List<AirHarassTargeting.AirThreat> priced = AirHarassTargeting.priced(all, Collections.singleton(7));

        assertEquals(Collections.singletonList(goliath), priced);
        assertEquals(all, AirHarassTargeting.priced(all, Collections.emptySet()));
        assertEquals(0, AirHarassTargeting.defenseAt(priced, east(560), 0), 0.0);
        assertTrue(AirHarassTargeting.defenseAt(all, east(560), 0) > 0);
    }

    @Test
    void aSituationTakesAnEdgeTurretInTheIsolatedAntiAirTierOnABaseHarass() {
        AirHarassTargeting.Contact turret = contact(7, UnitType.Terran_Missile_Turret, east(100));

        assertNull(AirHarassTargeting.tier(turret, situation(6).build()));
        assertEquals(Tier.EDGE_TURRET, AirHarassTargeting.tier(turret,
                situation(6).edgeTurretIds(Collections.singleton(7)).build()));
        assertNull(AirHarassTargeting.tier(turret, situation(6).edgeTurretIds(Collections.singleton(8)).build()));
    }

    @Test
    void aMutaAttacksAnEdgeTurretOnABaseHarassThroughItsOwnZone() {
        AirHarassTargeting.Contact turret = contact(7, UnitType.Terran_Missile_Turret, east(100));
        AirHarassTargeting.AirThreat zone = threat(7, UnitType.Terran_Missile_Turret, east(100));

        AirHarassTargeting.Decision decision = AirHarassTargeting.choose(muta(), situation(6)
                .contacts(Collections.singletonList(turret))
                .avoided(Collections.singletonList(zone))
                .edgeTurretIds(Collections.singleton(7))
                .build(), new AirHarassTargeting.MutaMemory());

        assertEquals(Kind.ATTACK, decision.getKind());
        assertEquals(7, decision.getTargetId());
        assertEquals(Tier.EDGE_TURRET, decision.getTier());
    }

    @Test
    void anEdgeTurretOutranksAWorkerInTheTargetArea() {
        AirHarassTargeting.Contact worker = contact(1, UnitType.Terran_SCV, east(40));
        AirHarassTargeting.Contact turret = contact(7, UnitType.Terran_Missile_Turret, east(200));
        AirHarassTargeting.Situation situation = situation(6)
                .contacts(Arrays.asList(worker, turret))
                .edgeTurretIds(Collections.singleton(7))
                .build();

        assertEquals(7, AirHarassTargeting.bestTarget(muta(), situation, -1).getId());
        assertEquals(7, AirHarassTargeting.bestTarget(muta(), situation, 1).getId());
        assertEquals(1, AirHarassTargeting.bestTarget(muta(), situation(6)
                .contacts(Arrays.asList(worker, turret)).build(), -1).getId());
    }

    @Test
    void aWorkerStepsInOnlyAsTheEdgeTurretDies() {
        AirHarassTargeting.Contact worker = contact(1, UnitType.Terran_SCV, east(40));

        assertEquals(1, AirHarassTargeting.bestTarget(muta(), situation(6)
                .contacts(Collections.singletonList(worker)).edgeTurretIds(Collections.singleton(7)).build(),
                7).getId());
    }

    @Test
    void aSwitchToAWorkerOrAnEdgeTurretIsAValueSwitchAndASwapBetweenEqualsIsNot() {
        assertTrue(AirHarassTargeting.isValueSwitch(UnitType.Terran_Barracks, UnitType.Terran_SCV, false));
        assertTrue(AirHarassTargeting.isValueSwitch(UnitType.Terran_SCV, UnitType.Terran_Missile_Turret, true));
        assertTrue(AirHarassTargeting.isValueSwitch(UnitType.Terran_Barracks, UnitType.Terran_Missile_Turret, true));
        assertEquals(false, AirHarassTargeting.isValueSwitch(UnitType.Terran_SCV, UnitType.Terran_SCV, false));
        assertEquals(false, AirHarassTargeting.isValueSwitch(UnitType.Terran_SCV, UnitType.Terran_Barracks, false));
        assertEquals(false, AirHarassTargeting.isValueSwitch(UnitType.Terran_Missile_Turret,
                UnitType.Terran_Missile_Turret, true));
    }

    @Test
    void aMutaStillSkirtsAnAvoidedTurretThatIsNotAnEdgeTurret() {
        AirHarassTargeting.Contact turret = contact(7, UnitType.Terran_Missile_Turret, east(100));
        AirHarassTargeting.AirThreat zone = threat(7, UnitType.Terran_Missile_Turret, east(100));

        AirHarassTargeting.Decision decision = AirHarassTargeting.choose(muta(), situation(6)
                .contacts(Collections.singletonList(turret))
                .avoided(Collections.singletonList(zone))
                .build(), new AirHarassTargeting.MutaMemory());

        assertNotEquals(Kind.ATTACK, decision.getKind());
    }

    @Test
    void aWorkerWithinTheOpportunityRadiusIsTakenOutsideTheTargetAreaAndOneBeyondItIsNot() {
        AirHarassTargeting.Contact worker = contact(1, UnitType.Terran_SCV,
                east(AirHarassTargeting.OPPORTUNITY_RADIUS));
        AirHarassTargeting.Contact tooFar = contact(2, UnitType.Terran_SCV,
                east(AirHarassTargeting.OPPORTUNITY_RADIUS + 1));
        AirHarassTargeting.Contact marine = contact(3, UnitType.Terran_Marine, east(100));

        assertEquals(1, AirHarassTargeting.bestTarget(muta(), situation(9)
                .contacts(Collections.singletonList(worker)).targetAllowed(point -> false).build(), -1).getId());
        assertNull(AirHarassTargeting.bestTarget(muta(), situation(9)
                .contacts(Collections.singletonList(tooFar)).targetAllowed(point -> false).build(), -1));
        assertEquals(3, AirHarassTargeting.bestTarget(muta(), situation(9)
                .contacts(Collections.singletonList(marine)).targetAllowed(point -> false).build(), -1).getId());
    }

    @Test
    void aSupplyOrProductionBuildingOutsideTheTargetAreaStaysLimitedToTheLocalRadius() {
        AirHarassTargeting.Contact depot = contact(1, UnitType.Terran_Supply_Depot,
                east(AirHarassTargeting.LOCAL_TARGET_RADIUS + 1));
        AirHarassTargeting.Contact barracks = contact(2, UnitType.Terran_Barracks,
                east(AirHarassTargeting.LOCAL_TARGET_RADIUS + 1));

        assertNull(AirHarassTargeting.bestTarget(muta(), situation(9)
                .contacts(Arrays.asList(depot, barracks)).targetAllowed(point -> false).build(), -1));
    }

    @Test
    void aMutaOnAProductionBuildingSwitchesToAWorkerThatComesWithinTheOpportunityRadius() {
        AirHarassTargeting.Contact barracks = contact(1, UnitType.Terran_Barracks, east(100));
        AirHarassTargeting.Contact worker = contact(2, UnitType.Terran_SCV,
                east(AirHarassTargeting.OPPORTUNITY_RADIUS - 8));
        AirHarassTargeting.Situation situation = situation(9)
                .contacts(Arrays.asList(barracks, worker)).targetAllowed(point -> false).build();

        assertEquals(2, AirHarassTargeting.bestTarget(muta(), situation, 1).getId());
    }

    @Test
    void aWorkerSteppingPastTheOpportunityRadiusIsKeptOnlyByTheMutaAlreadyOnIt() {
        AirHarassTargeting.Contact barracks = contact(1, UnitType.Terran_Barracks, east(100));
        AirHarassTargeting.Contact worker = contact(2, UnitType.Terran_SCV,
                east(AirHarassTargeting.OPPORTUNITY_RADIUS + 40));
        AirHarassTargeting.Situation situation = situation(9)
                .contacts(Arrays.asList(barracks, worker)).targetAllowed(point -> false).build();

        assertEquals(2, AirHarassTargeting.bestTarget(muta(), situation, 2).getId());
        assertEquals(1, AirHarassTargeting.bestTarget(muta(), situation, 1).getId());
        assertEquals(1, AirHarassTargeting.bestTarget(muta(), situation, -1).getId());
    }

    @Test
    void aWorkerBeyondTheLeaveRadiusIsDroppedEvenByTheMutaOnIt() {
        AirHarassTargeting.Contact barracks = contact(1, UnitType.Terran_Barracks, east(100));
        AirHarassTargeting.Contact worker = contact(2, UnitType.Terran_SCV,
                east(AirHarassTargeting.OPPORTUNITY_LEAVE_RADIUS + 1));
        AirHarassTargeting.Situation situation = situation(9)
                .contacts(Arrays.asList(barracks, worker)).targetAllowed(point -> false).build();

        assertEquals(1, AirHarassTargeting.bestTarget(muta(), situation, 2).getId());
    }

    @Test
    void theReachRadiusIsWiderForWorkersAndIsolatedAntiAirAndWidestOnceTheyAreTheTarget() {
        assertEquals(AirHarassTargeting.OPPORTUNITY_RADIUS, AirHarassTargeting.reachRadius(Tier.WORKER, false));
        assertEquals(AirHarassTargeting.OPPORTUNITY_RADIUS,
                AirHarassTargeting.reachRadius(Tier.EDGE_TURRET, false));
        assertEquals(AirHarassTargeting.OPPORTUNITY_LEAVE_RADIUS, AirHarassTargeting.reachRadius(Tier.WORKER, true));
        assertEquals(AirHarassTargeting.LOCAL_TARGET_RADIUS, AirHarassTargeting.reachRadius(Tier.SUPPLY, false));
        assertEquals(AirHarassTargeting.LOCAL_TARGET_RADIUS, AirHarassTargeting.reachRadius(Tier.PRODUCTION, true));
        assertTrue(AirHarassTargeting.OPPORTUNITY_LEAVE_RADIUS > AirHarassTargeting.OPPORTUNITY_RADIUS);
        assertTrue(AirHarassTargeting.OPPORTUNITY_RADIUS > AirHarassTargeting.LOCAL_TARGET_RADIUS);
    }

    @Test
    void aLoneTurretWithOtherAntiAirOnTheApproachIsNoEdgeTurret() {
        AirHarassTargeting.AirThreat turret = threat(7, UnitType.Terran_Missile_Turret, east(600));
        AirHarassTargeting.AirThreat goliath = threat(8, UnitType.Terran_Goliath,
                new Position(MUTA_AT.getX() + 100, MUTA_AT.getY() + 200));
        List<AirHarassTargeting.AirThreat> threats = threats(turret, goliath);

        assertFalse(goliath.covers(turret.getPosition(), AirHarassEvaluator.STRIKE_RADIUS));
        assertTrue(goliath.covers(new Position(MUTA_AT.getX() + 100, MUTA_AT.getY()), 0));
        assertTrue(AirHarassTargeting.edgeTurrets(threats, 12).contains(7));
        assertFalse(AirHarassTargeting.approachClear(turret, threats, MUTA_AT));
        assertTrue(AirHarassTargeting.edgeTurrets(threats, 12, MUTA_AT, Collections.emptySet()).isEmpty());
    }

    @Test
    void aLoneTurretWithAClearApproachIsAnEdgeTurretFromTheFlocksSide() {
        AirHarassTargeting.AirThreat turret = threat(7, UnitType.Terran_Missile_Turret, east(600));
        AirHarassTargeting.AirThreat goliath = threat(8, UnitType.Terran_Goliath,
                new Position(MUTA_AT.getX() - 900, MUTA_AT.getY() + 250));
        List<AirHarassTargeting.AirThreat> threats = threats(turret, goliath);

        assertTrue(AirHarassTargeting.approachClear(turret, threats, MUTA_AT));
        assertEquals(Collections.singleton(7),
                AirHarassTargeting.edgeTurrets(threats, 12, MUTA_AT, Collections.emptySet()));
    }

    @Test
    void anApproachAnotherAntiAirCoversOnlyFarFromTheTurretIsNotCheckedBeyondTheApproachSpan() {
        AirHarassTargeting.AirThreat turret = threat(7, UnitType.Terran_Missile_Turret, east(2000));
        AirHarassTargeting.AirThreat goliath = threat(8, UnitType.Terran_Goliath,
                new Position(MUTA_AT.getX() + 100, MUTA_AT.getY() + 250));

        assertTrue(AirHarassTargeting.approachClear(turret, threats(turret, goliath), MUTA_AT));
    }

    @Test
    void anUnknownFlockPositionLeavesTheApproachUnchecked() {
        AirHarassTargeting.AirThreat turret = threat(7, UnitType.Terran_Missile_Turret, east(600));

        assertTrue(AirHarassTargeting.approachClear(turret, threats(turret), null));
    }

    @Test
    void anEngagedTurretStaysAnEdgeTurretWhileItStandsWhateverElseIsKnown() {
        AirHarassTargeting.AirThreat turret = threat(7, UnitType.Terran_Missile_Turret, east(600));
        AirHarassTargeting.AirThreat goliath = threat(8, UnitType.Terran_Goliath, east(500));
        List<AirHarassTargeting.AirThreat> threats = threats(turret, goliath);

        assertTrue(AirHarassTargeting.edgeTurrets(threats, 12, MUTA_AT, Collections.emptySet()).isEmpty());
        assertEquals(Collections.singleton(7),
                AirHarassTargeting.edgeTurrets(threats, 12, MUTA_AT, Collections.singleton(7)));
        assertTrue(AirHarassTargeting.edgeTurrets(threats(goliath), 12, MUTA_AT, Collections.singleton(7)).isEmpty());
    }

    @Test
    void anEngagedTurretIsLeftOutOfThePricedDefenseWhileTheGoliathBesideItIsKept() {
        AirHarassTargeting.AirThreat turret = threat(7, UnitType.Terran_Missile_Turret, east(600));
        AirHarassTargeting.AirThreat goliath = threat(8, UnitType.Terran_Goliath, east(500));
        List<AirHarassTargeting.AirThreat> threats = threats(turret, goliath);

        List<AirHarassTargeting.AirThreat> priced = AirHarassTargeting.priced(threats,
                AirHarassTargeting.edgeTurrets(threats, 12, MUTA_AT, Collections.singleton(7)));

        assertEquals(Collections.singletonList(goliath), priced);
    }
}
