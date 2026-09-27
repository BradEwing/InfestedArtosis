package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import info.tracking.EnemyReachMemory;
import org.junit.jupiter.api.Test;
import unit.squad.horizon.UnitStrength;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.AirHarassTargeting.Kind;
import static unit.squad.AirHarassTargeting.Tier;

class AirHarassTargetingTest {

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

    @Test
    void workersComeFirstThenIsolatedAntiAirThenSupplyThenProductionThenAnythingElse() {
        int flock = 9;
        assertEquals(Tier.WORKER, AirHarassTargeting.tier(contact(1, UnitType.Terran_SCV, east(64)), flock));
        assertEquals(Tier.ISOLATED_AA, AirHarassTargeting.tier(contact(2, UnitType.Terran_Marine, east(64)), flock));
        assertEquals(Tier.SUPPLY, AirHarassTargeting.tier(contact(3, UnitType.Terran_Supply_Depot, east(64)), flock));
        assertEquals(Tier.PRODUCTION, AirHarassTargeting.tier(contact(4, UnitType.Terran_Barracks, east(64)), flock));
        assertEquals(Tier.PRODUCTION,
                AirHarassTargeting.tier(contact(5, UnitType.Terran_Command_Center, east(64)), flock));
        assertEquals(Tier.OTHER,
                AirHarassTargeting.tier(contact(6, UnitType.Terran_Siege_Tank_Siege_Mode, east(64)), flock));
        assertEquals(Tier.OTHER, AirHarassTargeting.tier(contact(7, UnitType.Terran_Academy, east(64)), flock));
        assertTrue(Tier.WORKER.ordinal() > Tier.ISOLATED_AA.ordinal());
        assertTrue(Tier.ISOLATED_AA.ordinal() > Tier.SUPPLY.ordinal());
        assertTrue(Tier.SUPPLY.ordinal() > Tier.PRODUCTION.ordinal());
        assertTrue(Tier.PRODUCTION.ordinal() > Tier.OTHER.ordinal());
    }

    @Test
    void antiAirTheFlockCannotKillQuicklyIsLeftAlone() {
        AirHarassTargeting.Contact goliath = contact(1, UnitType.Terran_Goliath, east(64));
        AirHarassTargeting.Contact turret = contact(2, UnitType.Terran_Missile_Turret, east(64));

        assertNull(AirHarassTargeting.tier(goliath, 2));
        assertEquals(Tier.ISOLATED_AA, AirHarassTargeting.tier(goliath, 9));
        assertNull(AirHarassTargeting.tier(turret, 9));
    }

    @Test
    void theKillCheckUsesPrimaryDamageLessArmorOverTwoVolleys() {
        UnitType goliath = UnitType.Terran_Goliath;
        int perHit = UnitType.Zerg_Mutalisk.groundWeapon().damageAmount() - goliath.armor();
        int enough = (int) Math.ceil(goliath.maxHitPoints() / (double) (perHit * AirHarassTargeting.KILL_VOLLEYS));

        assertTrue(AirHarassTargeting.killsQuickly(contact(1, goliath, east(0)), enough));
        assertFalse(AirHarassTargeting.killsQuickly(contact(1, goliath, east(0)), enough - 1));
    }

    @Test
    void aWorkerOutranksACloserDepot() {
        AirHarassTargeting.Contact depot = contact(1, UnitType.Terran_Supply_Depot, east(32));
        AirHarassTargeting.Contact scv = contact(2, UnitType.Terran_SCV, east(150));
        AirHarassTargeting.MutaMemory memory = new AirHarassTargeting.MutaMemory();

        AirHarassTargeting.Decision decision = AirHarassTargeting.choose(muta(),
                situation(9).contacts(Arrays.asList(depot, scv)).build(), memory);

        assertEquals(Kind.ATTACK, decision.getKind());
        assertEquals(2, decision.getTargetId());
        assertEquals(Tier.WORKER, decision.getTier());
        assertEquals(2, memory.getTargetId());
    }

    @Test
    void aWorkerUnderAnAvoidedTurretIsSkippedForAnOpenDepot() {
        Position turretAt = east(300);
        AirHarassTargeting.AirThreat turret = threat(50, UnitType.Terran_Missile_Turret, turretAt);
        AirHarassTargeting.Contact scv = contact(1, UnitType.Terran_SCV, new Position(turretAt.getX() + 40,
                turretAt.getY()));
        AirHarassTargeting.Contact depot = contact(2, UnitType.Terran_Supply_Depot, new Position(MUTA_AT.getX(),
                MUTA_AT.getY() - 100));

        AirHarassTargeting.Decision decision = AirHarassTargeting.choose(muta(), situation(9)
                .contacts(Arrays.asList(scv, depot))
                .avoided(Collections.singletonList(turret))
                .build(), new AirHarassTargeting.MutaMemory());

        assertEquals(Kind.ATTACK, decision.getKind());
        assertEquals(2, decision.getTargetId());
    }

    @Test
    void anIsolatedGoliathIsTakenInsideItsOwnZone() {
        Position goliathAt = east(100);
        AirHarassTargeting.AirThreat zone = threat(7, UnitType.Terran_Goliath, goliathAt);
        AirHarassTargeting.Contact goliath = contact(7, UnitType.Terran_Goliath, goliathAt);

        AirHarassTargeting.Decision decision = AirHarassTargeting.choose(muta(), situation(9)
                .contacts(Collections.singletonList(goliath))
                .avoided(Collections.singletonList(zone))
                .build(), new AirHarassTargeting.MutaMemory());

        assertEquals(Kind.ATTACK, decision.getKind());
        assertEquals(7, decision.getTargetId());
        assertEquals(Tier.ISOLATED_AA, decision.getTier());
    }

    @Test
    void aGoliathCoveredByAnotherAvoidedZoneIsNotIsolated() {
        Position goliathAt = east(300);
        AirHarassTargeting.AirThreat ownZone = threat(7, UnitType.Terran_Goliath, goliathAt);
        AirHarassTargeting.AirThreat turret = threat(8, UnitType.Terran_Missile_Turret,
                new Position(goliathAt.getX() + 64, goliathAt.getY()));
        AirHarassTargeting.Contact goliath = contact(7, UnitType.Terran_Goliath, goliathAt);

        AirHarassTargeting.Decision decision = AirHarassTargeting.choose(muta(), situation(9)
                .contacts(Collections.singletonList(goliath))
                .avoided(Arrays.asList(ownZone, turret))
                .build(), new AirHarassTargeting.MutaMemory());

        assertEquals(Kind.SEEK, decision.getKind());
    }

    @Test
    void aMutaInsideAnAvoidedZoneEvadesToItsEdge() {
        AirHarassTargeting.AirThreat turret = threat(50, UnitType.Terran_Missile_Turret, east(100));
        AirHarassTargeting.MutaMemory memory = new AirHarassTargeting.MutaMemory();

        AirHarassTargeting.Decision decision = AirHarassTargeting.choose(muta(), situation(9)
                .avoided(Collections.singletonList(turret))
                .build(), memory);

        assertEquals(Kind.EVADE, decision.getKind());
        assertTrue(memory.isEvading());
        assertTrue(turret.margin(decision.getPoint(), AirHarassTargeting.padding()) > 0);
    }

    @Test
    void anEvadingMutaKeepsItsPointForTheCommitFrames() {
        AirHarassTargeting.AirThreat turret = threat(50, UnitType.Terran_Missile_Turret, east(100));
        AirHarassTargeting.MutaMemory memory = new AirHarassTargeting.MutaMemory();
        AirHarassTargeting.Decision first = AirHarassTargeting.choose(muta(), situation(9)
                .avoided(Collections.singletonList(turret)).build(), memory);

        AirHarassTargeting.Decision second = AirHarassTargeting.choose(new AirHarassTargeting.Muta(1, east(-400)),
                situation(9).avoided(Collections.singletonList(turret)).now(NOW + 1).build(), memory);

        assertEquals(Kind.EVADE, second.getKind());
        assertEquals(first.getPoint(), second.getPoint());
    }

    @Test
    void aTolerableZoneIsFlownThrough() {
        AirHarassTargeting.Contact scv = contact(1, UnitType.Terran_SCV, east(120));

        AirHarassTargeting.Decision decision = AirHarassTargeting.choose(muta(), situation(9)
                .contacts(Collections.singletonList(scv))
                .build(), new AirHarassTargeting.MutaMemory());

        assertEquals(Kind.ATTACK, decision.getKind());
    }

    @Test
    void withNothingToHitTheMutaFliesStraightAtAClearSeekPoint() {
        AirHarassTargeting.Decision decision = AirHarassTargeting.choose(muta(), situation(9).build(),
                new AirHarassTargeting.MutaMemory());

        assertEquals(Kind.SEEK, decision.getKind());
        assertEquals(SEEK, decision.getPoint());
    }

    @Test
    void aZoneAcrossThePathIsSkirtedOnTheWayToTheSeekPoint() {
        AirHarassTargeting.AirThreat turret = threat(50, UnitType.Terran_Missile_Turret, east(400));
        List<AirHarassTargeting.AirThreat> zones = Collections.singletonList(turret);
        Position seek = east(800);

        Position step = AirHarassTargeting.edgePoint(MUTA_AT, zones, seek, point -> true, true);

        assertNotNull(step);
        assertFalse(AirHarassTargeting.segmentClear(MUTA_AT, seek, zones));
        assertTrue(turret.margin(step, AirHarassTargeting.padding()) > 0);
        assertTrue(AirHarassTargeting.segmentClear(MUTA_AT, step, zones));
        assertTrue(step.getDistance(seek) < MUTA_AT.getDistance(seek));
    }

    @Test
    void withNoClearPointTheSafestAllowedPointIsTaken() {
        AirHarassTargeting.AirThreat turret = threat(50, UnitType.Terran_Missile_Turret, MUTA_AT);

        Position step = AirHarassTargeting.edgePoint(MUTA_AT, Collections.singletonList(turret), SEEK,
                point -> true, false);

        assertNotNull(step);
        assertNull(AirHarassTargeting.edgePoint(MUTA_AT, Collections.singletonList(turret), null, point -> true,
                false));
        assertNull(AirHarassTargeting.edgePoint(MUTA_AT, Collections.singletonList(turret), SEEK, point -> false,
                false));
    }

    @Test
    void aZoneIsAvoidedWhenItsStackedStrengthExceedsTheTolerance() {
        AirHarassTargeting.AirThreat goliath = threat(1, UnitType.Terran_Goliath, east(0));
        AirHarassTargeting.AirThreat second = threat(2, UnitType.Terran_Goliath, east(32));
        AirHarassTargeting.AirThreat faraway = threat(3, UnitType.Terran_Goliath, east(2000));
        double one = UnitStrength.antiAirStrength(UnitType.Terran_Goliath);

        List<AirHarassTargeting.AirThreat> avoided = AirHarassTargeting.avoided(
                Arrays.asList(goliath, second, faraway), one * 1.5);

        assertEquals(2, avoided.size());
        assertTrue(avoided.contains(goliath));
        assertTrue(avoided.contains(second));
        assertTrue(AirHarassTargeting.avoided(Arrays.asList(goliath, second), one * 2).isEmpty());
    }

    @Test
    void defenseCountsOnlyThreatsThatReachThePointPlusTheRadius() {
        AirHarassTargeting.AirThreat turret = threat(1, UnitType.Terran_Missile_Turret, east(0));
        int reach = turret.getReach();
        int edge = UnitType.Terran_Missile_Turret.dimensionRight();
        Position justInside = east(edge + reach + 100);
        Position justOutside = east(edge + reach + 101);
        List<AirHarassTargeting.AirThreat> threats = Collections.singletonList(turret);

        assertEquals(turret.getStrength(), AirHarassTargeting.defenseAt(threats, justInside, 100), 1e-9);
        assertEquals(0, AirHarassTargeting.defenseAt(threats, justOutside, 100), 1e-9);
    }

    @Test
    void airRangeIsTheWeaponRangeAndABunkerFiresItsMarines() {
        int turret = UnitType.Terran_Missile_Turret.airWeapon().maxRange();
        int marine = UnitType.Terran_Marine.airWeapon().maxRange();

        assertEquals(turret, AirHarassTargeting.airRange(UnitType.Terran_Missile_Turret, weapon -> 0));
        assertEquals(marine + EnemyReachMemory.BUNKER_ALLOWANCE,
                AirHarassTargeting.airRange(UnitType.Terran_Bunker, weapon -> 0));
        assertEquals(0, AirHarassTargeting.airRange(UnitType.Terran_Siege_Tank_Tank_Mode, weapon -> 999));
        assertEquals(turret + 64, AirHarassTargeting.airRange(UnitType.Terran_Missile_Turret, weapon -> turret + 64));
    }

    @Test
    void mobileThreatsReachFurtherThanTheirRangeAndStructuresDoNot() {
        int goliathRange = UnitType.Terran_Goliath.airWeapon().maxRange();

        assertTrue(threat(1, UnitType.Terran_Goliath, east(0)).getReach() > goliathRange);
        assertEquals(UnitType.Terran_Missile_Turret.airWeapon().maxRange(),
                threat(2, UnitType.Terran_Missile_Turret, east(0)).getReach());
    }

    @Test
    void aBunkerAndStaticAntiAirCountAsAntiAirButATankDoesNot() {
        assertTrue(AirHarassTargeting.isAntiAir(UnitType.Terran_Bunker));
        assertTrue(AirHarassTargeting.isAntiAir(UnitType.Terran_Missile_Turret));
        assertTrue(AirHarassTargeting.isAntiAir(UnitType.Terran_Goliath));
        assertTrue(AirHarassTargeting.isAntiAir(UnitType.Protoss_Photon_Cannon));
        assertFalse(AirHarassTargeting.isAntiAir(UnitType.Terran_Siege_Tank_Siege_Mode));
        assertFalse(AirHarassTargeting.isAntiAir(UnitType.Terran_SCV));
    }

    @Test
    void aTargetFarFromTheBaseIsTakenOnlyWhenClose() {
        AirHarassTargeting.Contact near = contact(1, UnitType.Terran_SCV, east(AirHarassTargeting.LOCAL_TARGET_RADIUS));
        AirHarassTargeting.Contact far = contact(2, UnitType.Terran_SCV,
                east(AirHarassTargeting.LOCAL_TARGET_RADIUS + 1));

        AirHarassTargeting.Decision nearDecision = AirHarassTargeting.choose(muta(), situation(9)
                .contacts(Collections.singletonList(near)).targetAllowed(point -> false).build(),
                new AirHarassTargeting.MutaMemory());
        AirHarassTargeting.Decision farDecision = AirHarassTargeting.choose(muta(), situation(9)
                .contacts(Collections.singletonList(far)).targetAllowed(point -> false).build(),
                new AirHarassTargeting.MutaMemory());

        assertEquals(Kind.ATTACK, nearDecision.getKind());
        assertEquals(Kind.SEEK, farDecision.getKind());
    }

    @Test
    void theCurrentTargetIsKeptOverAnEquallyGoodNewOne() {
        AirHarassTargeting.Contact first = contact(1, UnitType.Terran_SCV, east(100));
        AirHarassTargeting.Contact second = contact(2, UnitType.Terran_SCV, east(-95));

        assertEquals(1, AirHarassTargeting.bestTarget(muta(), situation(9)
                .contacts(Arrays.asList(first, second)).build(), 1).getId());
        assertEquals(2, AirHarassTargeting.bestTarget(muta(), situation(9)
                .contacts(Arrays.asList(first, second)).build(), -1).getId());
    }
}
