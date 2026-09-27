package unit.squad;

import bwapi.Position;
import bwapi.TilePosition;
import info.map.GameMap;
import lombok.Getter;
import telemetry.RetreatRoute;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Plans where each member of a retreating ground squad walks.
 *
 * <p>Each member follows the ground path from its own tile toward home, the squad rally point, and is sent
 * {@link #PATH_STEP_TILES} steps along it. The path is searched with every tile within {@link #DANGER_RADIUS} of a
 * seen enemy threat closed, so a path home that runs through the enemy is replaced by the shortest one around it.
 * A member already standing inside that radius first backs out of it without stepping closer to the enemy, within
 * {@link #LOCAL_ESCAPE_TILES} steps, to a tile that has such a path onward.
 *
 * <p>A squad none of whose members has a path home clear of the enemy is cornered: each member backs off to the tile
 * it can reach without stepping closer to the enemy that lies farthest from it. Every target is the centre of a fully
 * walkable tile that a ground path joins to home.
 */
final class GroundRetreatRouter {

    /**
     * Tuning value: pixels around a seen enemy threat that a retreat path home may not cross. A judgment call, kept
     * to five tiles so a narrow exit beside the enemy stays open.
     */
    static final int DANGER_RADIUS = 160;
    /**
     * Tuning value: tiles along the path a member is sent per plan, the 192 pixels the away vector used to reach.
     */
    static final int PATH_STEP_TILES = 6;
    /**
     * Tuning value: tile steps a member inside the danger radius may take backing out of it before its escape is
     * given up.
     */
    static final int LOCAL_ESCAPE_TILES = 8;
    /**
     * Tuning value: tile radius searched for a walkable tile when a member or home stands on one that is not.
     */
    static final int SNAP_TILES = 3;

    private final GameMap gameMap;
    private TilePosition cachedHome;
    private int[][] cachedHomeDistances;

    GroundRetreatRouter(GameMap gameMap) {
        this.gameMap = gameMap;
    }

    /**
     * The route and per member target of one retreat plan.
     *
     * @param <K> member key
     */
    @Getter
    static final class Plan<K> {
        private final RetreatRoute route;
        private final Map<K, Position> targets;

        Plan(RetreatRoute route, Map<K, Position> targets) {
            this.route = route;
            this.targets = targets;
        }
    }

    /**
     * Plans the retreat of the given members.
     *
     * @param members position of each member
     * @param home where the squad falls back to
     * @param threats positions of the enemy threats the retreat avoids
     * @param <K> member key
     * @return the plan, or null when no walkable tile lies within {@link #SNAP_TILES} of home
     */
    <K> Plan<K> plan(Map<K, Position> members, Position home, Collection<Position> threats) {
        TilePosition homeTile = snapToWalkable(home.toTilePosition());
        if (homeTile == null) {
            return null;
        }
        int[][] direct = homeDistances(homeTile);
        List<Position> enemies = new ArrayList<>(threats);
        boolean[][] danger = dangerTiles(enemies);
        int[][] safe = enemies.isEmpty()
                ? direct
                : gameMap.groundStepDistances(homeTile, tile -> danger[tile.getX()][tile.getY()]);

        Map<K, Position> targets = new LinkedHashMap<>();
        int escaped = 0;
        boolean detour = false;
        for (Map.Entry<K, Position> member : members.entrySet()) {
            TilePosition start = snapToReached(member.getValue().toTilePosition(), direct);
            if (start == null) {
                targets.put(member.getKey(), center(homeTile));
                continue;
            }
            if (at(safe, start) >= 0) {
                targets.put(member.getKey(), center(descend(safe, start, PATH_STEP_TILES, enemies)));
                escaped++;
                detour |= at(safe, start) > at(direct, start);
                continue;
            }
            Escape escape = escape(start, safe, enemies);
            if (escape.exit == null) {
                targets.put(member.getKey(), center(escape.farthest));
                continue;
            }
            targets.put(member.getKey(), center(escape.target(safe, enemies)));
            escaped++;
            detour |= escape.exitSteps + at(safe, escape.exit) > at(direct, start);
        }

        RetreatRoute route;
        if (!members.isEmpty() && escaped == 0) {
            route = RetreatRoute.CORNERED;
        } else if (detour) {
            route = RetreatRoute.DETOUR;
        } else {
            route = RetreatRoute.HOME;
        }
        return new Plan<>(route, targets);
    }

    private int[][] homeDistances(TilePosition homeTile) {
        if (!homeTile.equals(cachedHome)) {
            cachedHome = homeTile;
            cachedHomeDistances = gameMap.groundStepDistances(homeTile, tile -> false);
        }
        return cachedHomeDistances;
    }

    private boolean[][] dangerTiles(List<Position> enemies) {
        boolean[][] danger = new boolean[gameMap.getWidth()][gameMap.getHeight()];
        int reachTiles = DANGER_RADIUS / 32 + 1;
        for (Position enemy : enemies) {
            TilePosition tile = enemy.toTilePosition();
            for (int x = tile.getX() - reachTiles; x <= tile.getX() + reachTiles; x++) {
                for (int y = tile.getY() - reachTiles; y <= tile.getY() + reachTiles; y++) {
                    if (x < 0 || y < 0 || x >= gameMap.getWidth() || y >= gameMap.getHeight()) {
                        continue;
                    }
                    if (center(new TilePosition(x, y)).getDistance(enemy) <= DANGER_RADIUS) {
                        danger[x][y] = true;
                    }
                }
            }
        }
        return danger;
    }

    private TilePosition descend(int[][] field, TilePosition start, int steps, List<Position> enemies) {
        TilePosition current = start;
        for (int i = 0; i < steps && at(field, current) > 0; i++) {
            TilePosition next = null;
            for (TilePosition neighbor : gameMap.groundNeighbors(current)) {
                if (at(field, neighbor) != at(field, current) - 1) {
                    continue;
                }
                if (next == null || enemyDistance(neighbor, enemies) > enemyDistance(next, enemies)) {
                    next = neighbor;
                }
            }
            if (next == null) {
                break;
            }
            current = next;
        }
        return current;
    }

    private Escape escape(TilePosition start, int[][] safe, List<Position> enemies) {
        double floor = enemyDistance(start, enemies);
        Map<TilePosition, TilePosition> parents = new HashMap<>();
        Map<TilePosition, Integer> steps = new HashMap<>();
        ArrayDeque<TilePosition> queue = new ArrayDeque<>();
        steps.put(start, 0);
        queue.add(start);
        Escape escape = new Escape(start, parents);
        double farthest = floor;
        int best = Integer.MAX_VALUE;
        while (!queue.isEmpty()) {
            TilePosition current = queue.poll();
            int depth = steps.get(current);
            double distance = enemyDistance(current, enemies);
            if (distance > farthest) {
                farthest = distance;
                escape.farthest = current;
            }
            if (at(safe, current) >= 0 && depth + at(safe, current) < best) {
                best = depth + at(safe, current);
                escape.exit = current;
                escape.exitSteps = depth;
            }
            if (depth >= LOCAL_ESCAPE_TILES) {
                continue;
            }
            for (TilePosition neighbor : gameMap.groundNeighbors(current)) {
                if (steps.containsKey(neighbor) || enemyDistance(neighbor, enemies) < floor) {
                    continue;
                }
                steps.put(neighbor, depth + 1);
                parents.put(neighbor, current);
                queue.add(neighbor);
            }
        }
        return escape;
    }

    private final class Escape {
        private final TilePosition start;
        private final Map<TilePosition, TilePosition> parents;
        private TilePosition farthest;
        private TilePosition exit;
        private int exitSteps;

        private Escape(TilePosition start, Map<TilePosition, TilePosition> parents) {
            this.start = start;
            this.parents = parents;
            this.farthest = start;
        }

        private TilePosition target(int[][] safe, List<Position> enemies) {
            if (exitSteps < PATH_STEP_TILES) {
                return descend(safe, exit, PATH_STEP_TILES - exitSteps, enemies);
            }
            ArrayDeque<TilePosition> path = new ArrayDeque<>();
            for (TilePosition tile = exit; tile != null && !tile.equals(start); tile = parents.get(tile)) {
                path.addFirst(tile);
            }
            int index = 0;
            for (TilePosition tile : path) {
                if (++index == PATH_STEP_TILES) {
                    return tile;
                }
            }
            return exit;
        }
    }

    private TilePosition snapToWalkable(TilePosition tile) {
        return nearest(tile, candidate -> gameMap.isWalkableTile(candidate));
    }

    private TilePosition snapToReached(TilePosition tile, int[][] field) {
        return nearest(tile, candidate -> at(field, candidate) >= 0);
    }

    private TilePosition nearest(TilePosition tile, Predicate<TilePosition> accepted) {
        TilePosition best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = tile.getX() - SNAP_TILES; x <= tile.getX() + SNAP_TILES; x++) {
            for (int y = tile.getY() - SNAP_TILES; y <= tile.getY() + SNAP_TILES; y++) {
                TilePosition candidate = new TilePosition(x, y);
                if (!gameMap.isValidTile(candidate) || !accepted.test(candidate)) {
                    continue;
                }
                double distance = candidate.getDistance(tile);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static int at(int[][] field, TilePosition tile) {
        return field[tile.getX()][tile.getY()];
    }

    private static double enemyDistance(TilePosition tile, List<Position> enemies) {
        double nearest = Double.MAX_VALUE;
        Position position = center(tile);
        for (Position enemy : enemies) {
            nearest = Math.min(nearest, position.getDistance(enemy));
        }
        return nearest;
    }

    static Position center(TilePosition tile) {
        return new Position(tile.getX() * 32 + 16, tile.getY() * 32 + 16);
    }
}
