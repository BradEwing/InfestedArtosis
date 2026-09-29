package unit.squad;

import bwapi.Position;
import bwapi.Race;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
    void theCoreIsSightedWhenAnyPointIsVisible() {
        Position resources = new Position(2112, 3950);
        List<Position> core = Arrays.asList(BASE, resources);

        assertTrue(AirHarassScouting.coreSighted(core, point -> true));
        assertTrue(AirHarassScouting.coreSighted(core, point -> point.equals(BASE)));
        assertTrue(AirHarassScouting.coreSighted(core, point -> point.equals(resources)));
        assertFalse(AirHarassScouting.coreSighted(core, point -> false));
        assertFalse(AirHarassScouting.coreSighted(Collections.emptyList(), point -> true));
    }

    @Test
    void theProberFliesBetweenTheBaseCenterAndItsResources() {
        assertEquals(new Position(2112, 3887), AirHarassScouting.probePoint(BASE, new Position(2112, 3950),
                UnitType.Zerg_Mutalisk.sightRange()));
        assertEquals(BASE, AirHarassScouting.probePoint(BASE, null, UnitType.Zerg_Mutalisk.sightRange()));
    }

    @Test
    void theFlockHoldsShortOfTheBaseOnItsOwnSide() {
        Position flock = new Position(2009, 589);

        Position hold = AirHarassScouting.holdPoint(BASE, flock, Collections.emptyList());

        assertEquals(AirHarassScouting.PROBE_HOLD_DISTANCE, hold.getDistance(BASE), 2);
        assertTrue(hold.getY() < BASE.getY());
    }

    @Test
    void aFlockAlreadyCloserThanTheHoldDistanceHoldsWhereItIs() {
        Position flock = new Position(2112, 3424);

        assertEquals(flock, AirHarassScouting.holdPoint(BASE, flock, Collections.emptyList()));
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
    void aProberLostOrHurtMeansTheBaseIsDefended() {
        int hurt = 120 - AirHarassScouting.PROBE_DAMAGE_HIT_POINTS;
        assertEquals(ProbeOutcome.DEFENDED,
                AirHarassScouting.probeOutcome(false, 0, 120, false, true, NOW, NOW - 24));
        assertEquals(ProbeOutcome.DEFENDED,
                AirHarassScouting.probeOutcome(true, hurt, 120, true, true, NOW, NOW - 24));
    }

    @Test
    void aStrayHitOnTheProberDoesNotEndTheProbe() {
        int grazed = 120 - AirHarassScouting.PROBE_DAMAGE_HIT_POINTS + 1;
        assertEquals(ProbeOutcome.CLEAR,
                AirHarassScouting.probeOutcome(true, grazed, 120, true, true, NOW, NOW - 24));
        assertEquals(ProbeOutcome.WAIT,
                AirHarassScouting.probeOutcome(true, grazed, 120, false, true, NOW, NOW - 24));
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
        assertEquals(120, state.getProberPeakHitPoints());
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
    void damageIsMeasuredFromTheRegeneratedPeak() {
        AirHarassState state = new AirHarassState(NOW, 500);
        state.probe(null, STRIKE, 265, 100, new Position(2112, 3887), new Position(2112, 3184), NOW);
        state.observeProberHitPoints(104);
        int hurt = 104 - AirHarassScouting.PROBE_DAMAGE_HIT_POINTS;
        state.observeProberHitPoints(hurt);

        assertEquals(104, state.getProberPeakHitPoints());
        assertEquals(ProbeOutcome.DEFENDED, AirHarassScouting.probeOutcome(true, hurt,
                state.getProberPeakHitPoints(), false, true, NOW + 24, NOW));
        assertEquals(ProbeOutcome.WAIT, AirHarassScouting.probeOutcome(true, hurt + 1,
                state.getProberPeakHitPoints(), false, true, NOW + 24, NOW));
    }

    @Test
    void theProberFliesOverTheStrikePointOnceTheResourcesAreSighted() {
        Position probe = new Position(2112, 3887);

        assertEquals(probe, AirHarassScouting.proberDestination(false, probe, STRIKE));
        assertEquals(STRIKE, AirHarassScouting.proberDestination(true, probe, STRIKE));
        assertEquals(probe, AirHarassScouting.proberDestination(true, probe, null));
    }

    @Test
    void aProbeJudgesTheBaseOnlyOnceItHasSeenTheResourcesAndTheStrikePoint() {
        assertFalse(AirHarassScouting.probeSighted(false, true, true));
        assertFalse(AirHarassScouting.probeSighted(true, true, false));
        assertTrue(AirHarassScouting.probeSighted(true, true, true));
        assertTrue(AirHarassScouting.probeSighted(true, false, false));
    }

    @Test
    void antiAirLeavingTheBaseNoStrikeDuringAProbeIsTheProbeFindingTheBaseDefended() {
        assertEquals(AirHarassEvaluator.ExitReason.PROBE_DEFENDED,
                AirHarassScouting.probeExitReason(AirHarassEvaluator.ExitReason.STRIKE_DEFENDED, true, false));
        assertEquals(AirHarassEvaluator.ExitReason.STRIKE_DEFENDED,
                AirHarassScouting.probeExitReason(AirHarassEvaluator.ExitReason.STRIKE_DEFENDED, false, false));
        assertEquals(AirHarassEvaluator.ExitReason.HP_LOSS,
                AirHarassScouting.probeExitReason(AirHarassEvaluator.ExitReason.HP_LOSS, true, false));
        assertNull(AirHarassScouting.probeExitReason(null, true, false));
    }

    @Test
    void aFlockStandingInAntiAirDuringAProbeKeepsItsOwnExitReason() {
        assertEquals(AirHarassEvaluator.ExitReason.FLOCK_DEFENDED,
                AirHarassScouting.probeExitReason(AirHarassEvaluator.ExitReason.FLOCK_DEFENDED, true, true));
        assertEquals(AirHarassEvaluator.ExitReason.STRIKE_DEFENDED,
                AirHarassScouting.probeExitReason(AirHarassEvaluator.ExitReason.STRIKE_DEFENDED, true, true));
        assertEquals(AirHarassEvaluator.ExitReason.FLOCK_DEFENDED,
                AirHarassScouting.probeExitReason(AirHarassEvaluator.ExitReason.FLOCK_DEFENDED, false, true));
    }

    @Test
    void aVisibleBaseCenterAloneDoesNotSightTheResourcesForAProbe() {
        Position resources = new Position(2112, 3950);

        assertFalse(AirHarassScouting.probeResourcesSighted(false, BASE, resources, point -> point.equals(BASE)));
        assertTrue(AirHarassScouting.probeResourcesSighted(false, BASE, resources,
                point -> point.equals(resources)));
        assertTrue(AirHarassScouting.probeResourcesSighted(true, BASE, resources, point -> false));
        assertTrue(AirHarassScouting.probeResourcesSighted(false, BASE, null, point -> point.equals(BASE)));
    }

    @Test
    void aProbeSeeingOnlyTheBaseCenterNeverClearsTheStrike() {
        Position resources = new Position(2112, 3950);
        boolean resourcesSighted = AirHarassScouting.probeResourcesSighted(false, BASE, resources,
                point -> point.equals(BASE) || point.equals(STRIKE));
        boolean sighted = AirHarassScouting.probeSighted(resourcesSighted, true, true);

        assertEquals(ProbeOutcome.WAIT, AirHarassScouting.probeOutcome(true, 120, 120, sighted, true, NOW, NOW - 24));
    }

    @Test
    void theHoldPointMovesOutOfAKnownTurretsReach() {
        Position flock = new Position(2009, 589);
        Position plain = AirHarassScouting.holdPoint(BASE, flock, Collections.emptyList());
        List<AirHarassTargeting.AirThreat> threats = Collections.singletonList(
                threat(301, UnitType.Terran_Missile_Turret, plain));

        assertTrue(AirHarassScouting.holdExposed(threats, plain));
        Position hold = AirHarassScouting.holdPoint(BASE, flock, threats);

        assertFalse(AirHarassScouting.holdExposed(threats, hold));
        assertTrue(hold.getDistance(BASE) > AirHarassScouting.PROBE_HOLD_DISTANCE);
        assertTrue(hold.getDistance(BASE) <= AirHarassScouting.PROBE_HOLD_DISTANCE
                + AirHarassScouting.PROBE_HOLD_SEARCH + 1);
        assertTrue(hold.getY() < plain.getY());
    }

    @Test
    void aFlockCloserThanTheHoldDistanceInsideAKnownTurretsReachHoldsFartherOut() {
        Position flock = new Position(2112, 3424);
        List<AirHarassTargeting.AirThreat> threats = Collections.singletonList(
                threat(302, UnitType.Terran_Missile_Turret, flock));

        Position hold = AirHarassScouting.holdPoint(BASE, flock, threats);

        assertFalse(AirHarassScouting.holdExposed(threats, hold));
        assertTrue(hold.getDistance(BASE) > flock.getDistance(BASE));
    }

    @Test
    void aHoldPointCoveredAllTheWayOutTakesTheWeakestCoverTried() {
        Position flock = new Position(2112, 1824);
        List<AirHarassTargeting.AirThreat> threats = new ArrayList<>();
        for (int y = 3200; y >= 2400; y -= 64) {
            threats.add(threat(400 + y, UnitType.Terran_Missile_Turret, new Position(2112, y)));
        }
        Position farthest = new Position(2112, BASE.getY() - AirHarassScouting.PROBE_HOLD_DISTANCE
                - AirHarassScouting.PROBE_HOLD_SEARCH);
        for (int i = 0; i < 3; i++) {
            threats.add(threat(900 + i, UnitType.Terran_Missile_Turret, farthest));
        }

        Position hold = AirHarassScouting.holdPoint(BASE, flock, threats);

        assertTrue(AirHarassScouting.holdExposed(threats, hold));
        double holdDefense = AirHarassTargeting.defenseAt(threats, hold, AirHarassScouting.EXIT_MARGIN);
        assertTrue(holdDefense < AirHarassTargeting.defenseAt(threats, farthest, AirHarassScouting.EXIT_MARGIN));
        for (int distance = AirHarassScouting.PROBE_HOLD_DISTANCE;
             distance <= AirHarassScouting.PROBE_HOLD_DISTANCE + AirHarassScouting.PROBE_HOLD_SEARCH;
             distance += AirHarassScouting.PROBE_HOLD_STEP) {
            Position tried = new Position(2112, BASE.getY() - distance);
            assertTrue(holdDefense <= AirHarassTargeting.defenseAt(threats, tried, AirHarassScouting.EXIT_MARGIN));
        }
    }

    @Test
    void resourcesFartherThanTheProbersSightFromTheMidpointPullTheProbePointToThem() {
        int sight = UnitType.Zerg_Mutalisk.sightRange();
        Position resources = new Position(BASE.getX(), BASE.getY() - 4 * sight);

        Position probe = AirHarassScouting.probePoint(BASE, resources, sight);

        assertEquals(sight - 32, probe.getDistance(resources), 2);
        assertEquals(BASE.getX(), probe.getX());
    }

    @Test
    void aHoldPointNoAntiAirCoversIsNotExposed() {
        assertFalse(AirHarassScouting.holdExposed(Collections.emptyList(), BASE));
        assertFalse(AirHarassScouting.holdExposed(
                Collections.singletonList(threat(303, UnitType.Terran_Missile_Turret, TURRET)), null));
    }

    @Test
    void aProbingMutaLeavesOnlyTheProbedBaseHot() {
        int sight = UnitType.Zerg_Mutalisk.sightRange();
        int edge = AirHarassScouting.NEW_AA_ZONE + sight;

        assertFalse(AirHarassScouting.coolsHeat(true, new Position(BASE.getX(), BASE.getY() - edge), BASE, sight));
        assertTrue(AirHarassScouting.coolsHeat(true, new Position(BASE.getX(), BASE.getY() - edge - 1), BASE,
                sight));
        assertTrue(AirHarassScouting.coolsHeat(false, BASE, BASE, sight));
        assertTrue(AirHarassScouting.coolsHeat(true, BASE, null, sight));
    }

    @Test
    void aNewTurretCoveringTheFlockEndsTheHarassAfterTheTargetBaseIsGone() {
        Position flock = new Position(3500, 1100);
        AirHarassTargeting.AirThreat turret = threat(300, UnitType.Terran_Missile_Turret, new Position(3500, 1000));
        List<AirHarassTargeting.AirThreat> threats = Collections.singletonList(turret);

        assertTrue(AirHarassScouting.newAntiAirExit(threats, threats, null, flock, AirHarassEvaluator.tolerance(5)));
        assertFalse(AirHarassScouting.newAntiAirExit(threats, threats, null, STRIKE, AirHarassEvaluator.tolerance(5)));
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

    @Test
    void aKnownTurretOverTheProbePointMakesTheBaseCountAsSightedSoTheEntryIsNotProbed() {
        AirHarassTargeting.AirThreat turret = threat(259, UnitType.Terran_Missile_Turret, TURRET);
        Position probePoint = new Position(2064, 3700);
        boolean covered = AirHarassScouting.knownAntiAirCovers(Collections.singletonList(turret), probePoint);

        assertTrue(covered);
        assertEquals(0, AirHarassScouting.probeSightingAge(NOW, covered));
        assertEquals(EntryVerdict.ENTER, AirHarassScouting.entryMode(EntryVerdict.ENTER,
                AirHarassScouting.probeSightingAge(NOW, covered)));
        assertEquals(EntryVerdict.DEFENDED, AirHarassScouting.entryMode(EntryVerdict.DEFENDED,
                AirHarassScouting.probeSightingAge(NOW, covered)));
    }

    @Test
    void aProbePointNoKnownStructureCoversIsStillProbedWhenStale() {
        AirHarassTargeting.AirThreat farTurret = threat(300, UnitType.Terran_Missile_Turret, new Position(3500, 1000));
        AirHarassTargeting.AirThreat goliath = threat(301, UnitType.Terran_Goliath, new Position(2064, 3700));
        Position probePoint = new Position(2064, 3700);
        boolean covered = AirHarassScouting.knownAntiAirCovers(Arrays.asList(farTurret, goliath), probePoint);

        assertFalse(covered);
        assertEquals(NOW, AirHarassScouting.probeSightingAge(NOW, covered));
        assertEquals(EntryVerdict.PROBE, AirHarassScouting.entryMode(EntryVerdict.ENTER,
                AirHarassScouting.probeSightingAge(NOW, covered)));
    }

    @Test
    void onlyAProbeFindingTheBaseDefendedRefusesIt() {
        assertTrue(AirHarassScouting.refusesBase(AirHarassEvaluator.ExitReason.PROBE_DEFENDED));
        for (AirHarassEvaluator.ExitReason reason : AirHarassEvaluator.ExitReason.values()) {
            if (reason != AirHarassEvaluator.ExitReason.PROBE_DEFENDED) {
                assertFalse(AirHarassScouting.refusesBase(reason), reason.name());
            }
        }
        assertFalse(AirHarassScouting.refusesBase(null));
    }

    @Test
    void theProberRegroupsOnlyWhenTheProbeEndedTheHarassAndTheHoldPointIsClear() {
        for (AirHarassEvaluator.ExitReason reason : AirHarassEvaluator.ExitReason.values()) {
            boolean probeEnded = reason == AirHarassEvaluator.ExitReason.PROBE_DEFENDED
                    || reason == AirHarassEvaluator.ExitReason.NO_TARGET;
            assertEquals(probeEnded, AirHarassScouting.proberRegroups(reason, false), reason.name());
            assertFalse(AirHarassScouting.proberRegroups(reason, true), reason.name());
        }
    }

    @Test
    void aFlockDefendedProbeExitDoesNotSendTheProberBackToAHoldPointInsideTurrets() {
        AirHarassTargeting.AirThreat turret = threat(259, UnitType.Terran_Missile_Turret, TURRET);
        Position hold = new Position(2016, 3900);

        assertTrue(AirHarassScouting.holdExposed(Collections.singletonList(turret), hold));
        assertFalse(AirHarassScouting.proberRegroups(AirHarassEvaluator.ExitReason.FLOCK_DEFENDED,
                AirHarassScouting.holdExposed(Collections.singletonList(turret), hold)));
        assertFalse(AirHarassScouting.proberRegroups(AirHarassEvaluator.ExitReason.PROBE_DEFENDED,
                AirHarassScouting.holdExposed(Collections.singletonList(turret), hold)));
    }

    @Test
    void aRefusedBaseIsLeftOutUntilItsRefusalRunsOut() {
        Map<String, Integer> refusedUntil = new HashMap<>();
        refusedUntil.put("main", NOW + AirHarassScouting.PROBE_REFUSAL_FRAMES);
        List<String> bases = Arrays.asList("main", "natural");

        assertEquals(Collections.singletonList("natural"), AirHarassScouting.unrefused(bases, refusedUntil, NOW));
        assertEquals(Collections.singletonList("natural"), AirHarassScouting.unrefused(bases, refusedUntil,
                NOW + AirHarassScouting.PROBE_REFUSAL_FRAMES));
        assertEquals(bases, AirHarassScouting.unrefused(bases, refusedUntil,
                NOW + AirHarassScouting.PROBE_REFUSAL_FRAMES + 1));
        assertEquals(bases, AirHarassScouting.unrefused(bases, new HashMap<>(), NOW));
    }
}
