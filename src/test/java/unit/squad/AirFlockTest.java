package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirFlockTest {

    private static final int MAP = 4096;

    private static Map<Integer, Position> flock(Position... positions) {
        Map<Integer, Position> members = new LinkedHashMap<>();
        for (int i = 0; i < positions.length; i++) {
            members.put(i + 1, positions[i]);
        }
        return members;
    }

    private static Map<Integer, Position> clusterWithStraggler() {
        return flock(new Position(1000, 1000), new Position(1040, 1000), new Position(1000, 1040),
                new Position(1040, 1040), new Position(1400, 1000));
    }

    @Test
    void everyMemberGetsTheSameRetreatPointAwayFromTheEnemiesNearAnyMember() {
        Map<Integer, Position> members = clusterWithStraggler();
        List<Position> enemies = Arrays.asList(new Position(1000, 1200), new Position(1400, 1150));

        Map<Integer, Position> targets = AirFlock.retreatTargets(members, enemies, MAP, MAP);

        Position anchor = AirFlock.anchor(members);
        Position shared = targets.get(1);
        assertNotNull(shared);
        assertEquals(members.keySet(), targets.keySet());
        for (Position target : targets.values()) {
            assertEquals(shared, target);
        }
        assertTrue(shared.getY() < anchor.getY());
        assertTrue(shared.getDistance(anchor) >= AirFlock.RETREAT_FLEE_DISTANCE - 1);
    }

    @Test
    void aMemberAheadOnTheFleeSideStaysTheFleeDistanceShortOfTheSharedPoint() {
        Map<Integer, Position> members = flock(new Position(2000, 2000), new Position(2040, 2000),
                new Position(2000, 2040), new Position(2000, 1760));
        List<Position> enemies = Collections.singletonList(new Position(2000, 2200));

        Map<Integer, Position> targets = AirFlock.retreatTargets(members, enemies, MAP, MAP);

        Position shared = targets.get(1);
        assertEquals(new Position(2000, 1760 - AirFlock.RETREAT_FLEE_DISTANCE), shared);
        for (Position member : members.values()) {
            assertTrue(member.getDistance(shared) >= AirFlock.RETREAT_FLEE_DISTANCE - 1);
        }
    }

    @Test
    void theRegroupingCountIsNotReportedInRetreat() {
        Set<Integer> regrouping = new HashSet<>(Arrays.asList(3, 4));

        assertEquals(2, AirFlock.regroupingCount(SquadStatus.FIGHT, regrouping));
        assertEquals(2, AirFlock.regroupingCount(SquadStatus.HARASS, regrouping));
        assertEquals(-1, AirFlock.regroupingCount(SquadStatus.RETREAT, regrouping));
    }

    @Test
    void aStragglerInsideAnAvoidedZoneStepsOutOfItFirst() {
        Position straggler = new Position(2400, 2100);
        Position anchor = new Position(1800, 2000);
        AirHarassTargeting.AirThreat turret = turret(new Position(2400, 2000));
        List<AirHarassTargeting.AirThreat> zones = Collections.singletonList(turret);
        assertTrue(turret.margin(straggler, AirHarassTargeting.padding()) <= 0);

        Position step = AirHarassTargeting.regroupPoint(straggler, zones, anchor, point -> true);

        assertNotNull(step);
        assertTrue(turret.margin(step, AirHarassTargeting.padding())
                > turret.margin(straggler, AirHarassTargeting.padding()));
    }

    @Test
    void anEnemyNearOnlyTheStragglerStillMovesTheWholeFlock() {
        Map<Integer, Position> members = clusterWithStraggler();
        List<Position> enemies = Collections.singletonList(new Position(1400, 1150));

        Map<Integer, Position> targets = AirFlock.retreatTargets(members, enemies, MAP, MAP);

        assertNotNull(targets.get(1));
        assertEquals(1, new HashSet<>(targets.values()).size());
    }

    @Test
    void withNoEnemyNearTheFlockEveryMemberFallsBackToTheRally() {
        Map<Integer, Position> members = clusterWithStraggler();
        List<Position> enemies = Collections.singletonList(new Position(3000, 3000));

        Map<Integer, Position> targets = AirFlock.retreatTargets(members, enemies, MAP, MAP);

        assertEquals(members.keySet(), targets.keySet());
        for (Position target : targets.values()) {
            assertNull(target);
        }
    }

    @Test
    void theRetreatPointStaysInsideTheMap() {
        Map<Integer, Position> members = flock(new Position(20, 20), new Position(40, 20));

        Position point = AirFlock.retreatPoint(AirFlock.anchor(members), members.values(),
                Collections.singletonList(new Position(120, 120)), MAP, MAP);

        assertNotNull(point);
        assertTrue(point.getX() >= 0 && point.getY() >= 0);
        assertTrue(point.getX() < MAP && point.getY() < MAP);
    }

    @Test
    void theAnchorStaysWithTheBulkOfTheFlockNotTheStraggler() {
        Map<Integer, Position> members = clusterWithStraggler();

        Position anchor = AirFlock.anchor(members);

        assertNotEquals(members.get(5), anchor);
        assertTrue(anchor.getX() <= 1040);
        assertNull(AirFlock.anchor(Collections.emptyMap()));
    }

    @Test
    void aStragglerBeyondTheRegroupRadiusRegroupsAndTheRestKeepTheirOrders() {
        Map<Integer, Position> members = clusterWithStraggler();
        Position anchor = AirFlock.anchor(members);

        Set<Integer> stragglers = AirFlock.stragglers(members, anchor, Collections.emptySet());

        assertEquals(Collections.singleton(5), stragglers);
        assertTrue(members.get(5).getDistance(anchor) > AirFlock.REGROUP_RADIUS);
    }

    @Test
    void aRegroupingMemberKeepsRegroupingUntilItIsWithinTheJoinRadius() {
        Position anchor = new Position(1000, 1000);
        Map<Integer, Position> members = flock(anchor, new Position(1000 + AirFlock.REGROUP_RADIUS - 40, 1000));

        assertTrue(AirFlock.stragglers(members, anchor, Collections.emptySet()).isEmpty());
        assertEquals(Collections.singleton(2), AirFlock.stragglers(members, anchor, Collections.singleton(2)));

        members.put(2, new Position(1000 + AirFlock.REGROUP_JOIN_RADIUS, 1000));
        assertTrue(AirFlock.stragglers(members, anchor, Collections.singleton(2)).isEmpty());

        members.put(2, new Position(1000 + AirFlock.REGROUP_RADIUS, 1000));
        assertTrue(AirFlock.stragglers(members, anchor, Collections.emptySet()).isEmpty());
        members.put(2, new Position(1000 + AirFlock.REGROUP_RADIUS + 1, 1000));
        assertEquals(Collections.singleton(2), AirFlock.stragglers(members, anchor, Collections.emptySet()));
    }

    @Test
    void aFlockOfOneHasNoStragglers() {
        Map<Integer, Position> members = flock(new Position(1000, 1000));

        assertTrue(AirFlock.stragglers(members, new Position(3000, 3000), Collections.singleton(1)).isEmpty());
    }

    @Test
    void aStragglerFliesToTheAnchorAroundAnAvoidedZone() {
        Position straggler = new Position(2000, 2000);
        Position anchor = new Position(2800, 2000);
        AirHarassTargeting.AirThreat turret = turret(new Position(2400, 2000));
        List<AirHarassTargeting.AirThreat> zones = Collections.singletonList(turret);

        assertEquals(anchor, AirHarassTargeting.regroupPoint(straggler, Collections.emptyList(), anchor,
                point -> true));
        Position step = AirHarassTargeting.regroupPoint(straggler, zones, anchor, point -> true);
        assertTrue(turret.margin(step, AirHarassTargeting.padding()) > 0);
        assertTrue(AirHarassTargeting.segmentClear(straggler, step, zones));
        assertTrue(step.getDistance(anchor) < straggler.getDistance(anchor));
        assertEquals(anchor, AirHarassTargeting.regroupPoint(straggler, zones, anchor, point -> false));
    }

    @Test
    void everyHarassingMutaWithAClearLineTakesTheOneFlockPathAroundAZone() {
        Position seek = new Position(2800, 2000);
        AirHarassTargeting.AirThreat turret = turret(new Position(2400, 2000));
        List<AirHarassTargeting.AirThreat> zones = Collections.singletonList(turret);
        Map<Integer, Position> members = flock(new Position(1880, 1930), new Position(1900, 2000),
                new Position(1880, 2070), new Position(1860, 2000));
        Position anchor = AirFlock.anchor(members);
        Position flockPoint = AirHarassTargeting.flockPoint(anchor, zones, seek, point -> true);
        assertNotNull(flockPoint);
        assertTrue(turret.margin(flockPoint, AirHarassTargeting.padding()) > 0);

        Set<Position> alone = new HashSet<>();
        Set<Position> together = new HashSet<>();
        for (Map.Entry<Integer, Position> member : members.entrySet()) {
            AirHarassTargeting.Muta muta = new AirHarassTargeting.Muta(member.getKey(), member.getValue());
            AirHarassTargeting.Decision own = AirHarassTargeting.choose(muta,
                    situation(zones, seek, null), new AirHarassTargeting.MutaMemory());
            AirHarassTargeting.Decision shared = AirHarassTargeting.choose(muta,
                    situation(zones, seek, flockPoint), new AirHarassTargeting.MutaMemory());
            assertTrue(AirHarassTargeting.segmentClear(member.getValue(), flockPoint, zones));
            assertEquals(AirHarassTargeting.Kind.SEEK, shared.getKind());
            alone.add(own.getPoint());
            together.add(shared.getPoint());
        }

        assertTrue(alone.size() > 1);
        assertEquals(Collections.singleton(flockPoint), together);
    }

    @Test
    void withAClearRouteTheFlockPointIsTheStrikePoint() {
        Position seek = new Position(2800, 2000);

        assertEquals(seek, AirHarassTargeting.flockPoint(new Position(1900, 2000), Collections.emptyList(), seek,
                point -> true));
        assertNull(AirHarassTargeting.flockPoint(null, Collections.emptyList(), seek, point -> true));
    }

    @Test
    void theFlockPointIsHeldUntilItsCommitRunsOutOrTheGoalMoves() {
        AirHarassState state = new AirHarassState(9000, 600);
        Position goal = new Position(2800, 2000);
        Position point = new Position(2100, 2200);

        assertTrue(state.flockPointDue(goal, 9000));
        state.holdFlockPoint(goal, point, 9000 + AirHarassController.FLOCK_POINT_COMMIT_FRAMES);

        assertFalse(state.flockPointDue(goal, 9000 + AirHarassController.FLOCK_POINT_COMMIT_FRAMES - 1));
        assertTrue(state.flockPointDue(goal, 9000 + AirHarassController.FLOCK_POINT_COMMIT_FRAMES));
        assertTrue(state.flockPointDue(new Position(2900, 2000), 9001));
        assertEquals(point, state.getFlockPoint());
    }

    @Test
    void spreadIsMeasuredFromTheCentroid() {
        List<Position> members = Arrays.asList(new Position(0, 0), new Position(100, 0), new Position(200, 0),
                new Position(300, 0));

        Position centroid = AirFlock.centroid(members);
        List<Double> distances = AirFlock.distances(members, centroid);

        assertEquals(new Position(150, 0), centroid);
        assertEquals(100, AirFlock.median(distances), 1e-9);
        assertEquals(150, distances.get(distances.size() - 1), 1e-9);
        assertEquals(50, AirFlock.median(Arrays.asList(10.0, 50.0, 90.0)), 1e-9);
        assertEquals(-1, AirFlock.median(Collections.emptyList()), 1e-9);
        assertNull(AirFlock.centroid(Collections.emptyList()));
    }

    @Test
    void theNearestMateDistanceIsMinusOneWithNoMates() {
        Position dead = new Position(1000, 1000);

        assertEquals(-1, AirFlock.nearestDistance(dead, Collections.emptyList()), 1e-9);
        assertEquals(300, AirFlock.nearestDistance(dead, Arrays.asList(new Position(1300, 1000),
                new Position(1000, 1500))), 1e-9);
    }

    private static AirHarassTargeting.AirThreat turret(Position position) {
        UnitType type = UnitType.Terran_Missile_Turret;
        return AirHarassTargeting.AirThreat.of(1, type, position,
                AirHarassTargeting.airRange(type, weapon -> weapon.maxRange()));
    }

    private static AirHarassTargeting.Situation situation(List<AirHarassTargeting.AirThreat> zones, Position seek,
                                                          Position flockPoint) {
        return AirHarassTargeting.Situation.builder()
                .avoided(zones)
                .flockSize(4)
                .seekPoint(seek)
                .flockPoint(flockPoint)
                .now(12000)
                .build();
    }
}
