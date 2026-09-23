package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import info.map.BaseArea;
import org.junit.jupiter.api.Test;
import util.StaticDefenseZone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.RunbyTargeting.Kind;

class RunbyTargetingTest {

    private static final int NOW = 9000;
    private static final Position LING_AT = new Position(1008, 1008);
    private static final Position SEEK = new Position(1400, 1008);
    private static final int ZEALOT_REACH = RunbyTargeting.reach(UnitType.Protoss_Zealot);
    private static final int LING_REACH = RunbyTargeting.reach(UnitType.Zerg_Zergling);

    private static RunbyTargeting.Ling ling() {
        return new RunbyTargeting.Ling(1, LING_AT, LING_REACH);
    }

    private static Position east(int pixels) {
        return new Position(LING_AT.getX() + pixels, LING_AT.getY());
    }

    private static RunbyTargeting.Contact probe(int id, Position position, double hpFraction) {
        return new RunbyTargeting.Contact(id, UnitType.Protoss_Probe, position, hpFraction, false);
    }

    private static RunbyTargeting.Contact meanProbe(int id, Position position) {
        return new RunbyTargeting.Contact(id, UnitType.Protoss_Probe, position, 1.0, true);
    }

    private static RunbyTargeting.Contact zealot(int id, Position position) {
        return new RunbyTargeting.Contact(id, UnitType.Protoss_Zealot, position, 1.0, false);
    }

    private static RunbyTargeting.Contact pylon(int id, Position position) {
        return new RunbyTargeting.Contact(id, UnitType.Protoss_Pylon, position, 1.0, false);
    }

    private static RunbyTargeting.Threat zealotThreat(Position position) {
        return RunbyTargeting.Threat.of(UnitType.Protoss_Zealot, position);
    }

    private static RunbyTargeting.Situation.SituationBuilder harass(boolean winnable) {
        return RunbyTargeting.Situation.builder()
                .phase(RunbyState.Phase.HARASS)
                .winnable(winnable)
                .seekPoint(SEEK)
                .now(NOW);
    }

    private static RunbyTargeting.Situation.SituationBuilder penetrate() {
        return RunbyTargeting.Situation.builder()
                .phase(RunbyState.Phase.PENETRATE)
                .winnable(true)
                .seekPoint(SEEK)
                .now(NOW);
    }

    private static RunbyTargeting.Decision choose(RunbyTargeting.Situation situation) {
        return RunbyTargeting.choose(ling(), situation, new RunbyTargeting.LingMemory());
    }

    @Test
    void aProbeInsideAZealotsReachLosesToEvade() {
        Position zealotAt = east(ZEALOT_REACH / 2);
        RunbyTargeting.Situation situation = harass(false)
                .contacts(Arrays.asList(probe(10, east(ZEALOT_REACH / 3), 0.1), zealot(20, zealotAt)))
                .threats(Collections.singletonList(zealotThreat(zealotAt)))
                .build();

        assertEquals(Kind.EVADE, choose(situation).getKind());
    }

    @Test
    void aProbeCoveredByAZealotIsNotAWorkerTarget() {
        Position zealotAt = east(ZEALOT_REACH + 200);
        RunbyTargeting.Situation situation = harass(false)
                .contacts(Arrays.asList(probe(10, east(200), 1.0), zealot(20, zealotAt)))
                .threats(Collections.singletonList(zealotThreat(zealotAt)))
                .build();

        RunbyTargeting.Decision decision = choose(situation);

        assertEquals(Kind.SEEK, decision.getKind());
    }

    @Test
    void aProbeOutsideEveryReachBeatsAZealotEvenInAWinnableFight() {
        Position zealotAt = east(ZEALOT_REACH + 300);
        RunbyTargeting.Situation situation = harass(true)
                .contacts(Arrays.asList(zealot(20, zealotAt), probe(10, east(40), 1.0)))
                .threats(Collections.singletonList(zealotThreat(zealotAt)))
                .build();

        RunbyTargeting.Decision decision = choose(situation);

        assertEquals(Kind.WORKER, decision.getKind());
        assertEquals(10, decision.getTargetId());
    }

    @Test
    void anOffBaseSafeWorkerIsNeverChosenEvenWithNoWorkerInTheBase() {
        Position offBase = east(200);
        RunbyTargeting.Situation situation = harass(false)
                .contacts(Arrays.asList(probe(10, offBase, 0.1), meanProbe(11, east(20))))
                .workerAllowed(point -> point.getX() < LING_AT.getX() + 10)
                .build();

        RunbyTargeting.Decision decision = choose(situation);

        assertEquals(Kind.SEEK, decision.getKind());
        assertEquals(SEEK, decision.getPoint());
    }

    @Test
    void anInBaseWorkerIsChosenOverAnOffBaseOne() {
        RunbyTargeting.Situation situation = harass(false)
                .contacts(Arrays.asList(probe(10, east(20), 0.1), probe(11, new Position(900, 1008), 1.0)))
                .workerAllowed(point -> point.getX() < LING_AT.getX())
                .build();

        assertEquals(11, choose(situation).getTargetId());
    }

    @Test
    void aWorkerTargetThatLeavesTheBaseIsDropped() {
        RunbyTargeting.LingMemory memory = new RunbyTargeting.LingMemory();
        RunbyTargeting.Situation inside = harass(false)
                .contacts(Collections.singletonList(probe(10, east(100), 1.0)))
                .workerAllowed(point -> point.getX() < LING_AT.getX() + 150)
                .build();
        assertEquals(10, RunbyTargeting.choose(ling(), inside, memory).getTargetId());

        RunbyTargeting.Situation left = harass(false)
                .contacts(Collections.singletonList(probe(10, east(200), 1.0)))
                .workerAllowed(point -> point.getX() < LING_AT.getX() + 150)
                .build();
        RunbyTargeting.Decision decision = RunbyTargeting.choose(ling(), left, memory);

        assertEquals(Kind.SEEK, decision.getKind());
        assertEquals(-1, memory.getTargetId());
    }

    @Test
    void aTargetThatLeavesTheBaseLosesItsStickinessToAnInBaseWorker() {
        RunbyTargeting.Situation left = harass(false)
                .contacts(Arrays.asList(probe(10, east(200), 1.0), probe(11, east(100), 1.0)))
                .workerAllowed(point -> point.getX() < LING_AT.getX() + 150)
                .build();

        assertTrue(RunbyTargeting.targetLeftWorkerArea(10, left));
        assertEquals(false, RunbyTargeting.targetLeftWorkerArea(11, left));
        assertEquals(false, RunbyTargeting.targetLeftWorkerArea(-1, left));
    }

    @Test
    void aMeanWorkerInReachBeatsAMiningWorker() {
        RunbyTargeting.Situation situation = harass(false)
                .contacts(Arrays.asList(probe(10, east(20), 0.1), meanProbe(11, east(LING_REACH / 2))))
                .build();

        RunbyTargeting.Decision decision = choose(situation);

        assertEquals(Kind.WORKER, decision.getKind());
        assertEquals(11, decision.getTargetId());
    }

    @Test
    void safeWorkersAreTakenLowestHitPointsFirstThenNearest() {
        RunbyTargeting.Situation situation = harass(false)
                .contacts(Arrays.asList(probe(10, east(20), 1.0), probe(11, east(120), 0.4),
                        probe(12, east(60), 0.4)))
                .build();

        assertEquals(12, choose(situation).getTargetId());
    }

    @Test
    void theCurrentWorkerTargetIsSticky() {
        RunbyTargeting.Situation situation = harass(false)
                .contacts(Arrays.asList(probe(10, east(100), 1.0), probe(11, east(110), 1.0)))
                .build();
        RunbyTargeting.LingMemory memory = new RunbyTargeting.LingMemory();

        assertEquals(10, RunbyTargeting.choose(ling(), situation, memory).getTargetId());

        RunbyTargeting.LingMemory onEleven = new RunbyTargeting.LingMemory();
        RunbyTargeting.Situation elevenFirst = harass(false)
                .contacts(Arrays.asList(probe(11, east(110), 1.0)))
                .build();
        RunbyTargeting.choose(ling(), elevenFirst, onEleven);

        assertEquals(11, RunbyTargeting.choose(ling(), situation, onEleven).getTargetId());
    }

    @Test
    void aNonWorkerIsTakenOnlyWhenTheFightIsWinnable() {
        Position zealotAt = east(ZEALOT_REACH + 200);
        List<RunbyTargeting.Contact> contacts = Collections.singletonList(zealot(20, zealotAt));
        List<RunbyTargeting.Threat> threats = Collections.singletonList(zealotThreat(zealotAt));

        assertEquals(Kind.SEEK, choose(harass(false).contacts(contacts).threats(threats).build()).getKind());
        assertEquals(Kind.FIGHT, choose(harass(true).contacts(contacts).threats(threats).build()).getKind());
    }

    @Test
    void aWinnableFightDoesNotEvade() {
        Position zealotAt = east(ZEALOT_REACH / 2);
        RunbyTargeting.Situation situation = harass(true)
                .contacts(Collections.singletonList(zealot(20, zealotAt)))
                .threats(Collections.singletonList(zealotThreat(zealotAt)))
                .build();

        assertEquals(Kind.FIGHT, choose(situation).getKind());
    }

    @Test
    void aBuildingIsTakenOnlyWhenNoThreatCoversIt() {
        Position pylonAt = east(150);
        Position coveringZealot = east(150 + ZEALOT_REACH / 2);
        Position awayZealot = new Position(3000, 3000);

        RunbyTargeting.Situation covered = harass(false)
                .contacts(Arrays.asList(pylon(30, pylonAt), zealot(20, coveringZealot)))
                .threats(Collections.singletonList(zealotThreat(coveringZealot)))
                .build();
        RunbyTargeting.Situation uncovered = harass(false)
                .contacts(Arrays.asList(pylon(30, pylonAt), zealot(20, awayZealot)))
                .threats(Collections.singletonList(zealotThreat(awayZealot)))
                .build();

        assertEquals(Kind.SEEK, choose(covered).getKind());
        RunbyTargeting.Decision decision = choose(uncovered);
        assertEquals(Kind.BUILDING, decision.getKind());
        assertEquals(30, decision.getTargetId());
    }

    @Test
    void aBuildingInsideAStaticDefenceZoneIsNotTaken() {
        Position pylonAt = east(150);
        StaticDefenseZone cannon = new StaticDefenseZone(UnitType.Protoss_Photon_Cannon, east(250),
                UnitType.Protoss_Photon_Cannon.groundWeapon().maxRange());
        RunbyTargeting.Situation situation = harass(false)
                .contacts(Collections.singletonList(pylon(30, pylonAt)))
                .zones(Collections.singletonList(cannon))
                .build();

        assertEquals(Kind.SEEK, choose(situation).getKind());
    }

    @Test
    void antiAirStaticDefenceDoesNotCoverABuilding() {
        Position pylonAt = east(150);
        for (UnitType antiAir : new UnitType[] {UnitType.Terran_Missile_Turret, UnitType.Zerg_Spore_Colony}) {
            StaticDefenseZone zone = new StaticDefenseZone(antiAir, east(200), antiAir.airWeapon().maxRange());
            RunbyTargeting.Situation situation = harass(false)
                    .contacts(Collections.singletonList(pylon(30, pylonAt)))
                    .zones(Collections.singletonList(zone))
                    .build();

            assertEquals(Kind.BUILDING, choose(situation).getKind(), antiAir.toString());
        }
    }

    @Test
    void groundStaticDefenceStillCoversABuilding() {
        Position pylonAt = east(150);
        StaticDefenseZone[] zones = {
            new StaticDefenseZone(UnitType.Zerg_Sunken_Colony, east(200),
                    UnitType.Zerg_Sunken_Colony.groundWeapon().maxRange()),
            new StaticDefenseZone(UnitType.Protoss_Photon_Cannon, east(200),
                    UnitType.Protoss_Photon_Cannon.groundWeapon().maxRange()),
            new StaticDefenseZone(UnitType.Terran_Bunker, east(200), UnitType.Terran_Marine.groundWeapon().maxRange())
        };
        for (StaticDefenseZone zone : zones) {
            RunbyTargeting.Situation situation = harass(false)
                    .contacts(Collections.singletonList(pylon(30, pylonAt)))
                    .zones(Collections.singletonList(zone))
                    .build();

            assertEquals(Kind.SEEK, choose(situation).getKind(), zone.getStructure().toString());
        }
    }

    @Test
    void aBuildingTargetIsStickyForItsWindow() {
        RunbyTargeting.LingMemory memory = new RunbyTargeting.LingMemory();
        RunbyTargeting.Situation farOnly = harass(false)
                .contacts(Collections.singletonList(pylon(30, east(300))))
                .build();
        RunbyTargeting.choose(ling(), farOnly, memory);

        RunbyTargeting.Situation bothSoon = harass(false)
                .contacts(Arrays.asList(pylon(30, east(300)), pylon(31, east(100))))
                .now(NOW + RunbyTargeting.BUILDING_STICKY_FRAMES - 1)
                .build();
        RunbyTargeting.Situation bothLater = harass(false)
                .contacts(Arrays.asList(pylon(30, east(300)), pylon(31, east(100))))
                .now(NOW + RunbyTargeting.BUILDING_STICKY_FRAMES)
                .build();

        assertEquals(30, RunbyTargeting.choose(ling(), bothSoon, memory).getTargetId());
        assertEquals(31, RunbyTargeting.choose(ling(), bothLater, memory).getTargetId());
    }

    @Test
    void withNothingToHitTheLingSeeksTheFirstUnvisitedSpot() {
        Position first = new Position(1200, 1200);
        Position second = new Position(1300, 1300);
        Set<Position> visited = new HashSet<>(Collections.singletonList(first));

        RunbyTargeting.Goal goal = RunbyTargeting.seekGoal(LING_AT, Collections.emptyList(), Collections.emptyList(),
                Arrays.asList(first, second), visited);
        RunbyTargeting.Decision decision = choose(harass(false).seekPoint(goal.getPoint()).build());

        assertEquals(RunbyState.GoalType.LIKELY, goal.getType());
        assertEquals(Kind.SEEK, decision.getKind());
        assertEquals(second, decision.getPoint());
    }

    @Test
    void theSeekGoalPrefersVisibleThenRecentWorkersThenLikelySpots() {
        List<Position> spots = Collections.singletonList(new Position(1200, 1200));
        List<Position> recent = Collections.singletonList(new Position(1100, 1100));
        List<Position> visible = Arrays.asList(new Position(2000, 2000), new Position(1050, 1050));

        assertEquals(RunbyState.GoalType.VISIBLE, RunbyTargeting.seekGoal(LING_AT, visible, recent, spots,
                new HashSet<>()).getType());
        assertEquals(new Position(1050, 1050), RunbyTargeting.seekGoal(LING_AT, visible, recent, spots,
                new HashSet<>()).getPoint());
        assertEquals(RunbyState.GoalType.LAST_SEEN, RunbyTargeting.seekGoal(LING_AT, Collections.emptyList(), recent,
                spots, new HashSet<>()).getType());
        assertEquals(RunbyState.GoalType.NONE, RunbyTargeting.seekGoal(LING_AT, Collections.emptyList(),
                Collections.emptyList(), spots, new HashSet<>(spots)).getType());
    }

    @Test
    void aSpotIsVisitedOnceReachedWithNoWorkerNearIt() {
        Position spot = new Position(1200, 1200);
        List<Position> spots = Collections.singletonList(spot);
        Set<Position> visited = new HashSet<>();

        RunbyTargeting.markVisited(spots, visited, Collections.singletonList(new Position(1200, 1400)),
                Collections.emptyList());
        assertTrue(visited.isEmpty());

        RunbyTargeting.markVisited(spots, visited, Collections.singletonList(new Position(1210, 1210)),
                Collections.singletonList(new Position(1250, 1250)));
        assertTrue(visited.isEmpty());

        RunbyTargeting.markVisited(spots, visited, Collections.singletonList(new Position(1210, 1210)),
                Collections.emptyList());
        assertTrue(visited.contains(spot));
    }

    @Test
    void anEvadePointOutsideTheBaseAreaIsNeverReturned() {
        BaseArea area = new BaseArea(LING_AT.toTilePosition(), null, Collections.emptyList(), 3, 0, tile -> null);
        Position zealotAt = new Position(LING_AT.getX() - 40, LING_AT.getY());
        RunbyTargeting.Situation situation = harass(false)
                .threats(Collections.singletonList(zealotThreat(zealotAt)))
                .evadeAllowed(point -> area.contains(point.toTilePosition()))
                .build();

        RunbyTargeting.Decision decision = choose(situation);

        assertEquals(Kind.EVADE, decision.getKind());
        assertTrue(area.contains(decision.getPoint().toTilePosition()));
    }

    @Test
    void aLingWithNowhereInsideTheBaseToGoDoesNotEvade() {
        BaseArea area = new BaseArea(LING_AT.toTilePosition(), null, Collections.emptyList(), 0, 0, tile -> null);
        Position zealotAt = new Position(LING_AT.getX() - 40, LING_AT.getY());
        RunbyTargeting.Situation situation = harass(false)
                .threats(Collections.singletonList(zealotThreat(zealotAt)))
                .evadeAllowed(point -> area.contains(point.toTilePosition()))
                .build();

        assertNotEquals(Kind.EVADE, choose(situation).getKind());
    }

    @Test
    void theEvadePointMovesAwayFromTheThreat() {
        Position zealotAt = new Position(LING_AT.getX() - 40, LING_AT.getY());
        RunbyTargeting.Situation situation = harass(false)
                .threats(Collections.singletonList(zealotThreat(zealotAt)))
                .build();

        Position point = choose(situation).getPoint();

        assertTrue(point.getDistance(zealotAt) > LING_AT.getDistance(zealotAt));
    }

    @Test
    void evadePersistsInsideTheHysteresisBand() {
        Position zealotAt = east(ZEALOT_REACH + 20);
        RunbyTargeting.Situation situation = harass(false)
                .threats(Collections.singletonList(zealotThreat(zealotAt)))
                .build();
        RunbyTargeting.LingMemory evading = new RunbyTargeting.LingMemory();
        evading.startEvade(new Position(900, 1008), NOW - 1);

        assertEquals(Kind.EVADE, RunbyTargeting.choose(ling(), situation, evading).getKind());
        assertEquals(Kind.SEEK, RunbyTargeting.choose(ling(), situation, new RunbyTargeting.LingMemory()).getKind());
    }

    @Test
    void evadeEndsBeyondTheHysteresisBand() {
        Position zealotAt = east(ZEALOT_REACH + RunbyTargeting.EVADE_HYSTERESIS + 20);
        RunbyTargeting.Situation situation = harass(false)
                .threats(Collections.singletonList(zealotThreat(zealotAt)))
                .build();
        RunbyTargeting.LingMemory evading = new RunbyTargeting.LingMemory();
        evading.startEvade(new Position(900, 1008), NOW - 1);

        assertEquals(Kind.SEEK, RunbyTargeting.choose(ling(), situation, evading).getKind());
    }

    @Test
    void aCommittedEvadeKeepsItsPoint() {
        Position committedPoint = new Position(900, 1008);
        RunbyTargeting.LingMemory evading = new RunbyTargeting.LingMemory();
        evading.startEvade(committedPoint, NOW + RunbyTargeting.EVADE_COMMIT_FRAMES);

        RunbyTargeting.Decision decision = RunbyTargeting.choose(ling(), harass(false).build(), evading);

        assertEquals(Kind.EVADE, decision.getKind());
        assertEquals(committedPoint, decision.getPoint());
    }

    @Test
    void penetrateNeverSelectsANonWorker() {
        Position zealotAt = east(ZEALOT_REACH + 300);
        List<RunbyTargeting.Contact> contacts = new ArrayList<>(Arrays.asList(zealot(20, zealotAt),
                pylon(30, new Position(600, 600))));

        RunbyTargeting.Decision decision = choose(penetrate()
                .contacts(contacts)
                .threats(Collections.singletonList(zealotThreat(zealotAt)))
                .build());

        assertEquals(Kind.SEEK, decision.getKind());
        assertEquals(SEEK, decision.getPoint());
    }

    @Test
    void penetrateTakesWorkersAndEvades() {
        Position zealotFar = east(ZEALOT_REACH + 300);
        RunbyTargeting.Decision worker = choose(penetrate()
                .contacts(Arrays.asList(zealot(20, zealotFar), probe(10, east(40), 1.0)))
                .threats(Collections.singletonList(zealotThreat(zealotFar)))
                .build());
        Position zealotNear = east(ZEALOT_REACH / 2);
        RunbyTargeting.Decision evade = choose(penetrate()
                .contacts(Collections.singletonList(zealot(20, zealotNear)))
                .threats(Collections.singletonList(zealotThreat(zealotNear)))
                .build());

        assertEquals(Kind.WORKER, worker.getKind());
        assertEquals(Kind.EVADE, evade.getKind());
    }

    @Test
    void withNoSeekPointAndNothingToHitTheLingHasNoOrder() {
        RunbyTargeting.Decision decision = choose(harass(false).seekPoint(null).build());

        assertEquals(Kind.NONE, decision.getKind());
        assertNull(decision.getPoint());
    }

    @Test
    void reachIsRangePlusExtentPlusTheLookaheadWalk() {
        UnitType type = UnitType.Protoss_Zealot;
        int extent = Math.max(Math.max(type.dimensionLeft(), type.dimensionRight()),
                Math.max(type.dimensionUp(), type.dimensionDown()));
        int expected = type.groundWeapon().maxRange() + extent
                + (int) Math.ceil(type.topSpeed() * RunbyTargeting.LOOKAHEAD_FRAMES) + RunbyTargeting.REACH_BUFFER;

        assertEquals(expected, RunbyTargeting.reach(type));
        assertTrue(RunbyTargeting.reach(UnitType.Terran_Bunker)
                >= UnitType.Terran_Marine.groundWeapon().maxRange());
    }

    @Test
    void aWorkerIsNeverAFightTarget() {
        assertTrue(RunbyTargeting.isFightTarget(UnitType.Protoss_Zealot));
        assertTrue(RunbyTargeting.isFightTarget(UnitType.Protoss_Photon_Cannon));
        assertEquals(false, RunbyTargeting.isFightTarget(UnitType.Protoss_Probe));
        assertEquals(false, RunbyTargeting.isFightTarget(UnitType.Protoss_Pylon));
    }
}
