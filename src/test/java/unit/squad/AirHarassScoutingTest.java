package unit.squad;

import bwapi.Position;
import bwapi.Race;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.AirHarassEvaluator.EntryVerdict;
import static unit.squad.AirHarassScouting.ProbeOutcome;

class AirHarassScoutingTest {

    private static final int NOW = 10296;
    private static final Position BASE = new Position(2112, 3824);
    private static final Position STRIKE = new Position(2096, 3664);
    private static final Position TURRET = new Position(2016, 3680);

    private static AirHarassTargeting.AirThreat threat(int id, UnitType type, Position position) {
        return AirHarassTargeting.AirThreat.of(id, type, position,
                AirHarassTargeting.airRange(type, weapon -> weapon.maxRange()));
    }

    private static Map<UnitType, Integer> mutas(int count) {
        Map<UnitType, Integer> composition = new HashMap<>();
        composition.put(UnitType.Zerg_Mutalisk, count);
        return composition;
    }

    private static EntryVerdict gates(int healthy) {
        return AirHarassEvaluator.entryVerdict(AirHarassEvaluator.EntryInput.builder()
                .opponentRace(Race.Terran)
                .composition(mutas(healthy))
                .healthyMutas(healthy)
                .basesUnderAttack(false)
                .options(Collections.singletonList(
                        new AirHarassEvaluator.BaseOption<>("main", STRIKE, 90, true, -1)))
                .build());
    }

    @Test
    void aBaseNeverSightedIsAsOldAsTheGame() {
        assertEquals(NOW, AirHarassScouting.sightingAge(-1, NOW));
        assertTrue(AirHarassScouting.stale(AirHarassScouting.sightingAge(-1, NOW)));
    }

    @Test
    void aSightingGoesStaleOnlyPastTheLimit() {
        int limit = AirHarassScouting.STALE_SIGHTING_FRAMES;
        assertEquals(limit, AirHarassScouting.sightingAge(NOW - limit, NOW));
        assertFalse(AirHarassScouting.stale(limit));
        assertTrue(AirHarassScouting.stale(limit + 1));
        assertFalse(AirHarassScouting.stale(0));
    }

    @Test
    void anEntryOnABaseWhoseAntiAirWasNeverSightedIsProbedInstead() {
        EntryVerdict verdict = gates(5);
        assertEquals(EntryVerdict.ENTER, verdict);

        assertEquals(EntryVerdict.PROBE,
                AirHarassScouting.entryMode(verdict, AirHarassScouting.sightingAge(-1, NOW)));
    }

    @Test
    void anEntryOnABaseSightedWithinTheLimitStrikesStraightAway() {
        assertEquals(EntryVerdict.ENTER, AirHarassScouting.entryMode(EntryVerdict.ENTER,
                AirHarassScouting.STALE_SIGHTING_FRAMES));
        assertEquals(EntryVerdict.PROBE, AirHarassScouting.entryMode(EntryVerdict.ENTER,
                AirHarassScouting.STALE_SIGHTING_FRAMES + 1));
    }

    @Test
    void aRefusedEntryStaysRefusedWhateverTheSighting() {
        assertEquals(EntryVerdict.DEFENDED, AirHarassScouting.entryMode(EntryVerdict.DEFENDED, NOW));
        assertEquals(EntryVerdict.TOO_FEW, AirHarassScouting.entryMode(EntryVerdict.TOO_FEW, NOW));
        assertEquals(EntryVerdict.NO_TARGET, AirHarassScouting.entryMode(EntryVerdict.NO_TARGET, 0));
    }

    @Test
    void theCoreIsSightedOnlyWhenEveryPointIsVisible() {
        Position resources = new Position(2112, 3950);
        List<Position> core = Arrays.asList(BASE, resources);

        assertTrue(AirHarassScouting.coreSighted(core, point -> true));
        assertFalse(AirHarassScouting.coreSighted(core, point -> point.equals(BASE)));
        assertFalse(AirHarassScouting.coreSighted(Collections.emptyList(), point -> true));
    }

    @Test
    void theProberFliesBetweenTheBaseCenterAndItsResources() {
        assertEquals(new Position(2112, 3887), AirHarassScouting.probePoint(BASE, new Position(2112, 3950)));
        assertEquals(BASE, AirHarassScouting.probePoint(BASE, null));
    }

    @Test
    void theFlockHoldsShortOfTheBaseOnItsOwnSide() {
        Position flock = new Position(2009, 589);

        Position hold = AirHarassScouting.holdPoint(BASE, flock);

        assertEquals(AirHarassScouting.PROBE_HOLD_DISTANCE, hold.getDistance(BASE), 2);
        assertTrue(hold.getY() < BASE.getY());
    }

    @Test
    void aFlockAlreadyCloserThanTheHoldDistanceHoldsWhereItIs() {
        Position flock = new Position(2112, 3424);

        assertEquals(flock, AirHarassScouting.holdPoint(BASE, flock));
    }

    @Test
    void theHealthiestMutaProbesAndTheLowestIdBreaksATie() {
        Map<Integer, Integer> hitPoints = new HashMap<>();
        hitPoints.put(265, 120);
        hitPoints.put(260, 120);
        hitPoints.put(250, 90);

        assertEquals(260, AirHarassScouting.chooseProber(hitPoints));
        assertEquals(-1, AirHarassScouting.chooseProber(new HashMap<>()));
    }

    @Test
    void aProberLostOrHitMeansTheBaseIsDefended() {
        assertEquals(ProbeOutcome.DEFENDED,
                AirHarassScouting.probeOutcome(false, 0, 120, false, true, NOW, NOW - 24));
        assertEquals(ProbeOutcome.DEFENDED,
                AirHarassScouting.probeOutcome(true, 111, 120, true, true, NOW, NOW - 24));
    }

    @Test
    void aSightedCoreClearsTheStrikeOnlyWithATolerantStrikePointLeft() {
        assertEquals(ProbeOutcome.CLEAR, AirHarassScouting.probeOutcome(true, 120, 120, true, true, NOW, NOW - 24));
        assertEquals(ProbeOutcome.DEFENDED,
                AirHarassScouting.probeOutcome(true, 120, 120, true, false, NOW, NOW - 24));
    }

    @Test
    void aProbeWithoutASightingWaitsUntilItTimesOut() {
        int timeout = AirHarassScouting.PROBE_TIMEOUT_FRAMES;
        assertEquals(ProbeOutcome.WAIT,
                AirHarassScouting.probeOutcome(true, 120, 120, false, true, NOW, NOW - timeout + 1));
        assertEquals(ProbeOutcome.TIMED_OUT,
                AirHarassScouting.probeOutcome(true, 120, 120, false, true, NOW, NOW - timeout));
    }

    @Test
    void aTurretFirstSeenInsideTheHarassZoneEndsTheHarassOfAFiveMutaFlock() {
        AirHarassTargeting.AirThreat turret = threat(259, UnitType.Terran_Missile_Turret, TURRET);
        List<AirHarassTargeting.AirThreat> threats = Collections.singletonList(turret);
        double tolerance = AirHarassEvaluator.tolerance(5);

        assertTrue(AirHarassScouting.newAntiAirExit(threats, threats, BASE, new Position(2099, 3314), tolerance));
    }

    @Test
    void aTurretKnownBeforeTheHarassIsNotNew() {
        AirHarassTargeting.AirThreat turret = threat(259, UnitType.Terran_Missile_Turret, TURRET);

        assertFalse(AirHarassScouting.newAntiAirExit(Collections.emptyList(), Collections.singletonList(turret),
                BASE, STRIKE, AirHarassEvaluator.tolerance(5)));
    }

    @Test
    void aNewTurretOutsideTheZoneAndAwayFromTheFlockDoesNotEndTheHarass() {
        AirHarassTargeting.AirThreat turret = threat(300, UnitType.Terran_Missile_Turret, new Position(3500, 1000));
        List<AirHarassTargeting.AirThreat> threats = Collections.singletonList(turret);

        assertFalse(AirHarassScouting.newAntiAirExit(threats, threats, BASE, STRIKE, AirHarassEvaluator.tolerance(5)));
    }

    @Test
    void aNewTurretAwayFromTheBaseButCoveringTheFlockEndsTheHarass() {
        Position flock = new Position(3500, 1100);
        AirHarassTargeting.AirThreat turret = threat(300, UnitType.Terran_Missile_Turret, new Position(3500, 1000));
        List<AirHarassTargeting.AirThreat> threats = Collections.singletonList(turret);

        assertTrue(AirHarassScouting.newAntiAirExit(threats, threats, BASE, flock, AirHarassEvaluator.tolerance(5)));
    }

    @Test
    void aNewMobileUnitIsLeftToTheAntiAirExit() {
        AirHarassTargeting.AirThreat goliath = threat(301, UnitType.Terran_Goliath, TURRET);
        List<AirHarassTargeting.AirThreat> threats = Collections.singletonList(goliath);

        assertFalse(AirHarassScouting.newAntiAirExit(threats, threats, BASE, STRIKE, 0));
    }

    @Test
    void aNewTurretTheFlockToleratesDoesNotEndTheHarass() {
        AirHarassTargeting.AirThreat turret = threat(259, UnitType.Terran_Missile_Turret, TURRET);
        List<AirHarassTargeting.AirThreat> threats = Collections.singletonList(turret);
        double tolerance = turret.getStrength();

        assertFalse(AirHarassScouting.newAntiAirExit(threats, threats, BASE, STRIKE, tolerance));
    }

    @Test
    void theFlockLeavesANewTurretForOnePointBeyondItsReach() {
        AirHarassTargeting.AirThreat turret = threat(259, UnitType.Terran_Missile_Turret, TURRET);
        Position flock = new Position(2205, 3421);

        Position exit = AirHarassScouting.sharedExitPoint(flock, Collections.singletonList(turret));

        assertTrue(exit.getDistance(TURRET) > flock.getDistance(TURRET));
        assertEquals(turret.getReach() + AirHarassScouting.EXIT_MARGIN, exit.getDistance(flock), 2);
        assertTrue(turret.margin(exit, 0) > 0);
    }

    @Test
    void aFlockWithNoAntiAirNearHasNoSharedExit() {
        AirHarassTargeting.AirThreat turret = threat(300, UnitType.Terran_Missile_Turret, new Position(3500, 1000));

        assertNull(AirHarassScouting.sharedExitPoint(STRIKE, Collections.singletonList(turret)));
        assertNull(AirHarassScouting.sharedExitPoint(STRIKE, Collections.emptyList()));
        assertNull(AirHarassScouting.sharedExitPoint(null, Collections.singletonList(turret)));
    }

    @Test
    void aProbeHoldsItsPointsUntilItClearsAndATargetDropsIt() {
        AirHarassState state = new AirHarassState(NOW, 600);
        Position probe = new Position(2112, 3887);
        Position hold = new Position(2112, 3184);

        state.probe(null, STRIKE, 265, 120, probe, hold, NOW);

        assertEquals(AirHarassState.Phase.PROBE, state.getPhase());
        assertEquals(265, state.getProberId());
        assertEquals(120, state.getProberStartHitPoints());
        assertEquals(NOW, state.getProbeStartFrame());
        assertEquals(probe, state.getProbePoint());
        assertEquals(hold, state.getHoldPoint());

        Position cleared = new Position(1968, 4048);
        state.clearProbe(cleared, NOW + 48);

        assertEquals(AirHarassState.Phase.TRANSIT, state.getPhase());
        assertEquals(cleared, state.getStrikePoint());
        assertEquals(-1, state.getProberId());
        assertNull(state.getHoldPoint());
        assertEquals(NOW + 48, state.getLastProgressFrame());

        state.probe(null, STRIKE, 265, 120, probe, hold, NOW + 60);
        state.target(null, STRIKE, NOW + 72);

        assertEquals(AirHarassState.Phase.TRANSIT, state.getPhase());
        assertNull(state.getProbePoint());
    }

    @Test
    void anAntiAirThreatIsNewOnlyTheFirstTimeItIsLearned() {
        AirHarassState state = new AirHarassState(NOW, 600);
        AirHarassTargeting.AirThreat turret = threat(259, UnitType.Terran_Missile_Turret, TURRET);
        AirHarassTargeting.AirThreat other = threat(252, UnitType.Terran_Missile_Turret, new Position(2208, 3744));

        assertEquals(1, state.learnAntiAir(Collections.singletonList(turret)).size());
        List<AirHarassTargeting.AirThreat> fresh = state.learnAntiAir(Arrays.asList(turret, other));

        assertEquals(1, fresh.size());
        assertEquals(252, fresh.get(0).getId());
        assertTrue(state.learnAntiAir(Arrays.asList(turret, other)).isEmpty());
    }
}
