package unit.squad;

import bwapi.Position;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirHarassScoutingTest {

    private static final int NOW = 10296;
    private static final Position BASE = new Position(2112, 3824);
    private static final Position STRIKE = new Position(2096, 3664);
    private static final Position TURRET = new Position(2016, 3680);
    private static final Position FAR = new Position(3500, 1000);

    private static AirHarassTargeting.AirThreat threat(int id, UnitType type, Position position) {
        return AirHarassTargeting.AirThreat.of(id, type, position,
                AirHarassTargeting.airRange(type, weapon -> weapon.maxRange()));
    }

    private static AirHarassTargeting.AirThreat reaction(List<AirHarassTargeting.AirThreat> news,
                                                         List<AirHarassTargeting.AirThreat> known, Position base,
                                                         Position flock, double tolerance) {
        return AirHarassScouting.antiAirReaction(news, known, base, flock, tolerance);
    }

    @Test
    void aBaseNeverSightedIsAsOldAsTheGame() {
        assertEquals(NOW, AirHarassScouting.sightingAge(-1, NOW));
        assertEquals(720, AirHarassScouting.sightingAge(NOW - 720, NOW));
        assertEquals(0, AirHarassScouting.sightingAge(NOW, NOW));
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
    void aTurretFirstSeenInsideTheHarassZoneEndsTheHarassOfAFiveMutaFlock() {
        AirHarassTargeting.AirThreat turret = threat(259, UnitType.Terran_Missile_Turret, TURRET);
        List<AirHarassTargeting.AirThreat> threats = Collections.singletonList(turret);

        assertSame(turret, reaction(threats, threats, BASE, new Position(2099, 3314), AirHarassEvaluator.tolerance(5)));
    }

    @Test
    void aTurretKnownBeforeTheHarassIsNotNew() {
        AirHarassTargeting.AirThreat turret = threat(259, UnitType.Terran_Missile_Turret, TURRET);

        assertNull(reaction(Collections.emptyList(), Collections.singletonList(turret), BASE, STRIKE,
                AirHarassEvaluator.tolerance(5)));
    }

    @Test
    void aNewTurretOutsideTheZoneAndAwayFromTheFlockDoesNotEndTheHarass() {
        List<AirHarassTargeting.AirThreat> threats = Collections.singletonList(
                threat(300, UnitType.Terran_Missile_Turret, FAR));

        assertNull(reaction(threats, threats, BASE, STRIKE, AirHarassEvaluator.tolerance(5)));
    }

    @Test
    void aNewTurretAwayFromTheBaseButCoveringTheFlockEndsTheHarass() {
        Position flock = new Position(3500, 1100);
        AirHarassTargeting.AirThreat turret = threat(300, UnitType.Terran_Missile_Turret, FAR);
        List<AirHarassTargeting.AirThreat> threats = Collections.singletonList(turret);

        assertSame(turret, reaction(threats, threats, BASE, flock, AirHarassEvaluator.tolerance(5)));
    }

    @Test
    void aNewTurretCoveringTheFlockEndsTheHarassAfterTheTargetBaseIsGone() {
        Position flock = new Position(3500, 1100);
        AirHarassTargeting.AirThreat turret = threat(300, UnitType.Terran_Missile_Turret, FAR);
        List<AirHarassTargeting.AirThreat> threats = Collections.singletonList(turret);

        assertSame(turret, reaction(threats, threats, null, flock, AirHarassEvaluator.tolerance(5)));
        assertNull(reaction(threats, threats, null, STRIKE, AirHarassEvaluator.tolerance(5)));
    }

    @Test
    void aNewTurretTheFlockToleratesDoesNotEndTheHarass() {
        AirHarassTargeting.AirThreat turret = threat(259, UnitType.Terran_Missile_Turret, TURRET);
        List<AirHarassTargeting.AirThreat> threats = Collections.singletonList(turret);

        assertNull(reaction(threats, threats, BASE, STRIKE, turret.getStrength()));
    }

    @Test
    void aValkyrieFirstSeenAtTheStrikePointEndsTheHarass() {
        AirHarassTargeting.AirThreat valkyrie = threat(310, UnitType.Terran_Valkyrie, TURRET);
        List<AirHarassTargeting.AirThreat> threats = Collections.singletonList(valkyrie);

        assertSame(valkyrie, reaction(threats, threats, BASE, STRIKE, valkyrie.getStrength() / 2));
    }

    @Test
    void aGroupOfGoliathsEndsTheHarassWithTheGoliathThatBringsItOverTheTolerance() {
        AirHarassTargeting.AirThreat first = threat(301, UnitType.Terran_Goliath, TURRET);
        AirHarassTargeting.AirThreat second = threat(302, UnitType.Terran_Goliath, new Position(2040, 3690));
        double tolerance = first.getStrength() * 1.5;

        assertNull(reaction(Collections.singletonList(first), Collections.singletonList(first), BASE, STRIKE,
                tolerance));
        assertSame(second, reaction(Collections.singletonList(second), Arrays.asList(first, second), BASE, STRIKE,
                tolerance));
    }

    @Test
    void aNewMobileUnitFarFromTheBaseAndTheFlockDoesNotEndTheHarass() {
        List<AirHarassTargeting.AirThreat> threats = Collections.singletonList(
                threat(301, UnitType.Terran_Goliath, FAR));

        assertNull(reaction(threats, threats, BASE, STRIKE, 0));
    }

    @Test
    void theContributorsOfADefenseAreTheThreatsCoveringTheTriggerAndNoOthers() {
        AirHarassTargeting.AirThreat first = threat(301, UnitType.Terran_Goliath, TURRET);
        AirHarassTargeting.AirThreat second = threat(302, UnitType.Terran_Goliath, new Position(2040, 3690));
        AirHarassTargeting.AirThreat far = threat(303, UnitType.Terran_Missile_Turret, FAR);

        assertEquals(Arrays.asList(301, 302),
                AirHarassScouting.contributors(second, Arrays.asList(first, second, far)));
    }

    @Test
    void anAntiAirThreatIsNewOnlyTheFirstTimeItIsLearned() {
        AirHarassState state = new AirHarassState(NOW, 600);
        AirHarassTargeting.AirThreat turret = threat(259, UnitType.Terran_Missile_Turret, TURRET);
        AirHarassTargeting.AirThreat other = threat(252, UnitType.Terran_Missile_Turret, new Position(2208, 3744));

        assertEquals(1, state.learnAntiAir(Collections.singletonList(turret), NOW, 600).size());
        List<AirHarassTargeting.AirThreat> fresh = state.learnAntiAir(Arrays.asList(turret, other), NOW + 5, 580);

        assertEquals(1, fresh.size());
        assertEquals(252, fresh.get(0).getId());
        assertTrue(state.learnAntiAir(Arrays.asList(turret, other), NOW + 9, 560).isEmpty());
    }

    @Test
    void aSightingKeepsTheFrameAndTheFlockHitPointsOfTheFirstTimeItWasSeen() {
        AirHarassState state = new AirHarassState(NOW, 600);
        AirHarassTargeting.AirThreat first = threat(301, UnitType.Terran_Goliath, TURRET);
        AirHarassTargeting.AirThreat second = threat(302, UnitType.Terran_Goliath, new Position(2040, 3690));
        state.learnAntiAir(Collections.singletonList(first), NOW, 600);
        state.learnAntiAir(Arrays.asList(first, second), NOW + 30, 540);
        state.learnAntiAir(Arrays.asList(first, second), NOW + 60, 500);

        AirHarassState.AntiAirSighting earliest = state.earliestSighting(Arrays.asList(302, 301));

        assertEquals(NOW, earliest.getFrame());
        assertEquals(600, earliest.getFlockHitPoints());
        assertEquals(NOW + 30, state.earliestSighting(Collections.singletonList(302)).getFrame());
        assertNull(state.earliestSighting(Collections.singletonList(999)));
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
        AirHarassTargeting.AirThreat turret = threat(300, UnitType.Terran_Missile_Turret, FAR);

        assertNull(AirHarassScouting.sharedExitPoint(STRIKE, Collections.singletonList(turret)));
        assertNull(AirHarassScouting.sharedExitPoint(STRIKE, Collections.emptyList()));
        assertNull(AirHarassScouting.sharedExitPoint(null, Collections.singletonList(turret)));
    }

    @Test
    void aKnownTurretOverACorePointIsCoveredButAMobileUnitOrAFarTurretIsNot() {
        AirHarassTargeting.AirThreat turret = threat(259, UnitType.Terran_Missile_Turret, TURRET);
        AirHarassTargeting.AirThreat farTurret = threat(300, UnitType.Terran_Missile_Turret, FAR);
        AirHarassTargeting.AirThreat goliath = threat(301, UnitType.Terran_Goliath, new Position(2064, 3700));
        Position corePoint = new Position(2064, 3700);

        assertTrue(AirHarassScouting.knownAntiAirCovers(Collections.singletonList(turret), corePoint));
        assertFalse(AirHarassScouting.knownAntiAirCovers(Arrays.asList(farTurret, goliath), corePoint));
    }

    @Test
    void resourcesFartherThanASightRangeFromTheMidpointPullTheCorePointToThem() {
        int sight = UnitType.Zerg_Mutalisk.sightRange();
        Position resources = new Position(BASE.getX(), BASE.getY() - 4 * sight);

        Position core = AirHarassScouting.corePoint(BASE, resources, sight);

        assertEquals(sight - 32, core.getDistance(resources), 2);
        assertEquals(BASE.getX(), core.getX());
        assertEquals(BASE, AirHarassScouting.corePoint(BASE, null, sight));
    }

    @Test
    void onlyNewAntiAirRefusesTheBase() {
        assertTrue(AirHarassScouting.refusesBase(AirHarassEvaluator.ExitReason.NEW_AA));
        for (AirHarassEvaluator.ExitReason reason : AirHarassEvaluator.ExitReason.values()) {
            if (reason != AirHarassEvaluator.ExitReason.NEW_AA) {
                assertFalse(AirHarassScouting.refusesBase(reason), reason.name());
            }
        }
        assertFalse(AirHarassScouting.refusesBase(null));
    }

    @Test
    void newAntiAirAtTheTargetHoldsTheBaseForTheFullRefusalAndAtTheFlockForTheReentryHold() {
        AirHarassEvaluator.ExitReason newAa = AirHarassEvaluator.ExitReason.NEW_AA;

        assertEquals(AirHarassScouting.DEFENDED_REFUSAL_FRAMES, AirHarassScouting.holdFrames(newAa, true));
        assertEquals(AirHarassEvaluator.REENTRY_HOLD_FRAMES, AirHarassScouting.holdFrames(newAa, false));
        for (AirHarassEvaluator.ExitReason reason : AirHarassEvaluator.ExitReason.values()) {
            if (reason != newAa) {
                assertEquals(0, AirHarassScouting.holdFrames(reason, true), reason.name());
                assertEquals(0, AirHarassScouting.holdFrames(reason, false), reason.name());
            }
        }
    }

    @Test
    void aSecondFlockSideExitFromTheSameBaseWithinTheCarryWindowHoldsItForTheFullRefusal() {
        AirHarassEvaluator.ExitReason newAa = AirHarassEvaluator.ExitReason.NEW_AA;
        Map<String, Integer> lastFlockSideExit = new HashMap<>();

        int first = AirHarassScouting.holdFrames(newAa, false, lastFlockSideExit.get("main"), NOW);
        AirHarassScouting.noteFlockSideExit(lastFlockSideExit, "main", newAa, false, first, NOW);
        int second = AirHarassScouting.holdFrames(newAa, false, lastFlockSideExit.get("main"),
                NOW + AirHarassEvaluator.REENTRY_HOLD_FRAMES + 120);

        assertEquals(AirHarassEvaluator.REENTRY_HOLD_FRAMES, first);
        assertEquals(AirHarassScouting.DEFENDED_REFUSAL_FRAMES, second);
    }

    @Test
    void threeFlockSideExitsEscalateOnTheSecondAndStartAgainOnTheThird() {
        AirHarassEvaluator.ExitReason newAa = AirHarassEvaluator.ExitReason.NEW_AA;
        Map<String, Integer> lastFlockSideExit = new HashMap<>();
        int[] frames = {NOW, NOW + 600, NOW + 1200};
        int[] holds = new int[3];

        for (int i = 0; i < 3; i++) {
            holds[i] = AirHarassScouting.holdFrames(newAa, false, lastFlockSideExit.get("main"), frames[i]);
            AirHarassScouting.noteFlockSideExit(lastFlockSideExit, "main", newAa, false, holds[i], frames[i]);
        }

        assertEquals(AirHarassEvaluator.REENTRY_HOLD_FRAMES, holds[0]);
        assertEquals(AirHarassScouting.DEFENDED_REFUSAL_FRAMES, holds[1]);
        assertEquals(AirHarassEvaluator.REENTRY_HOLD_FRAMES, holds[2]);
    }

    @Test
    void aFlockSideExitAfterTheCarryWindowStartsTheEscalationAgain() {
        AirHarassEvaluator.ExitReason newAa = AirHarassEvaluator.ExitReason.NEW_AA;
        Map<String, Integer> lastFlockSideExit = new HashMap<>();
        lastFlockSideExit.put("main", NOW);

        int late = AirHarassScouting.holdFrames(newAa, false, lastFlockSideExit.get("main"),
                NOW + AirHarassScouting.ESCALATION_FRAMES + 1);
        int onTime = AirHarassScouting.holdFrames(newAa, false, lastFlockSideExit.get("main"),
                NOW + AirHarassScouting.ESCALATION_FRAMES);

        assertEquals(AirHarassEvaluator.REENTRY_HOLD_FRAMES, late);
        assertEquals(AirHarassScouting.DEFENDED_REFUSAL_FRAMES, onTime);
    }

    @Test
    void anEscalatedHoldForgetsTheEarlierExitAndAnotherBaseIsNotEscalated() {
        AirHarassEvaluator.ExitReason newAa = AirHarassEvaluator.ExitReason.NEW_AA;
        Map<String, Integer> lastFlockSideExit = new HashMap<>();
        lastFlockSideExit.put("main", NOW);

        AirHarassScouting.noteFlockSideExit(lastFlockSideExit, "main", newAa, false,
                AirHarassScouting.DEFENDED_REFUSAL_FRAMES, NOW + 600);

        assertFalse(lastFlockSideExit.containsKey("main"));
        assertEquals(AirHarassEvaluator.REENTRY_HOLD_FRAMES,
                AirHarassScouting.holdFrames(newAa, false, lastFlockSideExit.get("natural"), NOW + 600));
    }

    @Test
    void anExitAtTheTargetOrForAnotherReasonIsNotRecordedAsAFlockSideExit() {
        Map<String, Integer> lastFlockSideExit = new HashMap<>();

        AirHarassScouting.noteFlockSideExit(lastFlockSideExit, "main", AirHarassEvaluator.ExitReason.NEW_AA, true,
                AirHarassScouting.DEFENDED_REFUSAL_FRAMES, NOW);
        AirHarassScouting.noteFlockSideExit(lastFlockSideExit, "main", AirHarassEvaluator.ExitReason.NO_TARGET, false,
                0, NOW);

        assertTrue(lastFlockSideExit.isEmpty());
        assertEquals(AirHarassScouting.DEFENDED_REFUSAL_FRAMES,
                AirHarassScouting.holdFrames(AirHarassEvaluator.ExitReason.NEW_AA, true, NOW, NOW + 10));
        assertEquals(0, AirHarassScouting.holdFrames(AirHarassEvaluator.ExitReason.NO_TARGET, false, NOW, NOW + 10));
    }

    @Test
    void aFlockThatLeftOnFlockSideAntiAirDoesNotReenterTheSameBaseWithinTheHold() {
        Map<String, Integer> refusedUntil = new HashMap<>();
        refusedUntil.put("main", NOW + AirHarassScouting.holdFrames(AirHarassEvaluator.ExitReason.NEW_AA, false));
        List<String> bases = Arrays.asList("main", "natural");

        assertEquals(Collections.singletonList("natural"), AirHarassScouting.unrefused(bases, refusedUntil,
                NOW + AirHarassEvaluator.REENTRY_HOLD_FRAMES));
        assertEquals(bases, AirHarassScouting.unrefused(bases, refusedUntil,
                NOW + AirHarassEvaluator.REENTRY_HOLD_FRAMES + 1));
    }

    @Test
    void aReactionAtTheFlockNamesTheTriggerAndTheIdsOfTheDefenseThatEndedTheHarass() {
        AirHarassState state = new AirHarassState(NOW, 600);
        AirHarassTargeting.AirThreat goliath = threat(301, UnitType.Terran_Goliath, FAR);
        double tolerance = goliath.getStrength() / 2;

        AirHarassScouting.Reaction reaction = AirHarassScouting.react(state, Collections.singletonList(goliath),
                BASE, FAR, tolerance, NOW, 600);

        assertSame(goliath, reaction.getTrigger());
        assertFalse(reaction.isAtTarget());
        assertEquals(Collections.singletonList(301), reaction.getContributorIds());
    }

    @Test
    void anAntiAirUnitCarriedFromTheLastExitIsAcceptedByTheNextHarassSoItIsNeverNewAgain() {
        AirHarassTargeting.AirThreat goliath = threat(301, UnitType.Terran_Goliath, FAR);
        double tolerance = goliath.getStrength() / 2;
        List<AirHarassTargeting.AirThreat> threats = Collections.singletonList(goliath);
        Map<Integer, Integer> carriedAt = new HashMap<>();
        carriedAt.put(301, NOW);
        int next = NOW + AirHarassEvaluator.REENTRY_HOLD_FRAMES + 1;

        AirHarassState state = new AirHarassState(next, 600);
        state.acceptIds(AirHarassScouting.carried(carriedAt, next), next, 600);

        assertNull(AirHarassScouting.react(state, threats, BASE, FAR, tolerance, next + 40, 600));
    }

    @Test
    void aCarriedUnitOutOfSightWhenTheNextHarassStartsIsStillNotNewWhenItIsSeenAgain() {
        AirHarassTargeting.AirThreat goliath = threat(301, UnitType.Terran_Goliath, FAR);
        double tolerance = goliath.getStrength() / 2;
        Map<Integer, Integer> carriedAt = new HashMap<>();
        carriedAt.put(301, NOW);
        int next = NOW + AirHarassEvaluator.REENTRY_HOLD_FRAMES + 1;
        AirHarassState state = new AirHarassState(next, 600);
        state.acceptIds(AirHarassScouting.carried(carriedAt, next), next, 600);

        assertNull(AirHarassScouting.react(state, Collections.emptyList(), BASE, FAR, tolerance, next + 1, 600));
        assertNull(AirHarassScouting.react(state, Collections.singletonList(goliath), BASE, FAR, tolerance,
                next + 30, 600));
    }

    @Test
    void aShorterHoldNeverCutsALongerRefusalShort() {
        Map<String, Integer> refusedUntil = new HashMap<>();
        AirHarassScouting.hold(refusedUntil, "main", NOW + AirHarassScouting.DEFENDED_REFUSAL_FRAMES);
        AirHarassScouting.hold(refusedUntil, "main", NOW + AirHarassEvaluator.REENTRY_HOLD_FRAMES);

        assertEquals(NOW + AirHarassScouting.DEFENDED_REFUSAL_FRAMES, (int) refusedUntil.get("main"));
        AirHarassScouting.hold(refusedUntil, "main", NOW + AirHarassScouting.DEFENDED_REFUSAL_FRAMES + 5);
        assertEquals(NOW + AirHarassScouting.DEFENDED_REFUSAL_FRAMES + 5, (int) refusedUntil.get("main"));
    }

    @Test
    void carriedAntiAirExpires() {
        Map<Integer, Integer> carriedAt = new HashMap<>();
        carriedAt.put(301, NOW);

        assertEquals(Collections.singletonList(301), AirHarassScouting.carried(carriedAt,
                NOW + AirHarassScouting.CARRY_FRAMES));
        assertTrue(AirHarassScouting.carried(carriedAt, NOW + AirHarassScouting.CARRY_FRAMES + 1).isEmpty());
        assertTrue(carriedAt.isEmpty());
    }

    @Test
    void aRefusedBaseIsLeftOutUntilItsRefusalRunsOut() {
        Map<String, Integer> refusedUntil = new HashMap<>();
        refusedUntil.put("main", NOW + AirHarassScouting.DEFENDED_REFUSAL_FRAMES);
        List<String> bases = Arrays.asList("main", "natural");

        assertEquals(Collections.singletonList("natural"), AirHarassScouting.unrefused(bases, refusedUntil, NOW));
        assertEquals(Collections.singletonList("natural"), AirHarassScouting.unrefused(bases, refusedUntil,
                NOW + AirHarassScouting.DEFENDED_REFUSAL_FRAMES));
        assertEquals(bases, AirHarassScouting.unrefused(bases, refusedUntil,
                NOW + AirHarassScouting.DEFENDED_REFUSAL_FRAMES + 1));
        assertEquals(bases, AirHarassScouting.unrefused(bases, new HashMap<>(), NOW));
    }

    @Test
    void aValkyrieAppearingAtTheStrikePointTurnsTheFlockOnTheFrameItIsSeen() {
        AirHarassState state = new AirHarassState(NOW, 600);
        AirHarassTargeting.AirThreat valkyrie = threat(310, UnitType.Terran_Valkyrie, TURRET);
        double tolerance = valkyrie.getStrength() / 2;
        List<AirHarassTargeting.AirThreat> none = Collections.emptyList();

        assertNull(AirHarassScouting.react(state, none, BASE, STRIKE, tolerance, NOW, 600));
        assertNull(AirHarassScouting.react(state, none, BASE, STRIKE, tolerance, NOW + 1, 600));
        AirHarassScouting.Reaction reaction = AirHarassScouting.react(state, Collections.singletonList(valkyrie),
                BASE, STRIKE, tolerance, NOW + 2, 590);

        assertSame(valkyrie, reaction.getTrigger());
        assertEquals(NOW + 2, reaction.getSeenFrame());
        assertEquals(NOW + 2, reaction.getTurnFrame());
        assertEquals(0, reaction.getHitPointsLost());
        assertTrue(reaction.isAtTarget());
    }

    @Test
    void aGoliathSeenFarAwayFirstStillTurnsTheFlockWhenItArrivesAtTheTarget() {
        AirHarassState state = new AirHarassState(NOW, 600);
        AirHarassTargeting.AirThreat away = threat(301, UnitType.Terran_Goliath, FAR);
        AirHarassTargeting.AirThreat arrived = threat(301, UnitType.Terran_Goliath, TURRET);
        double tolerance = away.getStrength() / 2;

        assertNull(AirHarassScouting.react(state, Collections.singletonList(away), BASE, STRIKE, tolerance, NOW, 600));
        assertNull(AirHarassScouting.react(state, Collections.singletonList(away), BASE, STRIKE, tolerance,
                NOW + 20, 600));
        AirHarassScouting.Reaction reaction = AirHarassScouting.react(state, Collections.singletonList(arrived),
                BASE, STRIKE, tolerance, NOW + 40, 600);

        assertSame(arrived, reaction.getTrigger());
        assertEquals(NOW + 40, reaction.getTurnFrame());
    }

    @Test
    void aGoliathGroupMeasuresTheReactionFromTheFirstGoliathAndTheHitPointsLostSince() {
        AirHarassState state = new AirHarassState(NOW, 600);
        AirHarassTargeting.AirThreat first = threat(301, UnitType.Terran_Goliath, TURRET);
        AirHarassTargeting.AirThreat second = threat(302, UnitType.Terran_Goliath, new Position(2040, 3690));
        double tolerance = first.getStrength() * 1.5;

        assertNull(AirHarassScouting.react(state, Collections.singletonList(first), BASE, STRIKE, tolerance,
                NOW + 10, 600));
        AirHarassScouting.Reaction reaction = AirHarassScouting.react(state, Arrays.asList(first, second), BASE,
                STRIKE, tolerance, NOW + 34, 552);

        assertSame(second, reaction.getTrigger());
        assertEquals(NOW + 10, reaction.getSeenFrame());
        assertEquals(NOW + 34, reaction.getTurnFrame());
        assertEquals(48, reaction.getHitPointsLost());
    }

    @Test
    void antiAirAcceptedAtTheStartIsNeverNewAndNeverStartsAReactionWindow() {
        AirHarassState state = new AirHarassState(NOW, 600);
        AirHarassTargeting.AirThreat known = threat(301, UnitType.Terran_Goliath, TURRET);
        AirHarassTargeting.AirThreat arriving = threat(302, UnitType.Terran_Goliath, new Position(2040, 3690));
        double tolerance = known.getStrength() * 1.5;
        state.acceptAntiAir(AirHarassScouting.inReach(Collections.singletonList(known), BASE, STRIKE), NOW, 600);

        assertNull(AirHarassScouting.react(state, Collections.singletonList(known), BASE, STRIKE, tolerance,
                NOW + 5, 600));
        AirHarassScouting.Reaction reaction = AirHarassScouting.react(state, Arrays.asList(known, arriving), BASE,
                STRIKE, tolerance, NOW + 90, 480);

        assertSame(arriving, reaction.getTrigger());
        assertEquals(NOW + 90, reaction.getSeenFrame());
        assertEquals(0, reaction.getHitPointsLost());
    }

    @Test
    void aTriggerCoveringOnlyTheFlockIsNotAtTheTarget() {
        AirHarassState state = new AirHarassState(NOW, 600);
        Position flock = new Position(3500, 1100);
        AirHarassTargeting.AirThreat turret = threat(300, UnitType.Terran_Missile_Turret, FAR);

        AirHarassScouting.Reaction reaction = AirHarassScouting.react(state, Collections.singletonList(turret),
                BASE, flock, AirHarassEvaluator.tolerance(5), NOW, 600);

        assertSame(turret, reaction.getTrigger());
        assertFalse(reaction.isAtTarget());
    }

    @Test
    void anInterceptorIsNotAnAntiAirReaction() {
        AirHarassState state = new AirHarassState(NOW, 600);
        AirHarassTargeting.AirThreat interceptor = threat(400, UnitType.Protoss_Interceptor, TURRET);

        assertTrue(AirHarassScouting.inReach(Collections.singletonList(interceptor), BASE, STRIKE).isEmpty());
        assertNull(AirHarassScouting.react(state, Collections.singletonList(interceptor), BASE, STRIKE, 0, NOW, 600));
    }
}
