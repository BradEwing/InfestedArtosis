package info.map;

import bwapi.Game;
import bwapi.Position;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.WalkPosition;
import info.exception.NoWalkablePathException;
import lombok.Getter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bundles up information about the game map.
 */
public class GameMap {

    private int x;
    private int y;
    @Getter
    private ArrayList<MapTile> heatMap = new ArrayList<>();
    @Getter
    private Set<WalkPosition> accessibleWalkPositions = new HashSet<>();
    @Getter
    private Set<Unit> blockingMinerals = new HashSet<>();

    private MapTile[][] mapTiles;

    public GameMap(int x, int y) {
        mapTiles = new MapTile[x][y];
        this.x = x;
        this.y = y;
    }

    public void addTile(MapTile tile, int x, int y) {
        mapTiles[x][y] = tile;
        heatMap.add(tile);
    }

    public MapTile get(int x, int y) {
        return mapTiles[x][y];
    }

    public void ageHeatMap() {
        int weight = 1;
        for (MapTile mapTile : heatMap) {
            if (mapTile.getType() == MapTileType.BASE_START) {
                weight = 3;
            } else if (mapTile.getType() == MapTileType.BASE_EXPANSION) {
                weight = 2;
            } else if (mapTile.getType() == MapTileType.NORMAL) {
                weight = 1;
            }
            mapTile.setScoutImportance(mapTile.getScoutImportance() + weight);
        }
        Collections.sort(heatMap, new MapTileScoutImportanceComparator());
    }

    /**
     * A* search to find a walkable path between start and end tiles.
     *
     * TODO: Consider neutral structures in MapTile
     * TODO: Consider current buildings in MapTile
     *
     * @param start origin TilePosition
     * @param end destination TilePosition
     * @return
     * @throws NoWalkablePathException if no walkable path exists
     */
    public GroundPath aStarSearch(MapTile start, MapTile end) throws NoWalkablePathException {
        Map<MapTile, MapTile> cameFrom = new HashMap<>();
        Map<MapTile, Integer> gScore = new HashMap<>();
        Map<MapTile, Integer> fScore = new HashMap<>();

        gScore.put(start, 0);
        fScore.put(start, this.calculateH(start, end));

        PriorityQueue<MapTile> openSet = new PriorityQueue<>(new MapTileFScoreComparator(fScore));
        openSet.add(start);

        while (!openSet.isEmpty()) {
            MapTile current = openSet.poll();
            if (current == end) {
                return reconstructPath(cameFrom, current);
            }

            List<MapTile> neighbors = this.getNeighbors(current);
            for (MapTile n: neighbors) {
                int neighborG = Integer.MAX_VALUE;
                final int currentG = gScore.get(current);
                final int tentativeGScore = this.calculateG(current, n, currentG);

                if (gScore.containsKey(n)) {
                    neighborG = gScore.get(n);
                }

                if (tentativeGScore < neighborG) {
                    cameFrom.put(n, current);
                    gScore.put(n, tentativeGScore);
                    fScore.put(n, this.calculateH(n, end));
                    if (!openSet.contains(n)) {
                        openSet.add(n);
                    }
                }
            }
        }

        throw new NoWalkablePathException("no walkable path exists");
    }

    public GroundPath aStarSearch(TilePosition start, TilePosition end) throws NoWalkablePathException {
        final MapTile startTile = this.mapTiles[start.getX()][start.getY()];
        final MapTile endTile = this.mapTiles[end.getX()][end.getY()];
        return aStarSearch(startTile, endTile);
    }

    public ScoutPath computeScoutPerimeter(TilePosition center) {
        final int SCAN_RADIUS = 20;
        final int MIN_RADIUS = 7;
        final int WAYPOINT_MIN_SPACING = 100;

        List<Position> unsortedPositions = new ArrayList<>();
        Position centerPos = center.toPosition();

        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
                TilePosition tp = center.add(new TilePosition(dx, dy));

                if (tp.getX() < 0 || tp.getX() >= x || tp.getY() < 0 || tp.getY() >= y) {
                    continue;
                }

                double dist = tp.toPosition().getDistance(centerPos);
                if (dist < MIN_RADIUS * 32 || dist > SCAN_RADIUS * 32) {
                    continue;
                }

                MapTile tile = mapTiles[tp.getX()][tp.getY()];
                if (!tile.isBuildable() || !tile.isWalkable()) {
                    continue;
                }

                boolean isEdge = false;
                TilePosition[] neighbors = {
                    tp.add(new TilePosition(1, 0)),
                    tp.add(new TilePosition(-1, 0)),
                    tp.add(new TilePosition(0, 1)),
                    tp.add(new TilePosition(0, -1))
                };

                for (TilePosition neighbor : neighbors) {
                    if (neighbor.getX() < 0 || neighbor.getX() >= x ||
                        neighbor.getY() < 0 || neighbor.getY() >= y) {
                        isEdge = true;
                        break;
                    }
                    MapTile neighborTile = mapTiles[neighbor.getX()][neighbor.getY()];
                    if (!neighborTile.isBuildable() || !neighborTile.isWalkable()) {
                        isEdge = true;
                        break;
                    }
                }

                if (isEdge) {
                    Position pos = tp.toPosition().add(new Position(16, 16));
                    unsortedPositions.add(pos);
                }
            }
        }

        if (unsortedPositions.isEmpty()) {
            List<TilePosition> fallback = new ArrayList<>();
            fallback.add(center.add(new TilePosition(0, -MIN_RADIUS)));
            fallback.add(center.add(new TilePosition(MIN_RADIUS, 0)));
            fallback.add(center.add(new TilePosition(0, MIN_RADIUS)));
            fallback.add(center.add(new TilePosition(-MIN_RADIUS, 0)));
            return new ScoutPath(fallback);
        }

        List<Position> orderedPositions = new ArrayList<>();
        Position current = unsortedPositions.get(0);
        unsortedPositions.remove(0);
        orderedPositions.add(current);

        while (!unsortedPositions.isEmpty()) {
            Position closest = null;
            double closestDist = Double.MAX_VALUE;

            for (Position pos : unsortedPositions) {
                double dist = current.getDistance(pos);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = pos;
                }
            }

            orderedPositions.add(closest);
            unsortedPositions.remove(closest);
            current = closest;
        }

        List<Position> simplifiedPositions = new ArrayList<>();
        simplifiedPositions.add(orderedPositions.get(0));

        for (int i = 1; i < orderedPositions.size(); i++) {
            Position last = simplifiedPositions.get(simplifiedPositions.size() - 1);
            Position currentPos = orderedPositions.get(i);

            if (last.getDistance(currentPos) >= WAYPOINT_MIN_SPACING) {
                simplifiedPositions.add(currentPos);
            }
        }

        List<TilePosition> waypoints = simplifiedPositions.stream()
            .map(pos -> new TilePosition(pos))
            .collect(Collectors.toList());

        return new ScoutPath(waypoints);
    }

    private int calculateG(MapTile current, MapTile target, int currentG) {
        return currentG + (int) target.getTile().getDistance(current.getTile());
    }

    private int calculateH(MapTile current, MapTile destination) {
        return (int) destination.getTile().getDistance(current.getTile());
    }

    private GroundPath reconstructPath(Map<MapTile, MapTile> cameFrom, MapTile current) {
        ArrayDeque<MapTile> path = new ArrayDeque<>();
        path.add(current);
        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            path.addFirst(current);
        }

        return new GroundPath(path);
    }

    /**
     * Returns neighbor tiles of current that can be considered for ground based path-finding.
     *
     *
     * @param current MapTile to find neighbors for
     * @return valid MapTile neighbors
     */
    private List<MapTile> getNeighbors(MapTile current) {
        List<MapTile> neighbors = new ArrayList<>();

        final int currentX = current.getX();
        final int currentY = current.getY();

        // Flags to keep track of walkable cardinal neighbors.
        boolean northWalkable = false;
        boolean southWalkable = false;
        boolean eastWalkable  = false;
        boolean westWalkable  = false;

        // Cardinal neighbors
        // North
        if (isValidTile(currentX, currentY + 1)) {
            MapTile north = mapTiles[currentX][currentY + 1];
            if (north.isWalkable()) {
                neighbors.add(north);
                northWalkable = true;
            }
        }
        // South
        if (isValidTile(currentX, currentY - 1)) {
            MapTile south = mapTiles[currentX][currentY - 1];
            if (south.isWalkable()) {
                neighbors.add(south);
                southWalkable = true;
            }
        }
        // West
        if (isValidTile(currentX - 1, currentY)) {
            MapTile west = mapTiles[currentX - 1][currentY];
            if (west.isWalkable()) {
                neighbors.add(west);
                westWalkable = true;
            }
        }
        // East
        if (isValidTile(currentX + 1, currentY)) {
            MapTile east = mapTiles[currentX + 1][currentY];
            if (east.isWalkable()) {
                neighbors.add(east);
                eastWalkable = true;
            }
        }

        // Diagonal neighbors:
        // Northeast
        if (isValidTile(currentX + 1, currentY + 1)) {
            MapTile ne = mapTiles[currentX + 1][currentY + 1];
            if (ne.isWalkable() && (northWalkable || eastWalkable)) {
                neighbors.add(ne);
            }
        }
        // Northwest
        if (isValidTile(currentX - 1, currentY + 1)) {
            MapTile nw = mapTiles[currentX - 1][currentY + 1];
            if (nw.isWalkable() && (northWalkable || westWalkable)) {
                neighbors.add(nw);
            }
        }
        // Southeast
        if (isValidTile(currentX + 1, currentY - 1)) {
            MapTile se = mapTiles[currentX + 1][currentY - 1];
            if (se.isWalkable() && (southWalkable || eastWalkable)) {
                neighbors.add(se);
            }
        }
        // Southwest
        if (isValidTile(currentX - 1, currentY - 1)) {
            MapTile sw = mapTiles[currentX - 1][currentY - 1];
            if (sw.isWalkable() && (southWalkable || westWalkable)) {
                neighbors.add(sw);
            }
        }

        return neighbors;
    }

    public boolean isValidTile(TilePosition tp) {
        return isValidTile(tp.getX(), tp.getY());
    }

    private boolean isValidTile(int x, int y) {
        return x >= 0 && x < this.x && y >= 0 && y < this.y;
    }

    /**
     * Calculates all accessible WalkPositions from the main base using flood fill algorithm.
     * 
     * @param game The BWAPI Game instance
     * @param mainBasePosition The main base TilePosition
     */
    public void calculateAccessibleWalkPositions(Game game, TilePosition mainBasePosition) {
        WalkPositionFloodFill floodFill = new WalkPositionFloodFill(game);
        this.accessibleWalkPositions = floodFill.calculateAccessibleWalkPositions(mainBasePosition);
    }

    public void addBlockingMineral(Unit mineral) {
        blockingMinerals.add(mineral);
    }

    public void removeBlockingMineral(Unit mineral) {
        blockingMinerals.remove(mineral);
    }

    /**
     * Finds the closest blocking mineral within the given pixel radius of a position.
     *
     * @param position the center position to search from
     * @param radius the search radius in pixels
     * @return the closest blocking mineral Unit, or null if none found
     */
    public Unit findNearbyBlockingMineral(Position position, int radius) {
        Unit closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Unit mineral : blockingMinerals) {
            if (!mineral.exists()) continue;
            double distance = position.getDistance(mineral.getPosition());
            if (distance <= radius && distance < closestDistance) {
                closestDistance = distance;
                closest = mineral;
            }
        }
        return closest;
    }
}
