package unit.squad;

import bwapi.Position;
import bwapi.TilePosition;
import info.map.GameMap;
import info.map.MapTile;
import info.map.MapTileType;
import org.junit.jupiter.api.Test;
import telemetry.RetreatRoute;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        List<String[]> layouts = Arrays.asList(POCKET_WITH_EAST_EXIT, OPEN_FIELD, SEALED_POCKET, TWO_GAP_WALL);
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
                    assertEquals(members.size(), plan.getTargets().size());
                    for (Map.Entry<String, Position> target : plan.getTargets().entrySet()) {
                        TilePosition tile = target.getValue().toTilePosition();
                        String where = "member " + target.getKey() + " enemy " + ex + ":" + ey + " target " + tile;
                        assertTrue(map.isWalkableTile(tile), "walkable, " + where);
                        assertTrue(connected[tile.getX()][tile.getY()] >= 0, "connected to home, " + where);
                        assertEquals(GroundRetreatRouter.center(tile), target.getValue());
                    }
                }
            }
        }
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
    void corneredSquadAtEngageFights() {
        assertTrue(SquadManager.corneredSquadFights(SquadStatus.RETREAT, RetreatRoute.CORNERED,
                CombatSimulator.CombatResult.ENGAGE));
        assertTrue(SquadManager.corneredSquadFights(SquadStatus.RETREAT, RetreatRoute.CORNERED,
                CombatSimulator.CombatResult.ADVANCE));
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
