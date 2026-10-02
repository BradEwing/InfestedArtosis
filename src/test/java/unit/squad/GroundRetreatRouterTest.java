package unit.squad;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.UnitType;
import info.map.GameMap;
import info.map.MapTile;
import info.map.MapTileType;
import org.junit.jupiter.api.Test;
import telemetry.RetreatRoute;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Retreat routing on small hand drawn maps, where {@code #} is unwalkable and {@code .} walkable, x is the column
 * and y the row.
 */
class GroundRetreatRouterTest {

    /**
     * The LYRGH0DQ shape: a walled dead end pocket north of the squad, a corridor east to home along rows 6-8, and
     * the enemy coming up from the south. Straight away from the enemy is north, into the pocket's wall.
     */
    private static final String[] POCKET_WITH_EAST_EXIT = {
        "##############################",
        "#........#####################",
        "#........#####################",
        "#........#####################",
        "#........#####################",
        "#........#####################",
        "#............................#",
        "#............................#",
        "#............................#",
        "#........#####################",
        "#........#####################",
        "#........#####################",
        "#........#####################",
        "#........#####################",
        "#........#####################",
        "##############################",
    };

    /**
     * Open ground with a border wall.
     */
    private static final String[] OPEN_FIELD = openField(24, 20);

    /**
     * A wall down column 10 with a gap at rows 9-11 on the straight line home and a second gap at rows 1-2.
     */
    private static final String[] TWO_GAP_WALL = {
        "########################",
        "#......................#",
        "#......................#",
        "#.........#............#",
        "#.........#............#",
        "#.........#............#",
        "#.........#............#",
        "#.........#............#",
        "#.........#............#",
        "#......................#",
        "#......................#",
        "#......................#",
        "#.........#............#",
        "#.........#............#",
        "#.........#............#",
        "#.........#............#",
        "#.........#............#",
        "#.........#............#",
        "#.........#............#",
        "########################",
    };

    /**
     * A pocket whose only way out is a two tile wide choke south to home.
     */
    private static final String[] SEALED_POCKET = {
        "##########",
        "#........#",
        "#........#",
        "#........#",
        "#........#",
        "#........#",
        "#........#",
        "#........#",
        "#........#",
        "####..####",
        "####..####",
        "####..####",
        "####..####",
        "#........#",
        "#........#",
        "#........#",
        "#........#",
        "#........#",
        "##########",
    };

    /**
     * A one tile high corridor with home at its west end, so home's only neighbour is the tile east of it.
     */
    private static final String[] DEAD_END_HOME = {
        "#########",
        "#.......#",
        "#########",
    };

    @Test
    void enemyBetweenSquadAndHomeWallLeadsAlongTheCorridorHomeNotIntoThePocket() {
        GameMap map = map(POCKET_WITH_EAST_EXIT);
        Position home = tileCenter(27, 7);
        Position squad = tileCenter(4, 7);
        Position enemy = tileCenter(4, 13);

        GroundRetreatRouter.Plan<String> plan = new GroundRetreatRouter(map)
                .plan(Collections.singletonMap("ling", squad), home, Collections.singletonList(enemy));

        Position target = plan.getTargets().get("ling");
        TilePosition tile = target.toTilePosition();
        assertEquals(RetreatRoute.HOME, plan.getRoute());
        assertTrue(tile.getY() >= 6 && tile.getY() <= 8, "stays in the corridor, got " + tile);
        assertEquals(4 + GroundRetreatRouter.PATH_STEP_TILES, tile.getX());
        assertTrue(homeDistance(map, home, tile) < homeDistance(map, home, squad.toTilePosition()));
    }

    @Test
    void enemyStandingInTheGapHomeIsWalkedAroundThroughTheOtherGap() {
        GameMap map = map(TWO_GAP_WALL);
        Position home = tileCenter(20, 10);
        Position squad = tileCenter(3, 10);
        Position enemy = tileCenter(10, 10);

        GroundRetreatRouter.Plan<String> plan = new GroundRetreatRouter(map)
                .plan(Collections.singletonMap("hydra", squad), home, Collections.singletonList(enemy));

        Position target = plan.getTargets().get("hydra");
        TilePosition tile = target.toTilePosition();
        assertEquals(RetreatRoute.DETOUR, plan.getRoute());
        assertTrue(target.getDistance(enemy) > GroundRetreatRouter.DANGER_RADIUS);
        assertTrue(tile.getY() < 10 && tile.getX() > 3, "heads up toward the top gap, got " + tile);
        int[][] around = map.groundStepDistances(home.toTilePosition(),
                t -> tileCenter(t.getX(), t.getY()).getDistance(enemy) <= GroundRetreatRouter.DANGER_RADIUS);
        assertEquals(around[3][10] - GroundRetreatRouter.PATH_STEP_TILES, around[tile.getX()][tile.getY()]);
    }

    @Test
    void enemyStandingOnTheDirectPathInOpenGroundIsWalkedAround() {
        GameMap map = map(OPEN_FIELD);
        Position home = tileCenter(21, 10);
        Position squad = tileCenter(3, 10);
        Position enemy = tileCenter(12, 10);

        GroundRetreatRouter.Plan<String> plan = new GroundRetreatRouter(map)
                .plan(Collections.singletonMap("hydra", squad), home, Collections.singletonList(enemy));

        Position target = plan.getTargets().get("hydra");
        assertFalse(plan.getRoute() == RetreatRoute.CORNERED);
        assertTrue(target.getDistance(enemy) > GroundRetreatRouter.DANGER_RADIUS);
        assertTrue(Math.abs(target.toTilePosition().getY() - 10) > 0, "leaves the enemy's row, got " + target);
        int[][] around = map.groundStepDistances(home.toTilePosition(),
                t -> tileCenter(t.getX(), t.getY()).getDistance(enemy) <= GroundRetreatRouter.DANGER_RADIUS);
        TilePosition start = squad.toTilePosition();
        TilePosition end = target.toTilePosition();
        assertEquals(around[start.getX()][start.getY()] - GroundRetreatRouter.PATH_STEP_TILES,
                around[end.getX()][end.getY()]);
    }

    @Test
    void memberInsideTheDangerRadiusBacksOutWithoutClosingOnTheEnemy() {
        GameMap map = map(TWO_GAP_WALL);
        Position home = tileCenter(20, 10);
        Position squad = tileCenter(7, 10);
        Position enemy = tileCenter(10, 10);

        GroundRetreatRouter.Plan<String> plan = new GroundRetreatRouter(map)
                .plan(Collections.singletonMap("ling", squad), home, Collections.singletonList(enemy));

        Position target = plan.getTargets().get("ling");
        assertEquals(RetreatRoute.DETOUR, plan.getRoute());
        assertTrue(target.getDistance(enemy) >= squad.getDistance(enemy));
    }

    @Test
    void noThreatsWalksTheDirectPathHome() {
        GameMap map = map(POCKET_WITH_EAST_EXIT);
        Position home = tileCenter(27, 7);

        GroundRetreatRouter.Plan<String> plan = new GroundRetreatRouter(map)
                .plan(Collections.singletonMap("ling", tileCenter(4, 3)), home, Collections.emptyList());

        assertEquals(RetreatRoute.HOME, plan.getRoute());
        TilePosition tile = plan.getTargets().get("ling").toTilePosition();
        assertEquals(homeDistance(map, home, new TilePosition(4, 3)) - GroundRetreatRouter.PATH_STEP_TILES,
                homeDistance(map, home, tile));
    }

    @Test
    void squadNearHomeIsSentHome() {
        GameMap map = map(POCKET_WITH_EAST_EXIT);
        Position home = tileCenter(27, 7);

        GroundRetreatRouter.Plan<String> plan = new GroundRetreatRouter(map)
                .plan(Collections.singletonMap("ling", tileCenter(25, 7)), home, Collections.emptyList());

        assertEquals(home, plan.getTargets().get("ling"));
    }

    @Test
    void everyRetreatTargetIsWalkableAndConnectedToHome() {
        List<String[]> layouts = Arrays.asList(POCKET_WITH_EAST_EXIT, OPEN_FIELD, SEALED_POCKET, TWO_GAP_WALL,
                DEAD_END_HOME);
        Set<RetreatRoute> routes = EnumSet.noneOf(RetreatRoute.class);
        for (String[] layout : layouts) {
            GameMap map = map(layout);
            Position home = firstWalkableFromBottomRight(map);
            int[][] connected = map.groundStepDistances(home.toTilePosition(), t -> false);
            for (int ex = 0; ex < map.getWidth(); ex += 3) {
                for (int ey = 0; ey < map.getHeight(); ey += 3) {
                    Map<String, Position> members = new LinkedHashMap<>();
                    for (int x = 0; x < map.getWidth(); x++) {
                        for (int y = 0; y < map.getHeight(); y++) {
                            members.put(x + ":" + y, tileCenter(x, y));
                        }
                    }
                    GroundRetreatRouter.Plan<String> plan = new GroundRetreatRouter(map)
                            .plan(members, home, Collections.singletonList(tileCenter(ex, ey)));
                    assertNotNull(plan);
                    routes.add(plan.getRoute());
                    assertEquals(members.size(), plan.getTargets().size());
                    for (Map.Entry<String, Position> target : plan.getTargets().entrySet()) {
                        String member = "member " + target.getKey() + " enemy " + ex + ":" + ey;
                        if (target.getValue() == null) {
                            assertFalse(reachedWithinSnap(connected, members.get(target.getKey())),
                                    "only a member with no reached tile near it goes unplanned, " + member);
                            continue;
                        }
                        TilePosition tile = target.getValue().toTilePosition();
                        String where = member + " target " + tile;
                        assertTrue(map.isWalkableTile(tile), "walkable, " + where);
                        assertTrue(connected[tile.getX()][tile.getY()] >= 0, "connected to home, " + where);
                        assertEquals(GroundRetreatRouter.center(tile), target.getValue());
                    }
                }
            }
        }
        assertTrue(routes.contains(RetreatRoute.HOME_CONTESTED), "the sweep covers the contested home plan");
    }

    @Test
    void squadInAPocketWhoseOnlyExitIsHeldByTheEnemyIsCornered() {
        GameMap map = map(SEALED_POCKET);
        Position home = tileCenter(4, 16);
        Position squad = tileCenter(4, 4);
        Position enemy = tileCenter(4, 10);

        GroundRetreatRouter.Plan<String> plan = new GroundRetreatRouter(map)
                .plan(Collections.singletonMap("marine", squad), home, Collections.singletonList(enemy));

        assertEquals(RetreatRoute.CORNERED, plan.getRoute());
        Position target = plan.getTargets().get("marine");
        assertTrue(target.getDistance(enemy) > squad.getDistance(enemy));
        assertTrue(target.toTilePosition().getY() < 9, "stays in the pocket, got " + target);
    }

    @Test
    void theSamePocketWithTheExitClearIsNotCornered() {
        GameMap map = map(SEALED_POCKET);
        Position home = tileCenter(4, 16);

        GroundRetreatRouter.Plan<String> plan = new GroundRetreatRouter(map)
                .plan(Collections.singletonMap("marine", tileCenter(4, 4)), home,
                        Collections.singletonList(tileCenter(1, 1)));

        assertEquals(RetreatRoute.HOME, plan.getRoute());
    }

    @Test
    void oneMemberWithAWayOutKeepsTheSquadFromBeingCornered() {
        GameMap map = map(SEALED_POCKET);
        Position home = tileCenter(4, 16);
        Map<String, Position> members = new LinkedHashMap<>();
        members.put("trapped", tileCenter(4, 3));
        members.put("through", tileCenter(4, 14));

        GroundRetreatRouter.Plan<String> plan = new GroundRetreatRouter(map)
                .plan(members, home, Collections.singletonList(tileCenter(4, 10)));

        assertFalse(plan.getRoute() == RetreatRoute.CORNERED);
    }

    @Test
    void homeWithNoWalkableTileNearItHasNoPlan() {
        GameMap map = map(SEALED_POCKET);
        String[] walled = new String[12];
        Arrays.fill(walled, "############");
        GameMap solid = map(walled);

        assertNotNull(new GroundRetreatRouter(map).plan(Collections.singletonMap("a", tileCenter(4, 4)),
                tileCenter(4, 16), Collections.emptyList()));
        assertEquals(null, new GroundRetreatRouter(solid).plan(Collections.singletonMap("a", tileCenter(4, 4)),
                tileCenter(6, 6), Collections.emptyList()));
    }

    @Test
    void enemyOnTheHomeTileContestsHomeInsteadOfCorneringTheSquad() {
        GameMap map = map(OPEN_FIELD);
        Position home = tileCenter(21, 10);
        Position squad = tileCenter(4, 10);

        GroundRetreatRouter.Plan<String> plan = new GroundRetreatRouter(map)
                .plan(Collections.singletonMap("ling", squad), home, Collections.singletonList(home));

        assertEquals(RetreatRoute.HOME_CONTESTED, plan.getRoute());
        TilePosition tile = plan.getTargets().get("ling").toTilePosition();
        assertEquals(homeDistance(map, home, squad.toTilePosition()) - GroundRetreatRouter.PATH_STEP_TILES,
                homeDistance(map, home, tile), "walks the direct path home");
        assertFalse(SquadManager.corneredSquadFights(SquadStatus.RETREAT, plan.getRoute(),
                CombatSimulator.CombatResult.ENGAGE));
    }

    @Test
    void memberWalkingToAContestedHomeStopsAtTheEdgeOfTheDanger() {
        GameMap map = map(OPEN_FIELD);
        Position home = tileCenter(21, 10);
        Position enemy = tileCenter(20, 10);

        GroundRetreatRouter.Plan<String> plan = new GroundRetreatRouter(map)
                .plan(Collections.singletonMap("ling", tileCenter(13, 10)), home, Collections.singletonList(enemy));

        Position target = plan.getTargets().get("ling");
        TilePosition tile = target.toTilePosition();
        assertEquals(RetreatRoute.HOME_CONTESTED, plan.getRoute());
        assertTrue(target.getDistance(enemy) > GroundRetreatRouter.DANGER_RADIUS, "outside the danger, got " + tile);
        assertTrue(homeDistance(map, home, tile) < homeDistance(map, home, new TilePosition(13, 10)));
        int[][] direct = map.groundStepDistances(home.toTilePosition(), t -> false);
        for (TilePosition next : map.groundNeighbors(tile)) {
            if (direct[next.getX()][next.getY()] == direct[tile.getX()][tile.getY()] - 1) {
                assertTrue(GroundRetreatRouter.center(next).getDistance(enemy) <= GroundRetreatRouter.DANGER_RADIUS,
                        "every next step home enters the danger, " + next);
            }
        }
    }

    @Test
    void memberStagingForAContestedHomeWalksAroundAnEnemyInTheWay() {
        GameMap map = map(TWO_GAP_WALL);
        Position home = tileCenter(20, 10);
        Position patrol = tileCenter(10, 10);

        GroundRetreatRouter.Plan<String> plan = new GroundRetreatRouter(map)
                .plan(Collections.singletonMap("hydra", tileCenter(3, 10)), home, Arrays.asList(home, patrol));

        Position target = plan.getTargets().get("hydra");
        TilePosition tile = target.toTilePosition();
        assertEquals(RetreatRoute.HOME_CONTESTED, plan.getRoute());
        assertTrue(target.getDistance(patrol) > GroundRetreatRouter.DANGER_RADIUS, "outside the patrol, got " + tile);
        assertTrue(tile.getY() < 10 && tile.getX() > 3, "heads up toward the top gap, got " + tile);
        int[][] around = map.groundStepDistances(home.toTilePosition(),
                t -> tileCenter(t.getX(), t.getY()).getDistance(patrol) <= GroundRetreatRouter.DANGER_RADIUS);
        assertEquals(around[3][10] - GroundRetreatRouter.PATH_STEP_TILES, around[tile.getX()][tile.getY()]);
    }

    @Test
    void enemyNextToHomeWithOneApproachClearDoesNotContestIt() {
        GameMap map = map(OPEN_FIELD);
        Position home = tileCenter(21, 10);
        Position enemy = tileCenter(15, 10);
        assertTrue(home.getDistance(enemy) > GroundRetreatRouter.DANGER_RADIUS);
        assertTrue(tileCenter(20, 10).getDistance(enemy) <= GroundRetreatRouter.DANGER_RADIUS);

        GroundRetreatRouter.Plan<String> plan = new GroundRetreatRouter(map)
                .plan(Collections.singletonMap("ling", tileCenter(4, 3)), home, Collections.singletonList(enemy));

        assertFalse(plan.getRoute() == RetreatRoute.HOME_CONTESTED);
        assertFalse(plan.getRoute() == RetreatRoute.CORNERED);
    }

    @Test
    void memberInsideTheDangerAtAContestedHomeBacksOutWithoutClosingOnTheEnemy() {
        GameMap map = map(OPEN_FIELD);
        Position home = tileCenter(21, 10);
        Position squad = tileCenter(18, 10);

        GroundRetreatRouter.Plan<String> plan = new GroundRetreatRouter(map)
                .plan(Collections.singletonMap("ling", squad), home, Collections.singletonList(home));

        Position target = plan.getTargets().get("ling");
        assertEquals(RetreatRoute.HOME_CONTESTED, plan.getRoute());
        assertTrue(target.getDistance(home) > GroundRetreatRouter.DANGER_RADIUS, "backs out, got " + target);
    }

    @Test
    void enemyCoveringEveryTileNextToHomeContestsIt() {
        GameMap map = map(DEAD_END_HOME);
        Position home = tileCenter(1, 1);
        Position enemy = tileCenter(7, 1);
        assertTrue(home.getDistance(enemy) > GroundRetreatRouter.DANGER_RADIUS);
        assertTrue(tileCenter(2, 1).getDistance(enemy) <= GroundRetreatRouter.DANGER_RADIUS);

        GroundRetreatRouter.Plan<String> plan = new GroundRetreatRouter(map)
                .plan(Collections.singletonMap("ling", tileCenter(4, 1)), home, Collections.singletonList(enemy));

        assertEquals(RetreatRoute.HOME_CONTESTED, plan.getRoute());
        assertEquals(home, plan.getTargets().get("ling"));
    }

    @Test
    void squadStagedForAContestedHomeTurnsToDefendItWhenTheEnemyCloses() {
        GameMap map = map(OPEN_FIELD);
        Position home = tileCenter(21, 10);
        Position enemy = tileCenter(20, 10);
        GroundRetreatRouter.Plan<String> plan = new GroundRetreatRouter(map)
                .plan(Collections.singletonMap("ling", tileCenter(13, 10)), home, Collections.singletonList(enemy));
        Squad squad = new GroundSquad();
        squad.setStatus(SquadStatus.RETREAT);
        squad.setRetreatRoute(plan.getRoute());
        squad.startRetreatLock(18700);
        int window = squad.getFightHysteresis().getFrames();
        double threshold = 1.44;
        assertEquals(RetreatRoute.HOME_CONTESTED, plan.getRoute());

        for (int frame = 18701; frame <= 18701 + 2 * window; frame++) {
            assertFalse(squad.corneredEngagePersisted(defendRead(squad, 0.8, threshold), frame),
                    "stages while the enemy standing on home reads well below the threshold, frame " + frame);
        }
        int closes = 18702 + 2 * window;
        for (int frame = closes; frame < closes + window; frame++) {
            assertFalse(squad.corneredEngagePersisted(defendRead(squad, 1.337, threshold), frame));
        }
        assertTrue(squad.corneredEngagePersisted(defendRead(squad, 1.337, threshold), closes + window));
        SquadManager.turnCorneredSquadToFight(squad, CombatSimulator.CombatResult.RETREAT, closes + window);

        assertEquals(SquadStatus.FIGHT, squad.getStatus());
        assertEquals(RetreatRoute.NONE, squad.getRetreatRoute());
        assertFalse(squad.isRetreatLocked(closes + window + 1));
        assertTrue(SquadManager.fightHeld(squad, closes + 2 * window - 1,
                SquadManager.fightLockHolds(false, CombatSimulator.CombatResult.RETREAT, true, 1.2, threshold)),
                "defends through the hold whatever the sim reads");
        assertFalse(SquadManager.fightHeld(squad, closes + 2 * window, false));
    }

    private static boolean defendRead(Squad squad, double ratio, double threshold) {
        return SquadManager.contestedHomeDefends(squad.getStatus(), squad.getRetreatRoute(),
                CombatSimulator.CombatResult.RETREAT, true, ratio, threshold);
    }

    @Test
    void corneredSquadAtEngageFights() {
        assertTrue(SquadManager.corneredSquadFights(SquadStatus.RETREAT, RetreatRoute.CORNERED,
                CombatSimulator.CombatResult.ENGAGE));
    }

    @Test
    void corneredSquadDoesNotFightAnUnmeasuredAdvance() {
        assertFalse(SquadManager.corneredSquadFights(SquadStatus.RETREAT, RetreatRoute.CORNERED,
                CombatSimulator.CombatResult.ADVANCE));
    }

    @Test
    void memberWithNoWalkableTileNearItIsLeftUnplanned() {
        GameMap map = map(POCKET_WITH_EAST_EXIT);
        Map<String, Position> members = new LinkedHashMap<>();
        members.put("stranded", tileCenter(20, 1));
        members.put("ling", tileCenter(4, 7));

        GroundRetreatRouter.Plan<String> plan = new GroundRetreatRouter(map)
                .plan(members, tileCenter(27, 7), Collections.singletonList(tileCenter(4, 13)));

        assertTrue(plan.getTargets().containsKey("stranded"));
        assertEquals(null, plan.getTargets().get("stranded"));
        assertEquals(RetreatRoute.HOME, plan.getRoute());
    }

    @Test
    void squadOfOnlyUnplannedMembersIsNotCornered() {
        GameMap map = map(POCKET_WITH_EAST_EXIT);

        GroundRetreatRouter.Plan<String> plan = new GroundRetreatRouter(map)
                .plan(Collections.singletonMap("stranded", tileCenter(20, 1)), tileCenter(27, 7),
                        Collections.singletonList(tileCenter(4, 13)));

        assertFalse(plan.getRoute() == RetreatRoute.CORNERED);
    }

    @Test
    void memberDeepInsideTheThreatsWalksItsEscapePathSixStepsOut() {
        GameMap map = map(OPEN_FIELD);
        Position squad = tileCenter(12, 10);
        List<Position> enemies = Arrays.asList(tileCenter(12, 10), tileCenter(12, 5), tileCenter(12, 15),
                tileCenter(7, 10), tileCenter(17, 10));

        for (int x = 7; x <= 17; x++) {
            for (int y = 5; y <= 15; y++) {
                Position near = tileCenter(x, y);
                assertTrue(enemies.stream().anyMatch(e -> near.getDistance(e) <= GroundRetreatRouter.DANGER_RADIUS),
                        "every tile within five steps is closed, so the exit is at least six away: " + x + ":" + y);
            }
        }

        GroundRetreatRouter.Plan<String> plan = new GroundRetreatRouter(map)
                .plan(Collections.singletonMap("ling", squad), tileCenter(21, 18), enemies);

        TilePosition tile = plan.getTargets().get("ling").toTilePosition();
        assertFalse(plan.getRoute() == RetreatRoute.CORNERED);
        assertEquals(GroundRetreatRouter.PATH_STEP_TILES, Math.max(Math.abs(tile.getX() - 12),
                Math.abs(tile.getY() - 10)));
        for (Position enemy : enemies) {
            assertTrue(GroundRetreatRouter.center(tile).getDistance(enemy) > GroundRetreatRouter.DANGER_RADIUS,
                    "outside every threat, got " + tile);
        }
    }

    @Test
    void retreatPlanIsKeptForTheReplanInterval() {
        assertTrue(SquadManager.retreatPlanFresh(RetreatRoute.HOME, 100, 100));
        assertTrue(SquadManager.retreatPlanFresh(RetreatRoute.CORNERED, 100,
                100 + SquadManager.RETREAT_REPLAN_FRAMES - 1));
        assertFalse(SquadManager.retreatPlanFresh(RetreatRoute.HOME, 100, 100 + SquadManager.RETREAT_REPLAN_FRAMES));
        assertFalse(SquadManager.retreatPlanFresh(RetreatRoute.NONE, 100, 100));
    }

    @Test
    void onlyAttackingWorkersCloseARetreatPath() {
        assertTrue(SquadManager.closesRetreatPath(UnitType.Terran_Marine, false));
        assertTrue(SquadManager.closesRetreatPath(UnitType.Terran_Bunker, false));
        assertFalse(SquadManager.closesRetreatPath(UnitType.Terran_SCV, false));
        assertTrue(SquadManager.closesRetreatPath(UnitType.Terran_SCV, true));
        assertFalse(SquadManager.closesRetreatPath(UnitType.Terran_Supply_Depot, false));
    }

    @Test
    void corneredSquadBelowTheEngageThresholdKeepsRetreating() {
        assertFalse(SquadManager.corneredSquadFights(SquadStatus.RETREAT, RetreatRoute.CORNERED,
                CombatSimulator.CombatResult.RETREAT));
    }

    @Test
    void squadWithAWayHomeKeepsRetreatingAtEngage() {
        List<RetreatRoute> routes = Arrays.asList(RetreatRoute.NONE, RetreatRoute.HOME, RetreatRoute.DETOUR,
                RetreatRoute.AWAY);
        for (RetreatRoute route : routes) {
            assertFalse(SquadManager.corneredSquadFights(SquadStatus.RETREAT, route,
                    CombatSimulator.CombatResult.ENGAGE));
        }
        assertFalse(SquadManager.corneredSquadFights(SquadStatus.FIGHT, RetreatRoute.CORNERED,
                CombatSimulator.CombatResult.ENGAGE));
    }

    private static boolean reachedWithinSnap(int[][] connected, Position member) {
        TilePosition tile = member.toTilePosition();
        int snap = GroundRetreatRouter.SNAP_TILES;
        for (int x = Math.max(0, tile.getX() - snap); x <= Math.min(connected.length - 1, tile.getX() + snap); x++) {
            for (int y = Math.max(0, tile.getY() - snap); y <= Math.min(connected[x].length - 1, tile.getY() + snap);
                 y++) {
                if (connected[x][y] >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int homeDistance(GameMap map, Position home, TilePosition tile) {
        return map.groundStepDistances(home.toTilePosition(), t -> false)[tile.getX()][tile.getY()];
    }

    private static Position firstWalkableFromBottomRight(GameMap map) {
        for (int x = map.getWidth() - 1; x >= 0; x--) {
            for (int y = map.getHeight() - 1; y >= 0; y--) {
                if (map.isWalkableTile(new TilePosition(x, y))) {
                    return tileCenter(x, y);
                }
            }
        }
        throw new IllegalStateException("no walkable tile");
    }

    private static Position tileCenter(int x, int y) {
        return new Position(x * 32 + 16, y * 32 + 16);
    }

    private static String[] openField(int width, int height) {
        String[] rows = new String[height];
        StringBuilder wall = new StringBuilder();
        StringBuilder inner = new StringBuilder("#");
        for (int x = 0; x < width; x++) {
            wall.append('#');
        }
        for (int x = 1; x < width - 1; x++) {
            inner.append('.');
        }
        inner.append('#');
        for (int y = 0; y < height; y++) {
            rows[y] = y == 0 || y == height - 1 ? wall.toString() : inner.toString();
        }
        return rows;
    }

    private static GameMap map(String[] rows) {
        int width = rows[0].length();
        int height = rows.length;
        GameMap map = new GameMap(width, height);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                boolean walkable = rows[y].charAt(x) == '.';
                MapTile tile = new MapTile(new TilePosition(x, y), 0, walkable, walkable, MapTileType.NORMAL);
                tile.setGroundOccupiable(walkable);
                map.addTile(tile, x, y);
            }
        }
        return map;
    }
}
