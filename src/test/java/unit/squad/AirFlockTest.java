package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import telemetry.FlockRow;

import java.util.ArrayList;
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

    private static Map<Integer, Position> targets(Map<Integer, Position> members, List<Position> enemies,
                                                  int mapWidth, int mapHeight) {
        return AirFlock.retreatPlan(members, enemies, mapWidth, mapHeight, new HashSet<>()).getTargets();
    }

    private static Position memberTarget(Position member, Position anchor, Position shared, List<Position> enemies,
                                         int mapWidth, int mapHeight) {
        switch (AirFlock.retreatBranch(member, anchor, shared, enemies, mapWidth, mapHeight, false)) {
            case SHARED:
                return shared;
            case FLEE:
                return AirFlock.fleePoint(member, shared, enemies, mapWidth, mapHeight);
            default:
                return anchor;
        }
    }

    private static Map<Integer, Position> clusterWithStraggler() {
        return flock(new Position(1000, 1000), new Position(1040, 1000), new Position(1000, 1040),
                new Position(1040, 1040), new Position(1400, 1000));
    }

    @Test
    void everyMemberGetsTheSameRetreatPointAwayFromTheEnemiesNearAnyMember() {
        Map<Integer, Position> members = clusterWithStraggler();
        List<Position> enemies = Arrays.asList(new Position(1000, 1200), new Position(1400, 1150));

        Map<Integer, Position> targets = targets(members, enemies, MAP, MAP);

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

        Map<Integer, Position> targets = targets(members, enemies, MAP, MAP);

        Position shared = targets.get(1);
        assertEquals(new Position(2000, 1760 - AirFlock.RETREAT_FLEE_DISTANCE), shared);
        for (Position member : members.values()) {
            assertTrue(member.getDistance(shared) >= AirFlock.RETREAT_FLEE_DISTANCE - 1);
        }
    }

    @Test
    void aFarMemberBeyondTheEnemyFleesOnItsOwnInsteadOfCrossingIt() {
        Map<Integer, Position> members = flock(new Position(1000, 1000), new Position(1040, 1000),
                new Position(1000, 1040), new Position(1040, 1040), new Position(1000, 1450));
        List<Position> enemies = Collections.singletonList(new Position(1000, 1250));

        Map<Integer, Position> targets = targets(members, enemies, MAP, MAP);

        Position shared = targets.get(1);
        Position straggler = members.get(5);
        assertNotNull(shared);
        assertTrue(straggler.getDistance(AirFlock.anchor(members)) > AirFlock.REGROUP_RADIUS);
        assertTrue(AirFlock.pathThroughEnemy(straggler, shared, enemies));
        for (int id = 1; id <= 4; id++) {
            assertEquals(shared, targets.get(id));
        }
        Position own = targets.get(5);
        assertNotEquals(shared, own);
        assertFalse(AirFlock.pathThroughEnemy(straggler, own, enemies));
        assertTrue(own.getY() > straggler.getY());
    }

    @Test
    void aFarMemberWhosePathToTheSharedPointIsBlockedRegroupsOnTheAnchorWhenThatPathIsClear() {
        Position anchor = new Position(1000, 1000);
        Position member = new Position(1600, 700);
        Position shared = new Position(1100, 300);
        List<Position> enemies = Collections.singletonList(new Position(1350, 500));
        assertTrue(AirFlock.pathThroughEnemy(member, shared, enemies));
        assertFalse(AirFlock.pathThroughEnemy(member, anchor, enemies));

        assertEquals(anchor, memberTarget(member, anchor, shared, enemies, MAP, MAP));
    }

    @Test
    void anEnemyFarFromEveryMemberDoesNotDivertAFarMemberFromTheSharedPoint() {
        Map<Integer, Position> members = clusterWithStraggler();
        Position nearEnemy = new Position(1000, 1200);
        Position shared = targets(members, Collections.singletonList(nearEnemy), MAP, MAP).get(1);
        Position straggler = members.get(5);
        Position distant = new Position(straggler.getX() + (int) Math.round(0.95 * (shared.getX() - straggler.getX())),
                straggler.getY() + (int) Math.round(0.95 * (shared.getY() - straggler.getY())));
        for (Position member : members.values()) {
            assertTrue(member.getDistance(distant) > AirFlock.RETREAT_SCAN_RADIUS);
        }
        List<Position> enemies = Arrays.asList(nearEnemy, distant);
        assertTrue(straggler.getDistance(AirFlock.anchor(members)) > AirFlock.REGROUP_RADIUS);
        assertTrue(AirFlock.pathThroughEnemy(straggler, shared, enemies));

        Map<Integer, Position> targets = targets(members, enemies, MAP, MAP);

        assertEquals(shared, targets.get(1));
        assertEquals(shared, targets.get(5));
    }

    @Test
    void aFarMemberPinnedAgainstTheMapEdgeRegroupsOnTheAnchor() {
        Position anchor = new Position(600, 600);
        Position member = new Position(10, 10);
        Position shared = new Position(1000, 1000);
        List<Position> enemies = Collections.singletonList(new Position(200, 200));
        assertTrue(AirFlock.pathThroughEnemy(member, shared, enemies));
        assertTrue(AirFlock.pathThroughEnemy(member, anchor, enemies));
        Position flee = AirFlock.fleePoint(member, shared, enemies, MAP, MAP);
        assertNotNull(flee);
        assertTrue(flee.getDistance(member) < AirFlock.MIN_FLEE_STEP);

        assertEquals(anchor, memberTarget(member, anchor, shared, enemies, MAP, MAP));
    }

    @Test
    void aFarMemberWithinTheLeashFleesOnItsOwnWhenBothPathsAreBlocked() {
        Position anchor = new Position(1000, 1000);
        Position member = new Position(1400, 1000);
        Position shared = new Position(600, 1000);
        List<Position> enemies = Collections.singletonList(new Position(1200, 1000));
        assertTrue(member.getDistance(anchor) <= AirFlock.RETREAT_FLEE_LEASH);

        assertEquals(AirFlock.RetreatBranch.FLEE,
                AirFlock.retreatBranch(member, anchor, shared, enemies, MAP, MAP, false));
        assertEquals(new Position(1656, 1000), memberTarget(member, anchor, shared, enemies, MAP, MAP));
    }

    @Test
    void aFarMemberBeyondTheLeashFliesToTheAnchorInsteadOfFleeing() {
        Position anchor = new Position(1000, 1000);
        Position member = new Position(1700, 1000);
        Position shared = new Position(600, 1000);
        List<Position> enemies = Collections.singletonList(new Position(1450, 1000));
        assertTrue(member.getDistance(anchor) > AirFlock.RETREAT_FLEE_LEASH);
        assertTrue(AirFlock.pathThroughEnemy(member, shared, enemies));
        assertTrue(AirFlock.pathThroughEnemy(member, anchor, enemies));

        assertEquals(AirFlock.RetreatBranch.ANCHOR,
                AirFlock.retreatBranch(member, anchor, shared, enemies, MAP, MAP, false));
        assertEquals(anchor, memberTarget(member, anchor, shared, enemies, MAP, MAP));
    }

    @Test
    void aMemberTakesTheSharedBranchWithinTheRegroupRadiusAndTheAnchorBranchOnAClearPath() {
        Position anchor = new Position(1000, 1000);
        Position shared = new Position(1000, 600);

        assertEquals(AirFlock.RetreatBranch.SHARED, AirFlock.retreatBranch(new Position(1000, 1150), anchor, shared,
                Collections.singletonList(new Position(1000, 900)), MAP, MAP, false));
        assertEquals(AirFlock.RetreatBranch.ANCHOR, AirFlock.retreatBranch(new Position(1600, 700),
                anchor, new Position(1100, 300), Collections.singletonList(new Position(1350, 500)), MAP, MAP, false));
    }

    private static List<AirFlock.RetreatBranch> farMemberRun(Position enemyStart, boolean chasing, double[] farthest) {
        Map<Integer, Position> members = flock(new Position(2000, 2000), new Position(2040, 2000),
                new Position(2000, 2040), new Position(2040, 2040), new Position(2400, 2000));
        Set<Integer> leashed = new HashSet<>();
        List<AirFlock.RetreatBranch> branches = new ArrayList<>();
        double speed = 20;
        Position enemy = enemyStart;
        for (int step = 0; step < 400; step++) {
            Position anchor = AirFlock.anchor(members);
            Position member = members.get(5);
            AirFlock.RetreatPlan plan = AirFlock.retreatPlan(members, Collections.singletonList(enemy), MAP, MAP,
                    leashed);
            AirFlock.RetreatBranch branch = plan.getBranches().get(5);
            branches.add(branch);
            if (branch == AirFlock.RetreatBranch.SHARED && branches.contains(AirFlock.RetreatBranch.ANCHOR)) {
                break;
            }
            Position target = plan.getTargets().get(5);
            double fraction = Math.min(1, speed / member.getDistance(target));
            Position moved = new Position(
                    member.getX() + (int) Math.round(fraction * (target.getX() - member.getX())),
                    member.getY() + (int) Math.round(fraction * (target.getY() - member.getY())));
            members.put(5, moved);
            farthest[0] = Math.max(farthest[0], moved.getDistance(anchor));
            if (chasing) {
                double gap = Math.max(0, enemy.getDistance(moved) - 40);
                double pull = Math.min(1, speed / Math.max(gap, 1));
                enemy = new Position(
                        enemy.getX() + (int) Math.round(pull * (moved.getX() - enemy.getX())),
                        enemy.getY() + (int) Math.round(pull * (moved.getY() - enemy.getY())));
            }
        }
        return branches;
    }

    private static void assertLeashHolds(List<AirFlock.RetreatBranch> branches, double farthest) {
        int firstAnchor = branches.indexOf(AirFlock.RetreatBranch.ANCHOR);
        assertTrue(firstAnchor >= 0);
        assertFalse(branches.subList(firstAnchor, branches.size()).contains(AirFlock.RetreatBranch.FLEE));
        assertEquals(AirFlock.RetreatBranch.SHARED, branches.get(branches.size() - 1));
        assertTrue(farthest <= AirFlock.RETREAT_FLEE_LEASH + AirFlock.RETREAT_FLEE_DISTANCE);
    }

    @Test
    void anEnemyThatFollowsAFarMemberNeverDragsItToTheMapEdgeAndTheLeashHolds() {
        double[] farthest = new double[1];

        List<AirFlock.RetreatBranch> branches = farMemberRun(new Position(2250, 2000), true, farthest);

        assertLeashHolds(branches, farthest[0]);
    }

    @Test
    void aStaticEnemyBetweenAFarMemberAndTheFlockDoesNotMakeItFlipBetweenFleeingAndTheAnchor() {
        double[] farthest = new double[1];

        List<AirFlock.RetreatBranch> branches = farMemberRun(new Position(2200, 2000), false, farthest);

        assertLeashHolds(branches, farthest[0]);
    }

    @Test
    void theLeashIsDroppedWhenTheMemberTakesTheSharedPointAndWhenNoEnemyIsNearTheFlock() {
        Map<Integer, Position> members = flock(new Position(1000, 1000), new Position(1040, 1000),
                new Position(1000, 1040), new Position(1040, 1040), new Position(1700, 1000));
        Set<Integer> leashed = new HashSet<>();
        List<Position> enemy = Collections.singletonList(new Position(1250, 1000));

        AirFlock.RetreatPlan blocked = AirFlock.retreatPlan(members, enemy, MAP, MAP, leashed);

        assertEquals(AirFlock.RetreatBranch.ANCHOR, blocked.getBranches().get(5));
        assertTrue(leashed.contains(5));

        members.put(5, new Position(1010, 1010));
        AirFlock.retreatPlan(members, enemy, MAP, MAP, leashed);
        assertFalse(leashed.contains(5));

        leashed.add(5);
        AirFlock.retreatPlan(members, Collections.singletonList(new Position(3000, 3000)), MAP, MAP, leashed);
        assertTrue(leashed.isEmpty());
    }

    @Test
    void aRetreatPlanNamesEveryMembersBranchAndIsNoneWithNoEnemyNearTheFlock() {
        Map<Integer, Position> members = flock(new Position(1000, 1000), new Position(1040, 1000),
                new Position(1000, 1040), new Position(1040, 1040), new Position(1000, 1450));

        AirFlock.RetreatPlan none = AirFlock.retreatPlan(members,
                Collections.singletonList(new Position(3000, 3000)), MAP, MAP, new HashSet<>());
        AirFlock.RetreatPlan near = AirFlock.retreatPlan(members,
                Collections.singletonList(new Position(1000, 1250)), MAP, MAP, new HashSet<>());

        for (AirFlock.RetreatBranch branch : none.getBranches().values()) {
            assertEquals(AirFlock.RetreatBranch.NONE, branch);
        }
        assertNull(none.getTargets().get(1));
        assertEquals(AirFlock.RetreatBranch.SHARED, near.getBranches().get(1));
        assertEquals(AirFlock.RetreatBranch.FLEE, near.getBranches().get(5));
    }

    @Test
    void branchCountCountsOnlyTheGivenMembersOnTheGivenBranch() {
        Map<Integer, AirFlock.RetreatBranch> branches = new LinkedHashMap<>();
        branches.put(1, AirFlock.RetreatBranch.SHARED);
        branches.put(2, AirFlock.RetreatBranch.SHARED);
        branches.put(3, AirFlock.RetreatBranch.FLEE);

        assertEquals(1, AirFlock.branchCount(branches, Arrays.asList(1, 3, 9), AirFlock.RetreatBranch.SHARED));
        assertEquals(1, AirFlock.branchCount(branches, Arrays.asList(1, 3, 9), AirFlock.RetreatBranch.FLEE));
        assertEquals(0, AirFlock.branchCount(branches, Arrays.asList(1, 3, 9), AirFlock.RetreatBranch.ANCHOR));
    }

    @Test
    void aMemberWithinTheRegroupRadiusKeepsTheSharedPointEvenAcrossAnEnemy() {
        Position anchor = new Position(1000, 1000);
        Position member = new Position(1000, 1150);
        Position shared = new Position(1000, 600);
        List<Position> enemies = Collections.singletonList(new Position(1000, 900));
        assertTrue(AirFlock.pathThroughEnemy(member, shared, enemies));

        assertEquals(shared, memberTarget(member, anchor, shared, enemies, MAP, MAP));
    }

    @Test
    void aFarMemberWithAClearPathKeepsTheSharedPoint() {
        Position anchor = new Position(1000, 1000);
        Position member = new Position(1400, 1000);
        Position shared = new Position(1000, 600);
        List<Position> enemies = Collections.singletonList(new Position(1000, 1300));

        assertEquals(shared, memberTarget(member, anchor, shared, enemies, MAP, MAP));
    }

    @Test
    void onlyAnEnemyAheadWithinTheClearanceRunsThePathThroughIt() {
        Position from = new Position(1000, 1000);
        Position to = new Position(1000, 500);
        int clearance = AirFlock.RETREAT_PATH_CLEARANCE;

        assertTrue(AirFlock.pathThroughEnemy(from, to,
                Collections.singletonList(new Position(1000 + clearance - 1, 800))));
        assertFalse(AirFlock.pathThroughEnemy(from, to,
                Collections.singletonList(new Position(1000 + clearance + 1, 800))));
        assertFalse(AirFlock.pathThroughEnemy(from, to, Collections.singletonList(new Position(1000, 1050))));
        assertFalse(AirFlock.pathThroughEnemy(from, to, Collections.singletonList(new Position(1050, 1000))));
        assertTrue(AirFlock.pathThroughEnemy(from, to, Collections.singletonList(new Position(1000, 400))));
        assertFalse(AirFlock.pathThroughEnemy(from, from, Collections.singletonList(new Position(1000, 1000))));
    }

    @Test
    void aFarMemberWithNoWayOutOfItsOwnRegroupsOnTheAnchor() {
        Position anchor = new Position(1000, 1000);
        Position member = new Position(1000, 1600);
        Position shared = new Position(1000, 400);
        List<Position> enemies = Arrays.asList(new Position(1000, 1350), new Position(1000, 1850));
        assertNull(AirFlock.fleePoint(member, shared, enemies, MAP, MAP));

        assertEquals(anchor, memberTarget(member, anchor, shared, enemies, MAP, MAP));
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

        Map<Integer, Position> targets = targets(members, enemies, MAP, MAP);

        assertNotNull(targets.get(1));
        assertEquals(1, new HashSet<>(targets.values()).size());
    }

    @Test
    void withNoEnemyNearTheFlockEveryMemberFallsBackToTheRally() {
        Map<Integer, Position> members = clusterWithStraggler();
        List<Position> enemies = Collections.singletonList(new Position(3000, 3000));

        Map<Integer, Position> targets = targets(members, enemies, MAP, MAP);

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

    @Test
    void aStragglerKeepsATargetInWeaponRangeButTakesNoOtherTarget() {
        assertTrue(AirFlock.keepsTarget(true, false));
        assertFalse(AirFlock.keepsTarget(false, false));
    }

    @Test
    void aStragglerInsideAnAvoidedZoneDropsEvenAnInRangeTarget() {
        assertFalse(AirFlock.keepsTarget(true, true));
        assertFalse(AirFlock.keepsTarget(false, true));
    }

    @Test
    void anArmedStragglerStaysRegroupingSoItRejoinsOnceItsTargetIsOutOfRange() {
        Map<Integer, Position> members = clusterWithStraggler();
        Position anchor = AirFlock.anchor(members);
        Set<Integer> first = AirFlock.stragglers(members, anchor, Collections.emptySet());
        assertEquals(Collections.singleton(5), first);
        assertTrue(AirFlock.keepsTarget(true, false));

        members.put(5, new Position(1200, 1000));
        Set<Integer> second = AirFlock.stragglers(members, AirFlock.anchor(members), first);
        assertEquals(Collections.singleton(5), second);
        assertFalse(AirFlock.keepsTarget(false, false));
    }

    @Test
    void onlyMembersNewToTheRegroupSetHaveEnteredIt() {
        Set<Integer> previous = new HashSet<>(Arrays.asList(1, 2));
        Set<Integer> current = new HashSet<>(Arrays.asList(2, 3));

        assertEquals(Collections.singleton(3), AirFlock.entered(previous, current));
        assertTrue(AirFlock.entered(current, current).isEmpty());
    }

    @Test
    void aLostMutaCountsOnlyOtherMutalisksAsMates() {
        Map<Integer, Position> mutas = new LinkedHashMap<>();
        mutas.put(243, new Position(2836, 1544));
        mutas.put(250, new Position(3107, 1544));
        mutas.put(251, new Position(3200, 1544));
        Set<Integer> regrouping = Collections.singleton(243);

        FlockRow row = SquadManager.flockLossRow(12935, 243, new Position(2836, 1544), "squad-1",
                SquadStatus.FIGHT, mutas, regrouping, null);

        assertEquals(3, row.getMutas());
        assertEquals(271.0, row.getNearestMateDistance(), 1e-9);
        assertEquals(0, row.getLastMuta());
        assertEquals(regrouping, row.getRegroupingIds());
    }

    @Test
    void theLastMutaOfItsSquadIsFlaggedAndHasNoMate() {
        Map<Integer, Position> mutas = Collections.singletonMap(243, new Position(2836, 1544));

        FlockRow row = SquadManager.flockLossRow(12935, 243, new Position(2836, 1544), "squad-1",
                SquadStatus.FIGHT, mutas, Collections.emptySet(), null);

        assertEquals(1, row.getLastMuta());
        assertEquals(-1.0, row.getNearestMateDistance(), 1e-9);
    }

    @Test
    void aLostMutaWithNoSquadLeavesTheSquadColumnsUnevaluated() {
        FlockRow row = SquadManager.flockLossRow(12935, 243, new Position(2836, 1544), null, null,
                Collections.emptyMap(), null, null);

        assertNull(row.getSquadId());
        assertEquals(-1, row.getLastMuta());
        assertEquals(-1, row.getMutas());
        assertNull(row.getRegroupingIds());
    }
}
